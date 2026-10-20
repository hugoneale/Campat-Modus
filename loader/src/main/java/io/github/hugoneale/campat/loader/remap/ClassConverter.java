package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts a single class file of a NeoForge mod so that it links against Forge plus the
 * compatibility runtime.
 */
public final class ClassConverter {
    public static final String NEO_MOD_ANNOTATION = "Lnet/neoforged/fml/common/Mod;";
    private static final String NEO_CANCELLABLE = "net/neoforged/bus/api/ICancellableEvent";
    private static final String NEO_EVENT = "net/neoforged/bus/api/Event";
    private static final String FORGE_CANCELABLE = "Lnet/minecraftforge/eventbus/api/Cancelable;";

    /** A NeoForge {@code @Mod} entrypoint found while converting. */
    public record Entrypoint(String modId, String className, List<String> dists) {}

    public record Output(byte[] bytes, List<Entrypoint> entrypoints, Set<String> referencedNeoClasses, List<String> problems) {}

    private final Mappings mappings;
    private final ClassHierarchy hierarchy;
    private final NeoRemapper remapper;
    private final RedirectPass redirects;
    private final EvolutionPass evolutions;
    private final MixinRetypes mixinRetypes;

    public ClassConverter(Mappings mappings, ClassHierarchy hierarchy) {
        this.mappings = mappings;
        this.hierarchy = hierarchy;
        this.remapper = new NeoRemapper(mappings, hierarchy);
        this.redirects = new RedirectPass(mappings, hierarchy);
        this.evolutions = new EvolutionPass(mappings, hierarchy);
        this.mixinRetypes = new MixinRetypes(mappings);
    }

    public MixinRetypes mixinRetypes() {
        return mixinRetypes;
    }

    public NeoRemapper remapper() {
        return remapper;
    }

    /** Header-only read, used to register every class in the hierarchy before converting. */
    public static void index(byte[] bytes, ClassHierarchy hierarchy) {
        ClassReader cr = new ClassReader(bytes);
        hierarchy.add(cr.getClassName(), cr.getSuperName(), List.of(cr.getInterfaces()));
    }

