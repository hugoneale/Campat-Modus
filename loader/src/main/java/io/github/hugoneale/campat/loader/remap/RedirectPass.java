package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Rewrites call sites and field reads whose NeoForge member has no binary compatible Forge
 * counterpart into calls to static helpers in the compatibility runtime. Runs on NeoForge names,
 * before the class rename table is applied.
 */
public final class RedirectPass {
    private final Mappings mappings;
    private final ClassHierarchy hierarchy;

    public RedirectPass(Mappings mappings, ClassHierarchy hierarchy) {
        this.mappings = mappings;
        this.hierarchy = hierarchy;
    }

    /** @return human readable problems (e.g. a super constructor call that cannot be redirected) */
    public List<String> apply(ClassNode cn) {
        List<String> problems = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null || mn.instructions.size() == 0) continue;
            redirectMethod(cn, mn, problems);
        }
        return problems;
    }

    private record Found(String owner, Mappings.Redirect redirect) {}

    private Found findMethod(String owner, String name, String desc) {
        if (owner.startsWith("[")) return null;
        Mappings.Redirect[] r = new Mappings.Redirect[1];
        String decl = name.equals("<init>")
            ? (mappings.hasMembers(owner) && (r[0] = mappings.methodRedirect(owner, name, desc)) != null ? owner : null)
            : hierarchy.find(owner, c -> mappings.hasMembers(c) && (r[0] = mappings.methodRedirect(c, name, desc)) != null);
        return decl == null ? null : new Found(decl, r[0]);
    }

    private Found findField(String owner, String name, String desc) {
        Mappings.Redirect[] r = new Mappings.Redirect[1];
        String decl = hierarchy.find(owner, c -> mappings.hasMembers(c) && (r[0] = mappings.fieldRedirect(c, name, desc)) != null);
        return decl == null ? null : new Found(decl, r[0]);
    }

    private void redirectMethod(ClassNode cn, MethodNode mn, List<String> problems) {
        InsnList insns = mn.instructions;
        // pending NEW instructions so constructor redirects can drop their NEW/DUP pair
        Deque<TypeInsnNode> pendingNew = new ArrayDeque<>();
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof TypeInsnNode ti && ti.getOpcode() == Opcodes.NEW) {
                pendingNew.push(ti);
            } else if (insn instanceof MethodInsnNode mi) {
                TypeInsnNode matchedNew = null;
                if (mi.name.equals("<init>") && mi.getOpcode() == Opcodes.INVOKESPECIAL) {
                    if (!pendingNew.isEmpty() && pendingNew.peek().desc.equals(mi.owner)) matchedNew = pendingNew.pop();
                }
                Mappings.CtorAdapter ca = mi.name.equals("<init>") ? mappings.ctorAdapter(mi.owner, mi.desc) : null;
                Found f = ca != null ? null : findMethod(mi.owner, mi.name, mi.desc);
                if (ca != null) {
                    adaptConstructorCall(mn, mi, ca);
                } else if (f != null) {
                    if (mi.name.equals("<init>")) {
                        AbstractInsnNode dup = matchedNew == null ? null : matchedNew.getNext();
                        if (matchedNew == null || dup == null || dup.getOpcode() != Opcodes.DUP) {
                            problems.add(cn.name + "." + mn.name + ": cannot redirect constructor call " + mi.owner + ".<init>" + mi.desc
                                + (matchedNew == null ? " (super/this call from a subclass)" : " (unexpected bytecode shape)"));
                        } else {
                            insns.remove(dup);
                            insns.remove(matchedNew);
                            String desc = f.redirect().targetDesc() != null ? f.redirect().targetDesc()
                                : Type.getMethodDescriptor(Type.getObjectType(mi.owner), Type.getArgumentTypes(mi.desc));
                            insns.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, f.redirect().targetOwner(), f.redirect().targetName(), desc, false));
                        }
                    } else {
                        String desc;
                        if (f.redirect().targetDesc() != null) {
                            desc = f.redirect().targetDesc();
                        } else if (mi.getOpcode() == Opcodes.INVOKESTATIC) {
                            desc = mi.desc;
                        } else {
                            Type[] args = Type.getArgumentTypes(mi.desc);
                            Type[] withRecv = new Type[args.length + 1];
                            withRecv[0] = Type.getObjectType(f.owner());
                            System.arraycopy(args, 0, withRecv, 1, args.length);
                            desc = Type.getMethodDescriptor(Type.getReturnType(mi.desc), withRecv);
                        }
                        if (mi.getOpcode() == Opcodes.INVOKESPECIAL) {
                            problems.add(cn.name + "." + mn.name + ": super call to redirected method " + mi.owner + "." + mi.name + mi.desc + " kept as is");
                        } else {
                            insns.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, f.redirect().targetOwner(), f.redirect().targetName(), desc, false));
                        }
                    }
                }
            } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
                // method references (Foo::bar) to redirected members
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (!(indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h)) continue;
                    org.objectweb.asm.Handle r = redirectHandle(h);
                    if (r != null) indy.bsmArgs[i] = r;
                }
            } else if (insn instanceof FieldInsnNode fi && (fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.GETFIELD)) {
                Found f = findField(fi.owner, fi.name, fi.desc);
                if (f != null) {
                    String desc = f.redirect().targetDesc() != null ? f.redirect().targetDesc()
                        : fi.getOpcode() == Opcodes.GETSTATIC ? "()" + fi.desc : "(L" + f.owner() + ";)" + fi.desc;
                    insns.set(fi, new MethodInsnNode(Opcodes.INVOKESTATIC, f.redirect().targetOwner(), f.redirect().targetName(), desc, false));
                }
            }
            insn = next;
        }
    }

    /**
     * Rewrites a constructor call (NEW site or super/this call) whose parameters changed: the old
     * arguments are spilled to fresh locals (legal before the super call, {@code this} stays on the
     * stack untouched), converted by the static adapter into an Object[] and unpacked again.
     * Straight line code, so existing stack map frames stay valid.
     */
    private static void adaptConstructorCall(MethodNode mn, MethodInsnNode mi, Mappings.CtorAdapter ca) {
        Type[] oldArgs = Type.getArgumentTypes(mi.desc);
        Type[] newArgs = Type.getArgumentTypes(ca.newDesc());
        InsnList code = new InsnList();
        int base = mn.maxLocals;
        int[] slots = new int[oldArgs.length];
        int next = base;
        for (int i = 0; i < oldArgs.length; i++) {
            slots[i] = next;
            next += oldArgs[i].getSize();
        }
        for (int i = oldArgs.length - 1; i >= 0; i--) code.add(new VarInsnNode(oldArgs[i].getOpcode(Opcodes.ISTORE), slots[i]));
        for (int i = 0; i < oldArgs.length; i++) code.add(new VarInsnNode(oldArgs[i].getOpcode(Opcodes.ILOAD), slots[i]));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ca.adapterOwner(), ca.adapterName(),
            Type.getMethodDescriptor(Type.getType(Object[].class), oldArgs), false));
        int arr = next;
        code.add(new VarInsnNode(Opcodes.ASTORE, arr));
        int newSlots = 0;
        for (int i = 0; i < newArgs.length; i++) {
            code.add(new VarInsnNode(Opcodes.ALOAD, arr));
            code.add(new LdcInsnNode(i));
            code.add(new InsnNode(Opcodes.AALOAD));
            unbox(code, newArgs[i]);
            newSlots += newArgs[i].getSize();
        }
        mn.instructions.insertBefore(mi, code);
        mi.desc = ca.newDesc();
        mn.maxLocals = arr + 1;
        mn.maxStack += newSlots + 4;
    }

    private static void unbox(InsnList code, Type t) {
        String box = switch (t.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.CHAR -> "java/lang/Character";
            case Type.BYTE, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> "java/lang/Number";
            default -> null;
        };
        if (box == null) {
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, t.getInternalName()));
            return;
        }
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, box));
        String m = switch (t.getSort()) {
            case Type.BOOLEAN -> "booleanValue";
            case Type.CHAR -> "charValue";
            case Type.BYTE -> "byteValue";
            case Type.SHORT -> "shortValue";
            case Type.INT -> "intValue";
            case Type.LONG -> "longValue";
            case Type.FLOAT -> "floatValue";
            default -> "doubleValue";
        };
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, box, m, "()" + t.getDescriptor(), false));
    }

    private org.objectweb.asm.Handle redirectHandle(org.objectweb.asm.Handle h) {
        int tag = h.getTag();
        if (tag != Opcodes.H_INVOKEVIRTUAL && tag != Opcodes.H_INVOKEINTERFACE && tag != Opcodes.H_INVOKESTATIC && tag != Opcodes.H_NEWINVOKESPECIAL) return null;
        Found f = findMethod(h.getOwner(), h.getName(), h.getDesc());
        if (f == null) return null;
        String desc;
        if (f.redirect().targetDesc() != null) desc = f.redirect().targetDesc();
        else if (tag == Opcodes.H_NEWINVOKESPECIAL) desc = Type.getMethodDescriptor(Type.getObjectType(h.getOwner()), Type.getArgumentTypes(h.getDesc()));
        else if (tag == Opcodes.H_INVOKESTATIC) desc = h.getDesc();
        else {
            Type[] args = Type.getArgumentTypes(h.getDesc());
            Type[] withRecv = new Type[args.length + 1];
            withRecv[0] = Type.getObjectType(f.owner());
            System.arraycopy(args, 0, withRecv, 1, args.length);
            desc = Type.getMethodDescriptor(Type.getReturnType(h.getDesc()), withRecv);
        }
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, f.redirect().targetOwner(), f.redirect().targetName(), desc, false);
    }
}
