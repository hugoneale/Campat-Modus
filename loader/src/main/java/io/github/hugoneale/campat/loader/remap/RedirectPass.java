package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
                Found f = findMethod(mi.owner, mi.name, mi.desc);
                if (f != null) {
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
