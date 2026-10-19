package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mixins written against NeoForge that shadow or access a vanilla field Forge patched to a subtype
 * (for example {@code ChunkGenerator.featuresPerStep}: Supplier on NeoForge, ClearableLazy on Forge).
 * Mixin matches fields by exact descriptor, so shadows and accessor getters are retyped to Forge's
 * type; callers of a retyped accessor get the new (narrower) return type, which they can use as is.
 */
public final class MixinRetypes {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

    private final Mappings mappings;
    /** accessorOwner.name+oldDesc -> newDesc, collected for the whole jar */
    private final Map<String, String> accessors = new HashMap<>();
    /** setter accessors: accessorOwner.name+oldDesc -> adapter applied to the argument */
    private final Map<String, String> setterAdapters = new HashMap<>();

    public MixinRetypes(Mappings mappings) {
        this.mappings = mappings;
    }

    /** First pass over all classes of a jar: retyped accessor getters. */
    public void scan(byte[] classFile) {
        if (!mappings.hasFieldRetypes()) return;
        ClassNode cn = new ClassNode();
        new ClassReader(classFile).accept(cn, ClassReader.SKIP_CODE);
        List<String> targets = targets(cn);
        if (targets.isEmpty()) return;
        for (MethodNode mn : cn.methods) {
            String field = accessorField(mn);
            if (field == null) continue;
            Type[] args = Type.getArgumentTypes(mn.desc);
            for (String t : targets) {
                String[] r = mappings.fieldRetype(t, field);
                if (r == null) continue;
                if (args.length == 0 && Type.getReturnType(mn.desc).getDescriptor().equals(r[0])) {
                    accessors.put(cn.name + "." + mn.name + mn.desc, "()" + r[1]);
                } else if (args.length == 1 && args[0].getDescriptor().equals(r[0]) && r[2] != null) {
                    String key = cn.name + "." + mn.name + mn.desc;
                    accessors.put(key, "(" + r[1] + ")" + Type.getReturnType(mn.desc).getDescriptor());
                    setterAdapters.put(key, r[2]);
                }
            }
        }
    }

    public void clear() {
        accessors.clear();
    }

    /** Second pass, per class: retype shadows/accessors declared here and calls to retyped accessors. */
    public void apply(ClassNode cn) {
        if (!mappings.hasFieldRetypes()) return;
        List<String> targets = targets(cn);
        Map<String, String> shadowRetypes = new HashMap<>(); // name -> {old, new}
        if (!targets.isEmpty()) {
            for (FieldNode fn : cn.fields) {
                if (!has(fn.visibleAnnotations, SHADOW) && !has(fn.invisibleAnnotations, SHADOW)) continue;
                for (String t : targets) {
                    String[] r = mappings.fieldRetype(t, fn.name);
                    if (r != null && fn.desc.equals(r[0])) {
                        shadowRetypes.put(fn.name, r[0] + " " + r[1] + (r[2] != null ? " " + r[2] : ""));
                        fn.desc = r[1];
                        fn.signature = null;
                    }
                }
            }
            for (MethodNode mn : cn.methods) {
                String nd = accessors.get(cn.name + "." + mn.name + mn.desc);
                if (nd != null) {
                    mn.desc = nd;
                    mn.signature = null;
                }
            }
        }
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof FieldInsnNode fi && fi.owner.equals(cn.name) && shadowRetypes.containsKey(fi.name)) {
                    String[] r = shadowRetypes.get(fi.name).split(" ");
                    if (!fi.desc.equals(r[0])) continue;
                    fi.desc = r[1];
                    if (fi.getOpcode() == Opcodes.PUTFIELD || fi.getOpcode() == Opcodes.PUTSTATIC) {
                        mn.instructions.insertBefore(fi, r.length > 2 ? adapterCall(r[2]) : new TypeInsnNode(Opcodes.CHECKCAST, Type.getType(r[1]).getInternalName()));
                    }
                } else if (insn instanceof MethodInsnNode mi) {
                    String key = mi.owner + "." + mi.name + mi.desc;
                    String nd = accessors.get(key);
                    if (nd != null) {
                        String adapter = setterAdapters.get(key);
                        if (adapter != null) mn.instructions.insertBefore(mi, adapterCall(adapter));
                        mi.desc = nd;
                    }
                }
            }
        }
    }

    /** owner.name(desc), a public static method of a class */
    private static MethodInsnNode adapterCall(String ref) {
        int paren = ref.indexOf('(');
        int dot = ref.lastIndexOf('.', paren);
        return new MethodInsnNode(Opcodes.INVOKESTATIC, ref.substring(0, dot), ref.substring(dot + 1, paren), ref.substring(paren), false);
    }

    private static List<String> targets(ClassNode cn) {
        List<String> out = new ArrayList<>();
        for (List<AnnotationNode> l : List.of(nn(cn.visibleAnnotations), nn(cn.invisibleAnnotations))) {
            for (AnnotationNode a : l) {
                if (!a.desc.equals(MIXIN) || a.values == null) continue;
                for (int i = 0; i < a.values.size(); i += 2) {
                    Object v = a.values.get(i + 1);
                    if (!(v instanceof List<?> list)) continue;
                    for (Object o : list) {
                        if (o instanceof Type t) out.add(t.getInternalName());
                        else if (o instanceof String s) out.add(s.replace('.', '/'));
                    }
                }
            }
        }
        return out;
    }

    private static String accessorField(MethodNode mn) {
        for (AnnotationNode a : nn(mn.visibleAnnotations)) {
            if (!a.desc.equals(ACCESSOR)) continue;
            if (a.values != null) {
                for (int i = 0; i < a.values.size(); i += 2) {
                    if ("value".equals(a.values.get(i)) && a.values.get(i + 1) instanceof String s && !s.isEmpty()) return s;
                }
            }
            String n = mn.name.contains("$") ? mn.name.substring(mn.name.lastIndexOf('$') + 1) : mn.name;
            for (String p : new String[]{"get", "is", "set"}) {
                if (n.startsWith(p) && n.length() > p.length()) return Character.toLowerCase(n.charAt(p.length())) + n.substring(p.length() + 1);
            }
            return n;
        }
        return null;
    }

    private static boolean has(List<AnnotationNode> l, String desc) {
        for (AnnotationNode a : nn(l)) if (a.desc.equals(desc)) return true;
        return false;
    }

    private static <T> List<T> nn(List<T> l) {
        return l == null ? List.of() : l;
    }
}
