package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import java.util.List;

/**
 * A class that used to extend another one and no longer does (1.20.1's ModelResourceLocation was a
 * ResourceLocation; 1.21.1's is a record of its own). Code passing the former where the latter is
 * expected no longer verifies; a type analysis finds those places and inserts a conversion.
 * Runs before renames, on 1.20.1 names; only arguments of calls, field stores and returns are fixed.
 */
public final class SubtypeLossPass {
    private final String sub, sup, convOwner, convName;
    private final ClassHierarchy hierarchy;

    public SubtypeLossPass(String sub, String sup, String convOwner, String convName, ClassHierarchy hierarchy) {
        this.sub = sub;
        this.sup = sup;
        this.convOwner = convOwner;
        this.convName = convName;
        this.hierarchy = hierarchy;
    }

    public void apply(ClassNode cn, byte[] original) {
        if (!new String(original, java.nio.charset.StandardCharsets.ISO_8859_1).contains(sub)) return;
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null || mn.instructions.size() == 0) continue;
            try {
                fix(cn, mn);
            } catch (AnalyzerException | RuntimeException ignored) {
                // leave methods the analysis cannot follow as they are
            }
        }
    }

    private final class Verifier extends SimpleVerifier {
        Verifier(ClassNode cn) {
            super(Opcodes.ASM9, Type.getObjectType(cn.name), cn.superName == null ? null : Type.getObjectType(cn.superName),
                cn.interfaces.stream().map(Type::getObjectType).toList(), (cn.access & Opcodes.ACC_INTERFACE) != 0);
        }

        @Override
        protected boolean isInterface(Type type) {
            return false;
        }

        @Override
        protected Type getSuperClass(Type type) {
            String s = hierarchy.superClass(type.getInternalName());
            if (type.getInternalName().equals(sub)) return Type.getObjectType(sup); // as in 1.20.1
            return s == null ? (type.getInternalName().equals("java/lang/Object") ? null : Type.getObjectType("java/lang/Object")) : Type.getObjectType(s);
        }

        @Override
        protected boolean isAssignableFrom(Type type, Type other) {
            if (type.equals(other)) return true;
            if (type.getSort() != Type.OBJECT || other.getSort() != Type.OBJECT) return super.isAssignableFrom(type, other);
            if (type.getInternalName().equals(sup) && other.getInternalName().equals(sub)) return true;
            if (type.getInternalName().equals("java/lang/Object")) return true;
            if (!hierarchy.knows(other.getInternalName())) return true; // unknown: be permissive
            return hierarchy.isSubtypeOf(other.getInternalName(), type.getInternalName()) || !hierarchy.knows(type.getInternalName());
        }

        @Override
        protected Class<?> getClass(Type type) {
            throw new IllegalStateException("no class loading during conversion");
        }
    }

    private boolean isSub(BasicValue v) {
        return v != null && v.getType() != null && v.getType().getSort() == Type.OBJECT && v.getType().getInternalName().equals(sub);
    }

    private boolean wantsSup(Type t) {
        return t.getSort() == Type.OBJECT && t.getInternalName().equals(sup);
    }

    private void fix(ClassNode cn, MethodNode mn) throws AnalyzerException {
        Analyzer<BasicValue> analyzer = new Analyzer<>(new Verifier(cn));
        Frame<BasicValue>[] frames = analyzer.analyze(cn.name, mn);
        AbstractInsnNode[] insns = mn.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            Frame<BasicValue> f = frames[i];
            if (f == null) continue;
            AbstractInsnNode insn = insns[i];
            int top = f.getStackSize();
            if (insn instanceof MethodInsnNode mi) {
                Type[] args = Type.getArgumentTypes(mi.desc);
                boolean any = false;
                boolean[] conv = new boolean[args.length];
                for (int a = 0; a < args.length; a++) {
                    BasicValue v = f.getStack(top - args.length + a);
                    if (wantsSup(args[a]) && isSub(v)) { conv[a] = true; any = true; }
                }
                if (any) convertArgs(mn, mi, args, conv);
            } else if (insn instanceof FieldInsnNode fi && (fi.getOpcode() == Opcodes.PUTFIELD || fi.getOpcode() == Opcodes.PUTSTATIC)) {
                if (wantsSup(Type.getType(fi.desc)) && isSub(f.getStack(top - 1))) mn.instructions.insertBefore(fi, conversion());
            } else if (insn.getOpcode() == Opcodes.ARETURN) {
                if (wantsSup(Type.getReturnType(mn.desc)) && isSub(f.getStack(top - 1))) mn.instructions.insertBefore(insn, conversion());
            }
        }
    }

    private MethodInsnNode conversion() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, convOwner, convName, "(Ljava/lang/Object;)L" + sup + ";", false);
    }

    /** spill the arguments to fresh locals, then reload them converting the flagged ones */
    private void convertArgs(MethodNode mn, MethodInsnNode mi, Type[] args, boolean[] conv) {
        int last = args.length - 1;
        boolean onlyTop = true;
        for (int a = 0; a < last; a++) if (conv[a]) onlyTop = false;
        if (onlyTop) {
            mn.instructions.insertBefore(mi, conversion());
            return;
        }
        InsnList code = new InsnList();
        int base = mn.maxLocals;
        int[] slots = new int[args.length];
        int next = base;
        for (int a = 0; a < args.length; a++) {
            slots[a] = next;
            next += args[a].getSize();
        }
        for (int a = last; a >= 0; a--) code.add(new VarInsnNode(args[a].getOpcode(Opcodes.ISTORE), slots[a]));
        for (int a = 0; a <= last; a++) {
            code.add(new VarInsnNode(args[a].getOpcode(Opcodes.ILOAD), slots[a]));
            if (conv[a]) code.add(conversion());
        }
        mn.instructions.insertBefore(mi, code);
        mn.maxLocals = next;
        mn.maxStack += 2;
    }

    static List<InsnNode> unused() {
        return List.of();
    }
}