    /**
     * Synthetic lambda methods returning a type listed in a Z mapping are re-declared to return
     * Object, where every indy site using them has an erased functional interface return of Object
     * (Supplier and friends): only those sites call javac's lambda methods.
     */
    private void widenLambdaReturns(ClassNode cn) {
        java.util.Map<String, String> widened = new java.util.HashMap<>();
        for (MethodNode mn : cn.methods) {
            if ((mn.access & Opcodes.ACC_SYNTHETIC) == 0 || !mn.name.startsWith("lambda$")) continue;
            Type ret = Type.getReturnType(mn.desc);
            if (ret.getSort() != Type.OBJECT || mappings.superReplacement(ret.getInternalName()) == null) continue;
            widened.put(mn.name + mn.desc, Type.getMethodDescriptor(Type.getType(Object.class), Type.getArgumentTypes(mn.desc)));
        }
        if (widened.isEmpty()) return;
        // only when every use is an indy whose functional interface already returns Object
        java.util.List<org.objectweb.asm.tree.InvokeDynamicInsnNode> sites = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof MethodInsnNode mi && mi.owner.equals(cn.name)) widened.remove(mi.name + mi.desc);
                if (!(insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy)) continue;
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (!(indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h) || !h.getOwner().equals(cn.name) || !widened.containsKey(h.getName() + h.getDesc())) continue;
                    boolean lmf = indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && i == 1 && indy.bsmArgs[0] instanceof Type sam
                        && Type.getReturnType(sam.getDescriptor()).getDescriptor().equals("Ljava/lang/Object;");
                    if (lmf) sites.add(indy);
                    else widened.remove(h.getName() + h.getDesc());
                }
            }
        }
        if (widened.isEmpty()) return;
        for (MethodNode mn : cn.methods) {
            String nd = widened.get(mn.name + mn.desc);
            if (nd != null) mn.desc = nd;
        }
        for (var indy : sites) {
            org.objectweb.asm.Handle h = (org.objectweb.asm.Handle) indy.bsmArgs[1];
            String nd = widened.get(h.getName() + h.getDesc());
            if (nd == null) continue;
            indy.bsmArgs[1] = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), h.getName(), nd, h.isInterface());
            Type inst = (Type) indy.bsmArgs[2];
            indy.bsmArgs[2] = Type.getMethodType(Type.getType(Object.class), inst.getArgumentTypes());
        }
    }

    public Output convert(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        List<Entrypoint> entrypoints = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> neoRefs = new java.util.TreeSet<>();

        // 1. entrypoints
        for (AnnotationNode an : visible(cn)) {
            if (NEO_MOD_ANNOTATION.equals(an.desc)) entrypoints.add(readEntrypoint(cn.name, an));
        }

        // 1-. values of classes that lost a supertype, flowing into slots of that supertype
        for (String[] w : mappings.lostSubtypes()) new SubtypeLossPass(w[0], w[1], w[2], w[3], hierarchy).apply(cn, bytes);

        // 1a. classes extending a platform class that cannot be extended any more get a stand-in parent
        String newSuper = cn.superName == null ? null : mappings.superReplacement(cn.superName);
        if (newSuper != null) {
            String oldSuper = cn.superName;
            cn.superName = newSuper;
            hierarchy.add(cn.name, newSuper, cn.interfaces);
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESPECIAL && mi.owner.equals(oldSuper)) mi.owner = newSuper;
                }
            }
        }

        // 1a'. lambdas declared to return such a class (registration suppliers creating a mod
        // subclass of it) return Object, so the subclass on its new parent still verifies
        widenLambdaReturns(cn);

        // 1b. evolved virtual method signatures (legacy layer): bridges and adapted calls. The
        // generated methods are straight line code with explicit max stack/locals, no frames needed.
        evolutions.apply(cn);
        addDefaultImpls(cn);
        fixKindChanges(cn);
        mixinRetypes.apply(cn);

        // 2. call site redirects, still in NeoForge names
        boolean ctorRedirect = false;
        int newsBefore = countNew(cn);
        problems.addAll(redirects.apply(cn));
        if (countNew(cn) != newsBefore) ctorRedirect = true;

        // 3. NeoForge cancellable events are marked by an interface, Forge's by an annotation
        if (hierarchy.isSubtypeOf(cn.name, NEO_CANCELLABLE) && (cn.access & Opcodes.ACC_INTERFACE) == 0) {
            boolean has = visible(cn).stream().anyMatch(a -> FORGE_CANCELABLE.equals(a.desc));
            if (!has) {
                if (cn.visibleAnnotations == null) cn.visibleAnnotations = new ArrayList<>();
                cn.visibleAnnotations.add(new AnnotationNode(FORGE_CANCELABLE));
            }
        }

        // 3b. Forge's event bus instantiates events through a public no-arg constructor to build
        // listener lists; NeoForge's bus does not, so mod events often have a non-public one.
        if (hierarchy.isSubtypeOf(cn.name, NEO_EVENT)) {
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals("<init>") && mn.desc.equals("()V") && (mn.access & Opcodes.ACC_PUBLIC) == 0) {
                    mn.access = (mn.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }
            }
        }

        // 3c. NeoForge (20.5+) no longer strips or enforces @OnlyIn, and mods rely on loading such
        // classes on the server; Forge's RuntimeDistCleaner would refuse them. Also NeoForge's bus
        // accepts private @SubscribeEvent methods, Forge's rejects them.
        stripOnlyIn(cn);
        for (MethodNode mn : cn.methods) {
            if ((mn.access & Opcodes.ACC_PRIVATE) != 0 && has(mn.visibleAnnotations, NEO_SUBSCRIBE)) {
                mn.access = (mn.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            }
        }

        // 4. strings: reflection by name and mixin targets
        for (MethodNode mn : cn.methods) {
            if (mn.instructions != null) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) ldc.cst = remapper.mapStringReferences(s);
                }
            }
            remapAnnotationStrings(mn.visibleAnnotations);
            remapAnnotationStrings(mn.invisibleAnnotations);
        }
        for (FieldNode fn : cn.fields) {
            if (fn.value instanceof String s) fn.value = remapper.mapStringReferences(s);
            remapAnnotationStrings(fn.visibleAnnotations);
            remapAnnotationStrings(fn.invisibleAnnotations);
        }
        remapAnnotationStrings(cn.visibleAnnotations);
        remapAnnotationStrings(cn.invisibleAnnotations);

        // 5. record which NeoForge classes stay referenced (for diagnostics)
        collectNeoRefs(cn, neoRefs);

        // 6. apply renames
        ClassWriter cw = ctorRedirect ? new HierarchyClassWriter(hierarchy, mappings) : new ClassWriter(0);
        cn.accept(new ClassRemapper(cw, remapper));
        return new Output(cw.toByteArray(), entrypoints, neoRefs, problems);
    }

    /**
     * Implements abstract methods a platform type gained ({@code D} lines) in the topmost mod class
     * below it, by calling a static helper with {@code this} and the arguments.
     */
    private void addDefaultImpls(ClassNode cn) {
        if (mappings.defaultImpls().isEmpty() || (cn.access & Opcodes.ACC_INTERFACE) != 0) return;
        for (Mappings.DefaultImpl d : mappings.defaultImpls()) {
            if (!hierarchy.isSubtypeOf(cn.name, d.owner()) || cn.name.equals(d.owner())) continue;
            if (cn.methods.stream().anyMatch(m -> m.name.equals(d.name()) && m.desc.equals(d.desc()))) continue;
            // a mod superclass below the owner gets (or has) the implementation itself
            String sup = cn.superName;
            if (sup != null && !sup.equals(d.owner()) && !hierarchy.isPlatform(sup) && hierarchy.knows(sup) && hierarchy.isSubtypeOf(sup, d.owner())) continue;
            // first platform ancestor: must be the owner itself or unrelated to it (then a mod class brings the interface)
            String p = sup;
            while (p != null && !hierarchy.isPlatform(p)) p = hierarchy.superClass(p);
            if (p != null && !p.equals(d.owner()) && hierarchy.isSubtypeOf(p, d.owner())) continue;
            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(d.desc());
            org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(d.desc());
            MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, d.name(), d.desc(), null, null);
            m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            int slot = 1;
            org.objectweb.asm.Type[] helperArgs = new org.objectweb.asm.Type[args.length + 1];
            helperArgs[0] = org.objectweb.asm.Type.getObjectType(d.owner());
            for (int i = 0; i < args.length; i++) {
                m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(args[i].getOpcode(Opcodes.ILOAD), slot));
                slot += args[i].getSize();
                helperArgs[i + 1] = args[i];
            }
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, d.helperOwner(), d.helperName(),
                org.objectweb.asm.Type.getMethodDescriptor(ret, helperArgs), false));
            m.instructions.add(new org.objectweb.asm.tree.InsnNode(ret.getOpcode(Opcodes.IRETURN)));
            m.maxLocals = slot;
            m.maxStack = Math.max(slot, ret.getSize());
            cn.methods.add(m);
        }
    }

    /** call opcodes for owners that changed between class and interface */
    private void fixKindChanges(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (!(insn instanceof MethodInsnNode mi)) continue;
                Boolean nowItf = mappings.kindChange(mi.owner);
                if (nowItf == null) continue;
                if (nowItf) {
                    if (mi.getOpcode() == Opcodes.INVOKEVIRTUAL) mi.setOpcode(Opcodes.INVOKEINTERFACE);
                    mi.itf = true;
                } else {
                    if (mi.getOpcode() == Opcodes.INVOKEINTERFACE) mi.setOpcode(Opcodes.INVOKEVIRTUAL);
                    mi.itf = false;
                }
            }
            if (mn.instructions != null) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
                        for (int i = 0; i < indy.bsmArgs.length; i++) {
                            if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h) {
                                Boolean nowItf = mappings.kindChange(h.getOwner());
                                if (nowItf == null || h.isInterface() == nowItf) continue;
                                int tag = h.getTag();
                                if (nowItf && tag == org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL) tag = org.objectweb.asm.Opcodes.H_INVOKEINTERFACE;
                                if (!nowItf && tag == org.objectweb.asm.Opcodes.H_INVOKEINTERFACE) tag = org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
                                indy.bsmArgs[i] = new org.objectweb.asm.Handle(tag, h.getOwner(), h.getName(), h.getDesc(), nowItf);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final String NEO_SUBSCRIBE = "Lnet/neoforged/bus/api/SubscribeEvent;";
    private static final java.util.Set<String> NEO_ONLY_IN = java.util.Set.of("Lnet/neoforged/api/distmarker/OnlyIn;", "Lnet/neoforged/api/distmarker/OnlyIns;");

    private static boolean has(List<AnnotationNode> anns, String desc) {
        if (anns == null) return false;
        for (AnnotationNode a : anns) if (a.desc.equals(desc)) return true;
        return false;
    }

    private static final java.util.Set<String> ONLY_IN = java.util.Set.of("Lnet/neoforged/api/distmarker/OnlyIn;", "Lnet/minecraftforge/api/distmarker/OnlyIn;");

    /** set when converting for a dedicated server (part of the cache key) */
    private static volatile boolean dedicatedServer;

    public static void setDedicatedServer(boolean server) {
        dedicatedServer = server;
    }

    private static boolean clientOnly(List<AnnotationNode> anns) {
        if (anns == null) return false;
        for (AnnotationNode a : anns) {
            if (!ONLY_IN.contains(a.desc) || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if ("value".equals(a.values.get(i)) && a.values.get(i + 1) instanceof String[] e && e.length == 2 && "CLIENT".equals(e[1])) return true;
            }
        }
        return false;
    }

    /**
     * Client-only methods on a dedicated server. NeoForge leaves them in place, but its server runs
     * the full joined jar; Forge's server jar holds only part of the client classes, so verifying
     * such a method (a Screen argument, ...) can hit a client class whose parents are missing.
     * Their bodies become a throw, which is what calling one on a server would end in anyway.
     */
    private static void neuterClientOnlyMethods(ClassNode cn) {
        if (!dedicatedServer) return;
        java.util.Set<String> targets = new java.util.HashSet<>();
        for (MethodNode m : cn.methods) {
            if (clientOnly(m.visibleAnnotations) || clientOnly(m.invisibleAnnotations)) targets.add(m.name + m.desc);
        }
        if (targets.isEmpty()) return;
        // lambdas written inside client-only methods are client-only too
        java.util.Deque<String> work = new java.util.ArrayDeque<>(targets);
        while (!work.isEmpty()) {
            String t = work.pop();
            for (MethodNode m : cn.methods) {
                if (!(m.name + m.desc).equals(t) || m.instructions == null) continue;
                for (AbstractInsnNode insn : m.instructions) {
                    if (!(insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy)) continue;
                    for (Object a : indy.bsmArgs) {
                        if (a instanceof org.objectweb.asm.Handle h && h.getOwner().equals(cn.name) && h.getName().startsWith("lambda$") && targets.add(h.getName() + h.getDesc())) work.push(h.getName() + h.getDesc());
                    }
                }
            }
        }
        for (MethodNode m : cn.methods) {
            if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 || m.instructions == null || m.instructions.size() == 0) continue;
            if (!targets.contains(m.name + m.desc)) continue;
            m.instructions.clear();
            m.tryCatchBlocks.clear();
            m.localVariables = null;
            m.visibleLocalVariableAnnotations = null;
            m.invisibleLocalVariableAnnotations = null;
            m.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
            m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP));
            m.instructions.add(new org.objectweb.asm.tree.LdcInsnNode("client-only method " + cn.name + "." + m.name + " called on a dedicated server"));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false));
            m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ATHROW));
            m.maxStack = 3;
            m.maxLocals = (org.objectweb.asm.Type.getArgumentsAndReturnSizes(m.desc) >> 2) - ((m.access & Opcodes.ACC_STATIC) != 0 ? 1 : 0);
        }
    }

    private static void stripOnlyIn(ClassNode cn) {
        neuterClientOnlyMethods(cn);
        java.util.function.Consumer<List<AnnotationNode>> strip = l -> { if (l != null) l.removeIf(a -> NEO_ONLY_IN.contains(a.desc)); };
        strip.accept(cn.visibleAnnotations);
        strip.accept(cn.invisibleAnnotations);
        for (MethodNode m : cn.methods) { strip.accept(m.visibleAnnotations); strip.accept(m.invisibleAnnotations); }
        for (FieldNode f : cn.fields) { strip.accept(f.visibleAnnotations); strip.accept(f.invisibleAnnotations); }
    }

    private static int countNew(ClassNode cn) {
        int n = 0;
        for (MethodNode mn : cn.methods)
            if (mn.instructions != null)
                for (AbstractInsnNode i : mn.instructions) if (i.getOpcode() == Opcodes.NEW) n++;
        return n;
    }

    private void collectNeoRefs(ClassNode cn, Set<String> out) {
        java.util.function.Consumer<String> add = t -> {
            if (t != null && t.startsWith("net/neoforged/")) out.add(t);
        };
        java.util.function.Consumer<String> addDesc = d -> {
            if (d == null || !d.contains("net/neoforged/")) return;
            int i = 0;
            while ((i = d.indexOf("Lnet/neoforged/", i)) >= 0) {
                int s = d.indexOf(';', i);
                if (s < 0) break;
                out.add(d.substring(i + 1, s));
                i = s;
            }
        };
        add.accept(cn.superName);
        cn.interfaces.forEach(add);
        for (FieldNode f : cn.fields) addDesc.accept(f.desc);
        for (MethodNode mn : cn.methods) {
            addDesc.accept(mn.desc);
            if (mn.instructions == null) continue;
            for (AbstractInsnNode i : mn.instructions) {
                if (i instanceof MethodInsnNode mi) { add.accept(mi.owner); addDesc.accept(mi.desc); }
                else if (i instanceof org.objectweb.asm.tree.FieldInsnNode fi) { add.accept(fi.owner); addDesc.accept(fi.desc); }
                else if (i instanceof TypeInsnNode ti) addDesc.accept(ti.desc.startsWith("[") ? ti.desc : "L" + ti.desc + ";");
                else if (i instanceof LdcInsnNode l && l.cst instanceof Type t) addDesc.accept(t.getDescriptor());
            }
        }
        // only classes that are neither renamed nor otherwise handled matter; renamed ones are filtered by the caller
    }

    private void remapAnnotationStrings(List<AnnotationNode> anns) {
        if (anns == null) return;
        for (AnnotationNode an : anns) remapAnnotation(an);
    }

    private void remapAnnotation(AnnotationNode an) {
        if (an.values == null) return;
        // Only touch strings of annotations that carry class/member references by string: mixin & co.
        boolean stringRefs = an.desc.startsWith("Lorg/spongepowered/") || an.desc.startsWith("Lcom/llamalad7/");
        for (int i = 1; i < an.values.size(); i += 2) {
            Object v = an.values.get(i);
            an.values.set(i, remapValue(v, stringRefs));
        }
    }

    @SuppressWarnings("unchecked")
    private Object remapValue(Object v, boolean strings) {
        if (v instanceof String s && strings) return remapper.mapStringReferences(s);
        if (v instanceof AnnotationNode a) { remapAnnotation(a); return a; }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(remapValue(o, strings));
            return out;
        }
        return v;
    }

    private static List<AnnotationNode> visible(ClassNode cn) {
        return cn.visibleAnnotations == null ? List.of() : cn.visibleAnnotations;
    }

    private static Entrypoint readEntrypoint(String owner, AnnotationNode an) {
        String modId = null;
        List<String> dists = new ArrayList<>();
        if (an.values != null) {
            for (int i = 0; i < an.values.size(); i += 2) {
                String k = (String) an.values.get(i);
                Object v = an.values.get(i + 1);
                if (k.equals("value")) modId = (String) v;
                else if (k.equals("dist") && v instanceof List<?> l) {
                    for (Object o : l) if (o instanceof String[] e) dists.add(e[1]);
                }
            }
        }
        return new Entrypoint(modId, owner.replace('/', '.'), dists);
    }

    /** ClassWriter that computes frames using the converter's hierarchy instead of class loading. */
    static final class HierarchyClassWriter extends ClassWriter {
        private final ClassHierarchy hierarchy;
        private final Mappings mappings;

        HierarchyClassWriter(ClassHierarchy hierarchy, Mappings mappings) {
            super(COMPUTE_FRAMES);
            this.hierarchy = hierarchy;
            this.mappings = mappings;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            // Types arrive already renamed (the remapper runs before the writer). Look them up by
            // their NeoForge names where possible, since that is what the hierarchy holds.
            String a = unmap(type1), b = unmap(type2);
            List<String> chainA = new ArrayList<>();
            for (String c = a; c != null; c = superOf(c)) chainA.add(c);
            for (String c = b; c != null; c = superOf(c)) {
                if (!chainA.contains(c)) continue;
                String m = mappings.mapClass(c);
                // the hierarchy is NeoForge's patched Minecraft: a NeoForge-only parent
                // (BlockEntity extends AttachmentHolder) does not exist on Forge
                if (m.startsWith("net/neoforged/")) continue;
                return m;
            }
            return "java/lang/Object";
        }

        private String superOf(String c) {
            if (c.equals("java/lang/Object")) return null;
            String s = hierarchy.superClass(c);
            if (s != null) return s;
            // JDK types (exceptions merged at catch handlers, ...) are not in the hierarchy
            // JDK and library types (exceptions merged at catch handlers, gson, guava, ...) are not in the
            // hierarchy; the loader can see them without initialising anything
            for (ClassLoader cl : new ClassLoader[]{ClassLoader.getPlatformClassLoader(), ClassConverter.class.getClassLoader()}) {
                try {
                    Class<?> k = Class.forName(c.replace('/', '.'), false, cl);
                    if (k.isInterface()) return "java/lang/Object";
                    Class<?> sup = k.getSuperclass();
                    return sup == null ? "java/lang/Object" : sup.getName().replace('.', '/');
                } catch (ReflectiveOperationException | LinkageError e) {
                    // try the next loader
                }
            }
            return "java/lang/Object";
        }

        private java.util.Map<String, String> reverse;

        private String unmap(String forgeName) {
            if (reverse == null) {
                reverse = new java.util.HashMap<>();
                for (var e : mappings.classes().entrySet()) reverse.putIfAbsent(e.getValue(), e.getKey());
            }
            return reverse.getOrDefault(forgeName, forgeName);
        }
    }
}
