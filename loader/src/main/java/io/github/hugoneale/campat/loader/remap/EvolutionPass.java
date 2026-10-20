package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps mod code working across a method signature change of a platform virtual method (for example
 * {@code BlockEntity.saveAdditional(CompoundTag)} gaining a {@code HolderLookup.Provider}).
 *
 * <ul>
 *   <li><b>Overrides</b>: a mod class that declares the old signature gets a bridge with the new
 *   signature that calls its own old method non-virtually ({@code invokespecial}), so the platform's
 *   calls reach the mod's code and super chains through other converted classes do not loop.</li>
 *   <li><b>Calls</b>: every call of the old signature (including {@code super} calls) goes through a
 *   synthetic method of the calling class that adapts the arguments and calls the new signature.</li>
 * </ul>
 *
 * <p>Maps are comma separated, {@code -} for none, with an optional {@code ;r:<conv>} return part:</p>
 * <pre>
 * callMap   one entry per NEW argument:  o&lt;i&gt;  |  o&lt;i&gt;:&lt;conv&gt;  |  s:&lt;supplier&gt;
 * bridgeMap one entry per OLD argument:  n&lt;i&gt;  |  n&lt;i&gt;:&lt;conv&gt;  |  s:&lt;supplier&gt;
 *           a {@code super:} prefix adapts only super calls (other calls are left to a redirect)
 * conv / supplier:  owner.name(desc)  -- a public static method; results are cast to the target type
 * </pre>
 * Runs on pre-rename names, after nothing else has touched the class.
 */
public final class EvolutionPass {
    /** callMap prefix: only {@code super} calls are adapted */
    private static final String SUPER_ONLY = "super:";

    private final Mappings mappings;
    private final ClassHierarchy hierarchy;

    public EvolutionPass(Mappings mappings, ClassHierarchy hierarchy) {
        this.mappings = mappings;
        this.hierarchy = hierarchy;
    }

    public void apply(ClassNode cn) {
        if (!mappings.hasEvolutions()) return;
        boolean itf = (cn.access & Opcodes.ACC_INTERFACE) != 0;
        Map<String, MethodNode> synthetic = new HashMap<>();
        // 1. calls
        for (MethodNode mn : new ArrayList<>(cn.methods)) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode mi) || mi.getOpcode() == Opcodes.INVOKESTATIC) continue;
                Mappings.Evolution ev = find(mi.owner, mi.name, mi.desc);
                if (ev == null) continue;
                boolean special = mi.getOpcode() == Opcodes.INVOKESPECIAL;
                if (!special && ev.callMap().startsWith(SUPER_ONLY)) continue; // other calls are handled by a redirect
                if (special && itf) continue; // default method super calls: rare, leave
                String key = mi.getOpcode() + " " + mi.owner + " " + ev.name() + ev.oldDesc() + "->" + ev.newName() + ev.newDesc();
                MethodNode s = synthetic.computeIfAbsent(key, k -> callAdapter(cn, mi, ev, special, synthetic.size()));
                MethodInsnNode call = special
                    ? new MethodInsnNode(Opcodes.INVOKESPECIAL, cn.name, s.name, s.desc, false)
                    : new MethodInsnNode(Opcodes.INVOKESTATIC, cn.name, s.name, s.desc, itf);
                mn.instructions.set(mi, call);
            }
        }
        cn.methods.addAll(synthetic.values());
        // 2. overrides
        List<MethodNode> bridges = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            if ((mn.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_ABSTRACT)) != 0) continue;
            for (Mappings.Evolution ev : mappings.evolutions(mn.name)) {
                if (!ev.oldDesc().equals(mn.desc) || !hierarchy.isSubtypeOf(cn.name, ev.owner()) || cn.name.equals(ev.owner())) continue;
                if (declares(cn, ev.newName(), ev.newDesc()) || bridges.stream().anyMatch(b -> b.name.equals(ev.newName()) && b.desc.equals(ev.newDesc()))) continue;
                bridges.add(bridge(cn, mn, ev, itf));
            }
        }
        cn.methods.addAll(bridges);
    }

    private Mappings.Evolution find(String owner, String name, String desc) {
        for (Mappings.Evolution ev : mappings.evolutions(name)) {
            if (ev.oldDesc().equals(desc) && (owner.equals(ev.owner()) || hierarchy.isSubtypeOf(owner, ev.owner()))) return ev;
        }
        return null;
    }

    private static boolean declares(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
        return false;
    }

    /** synthetic method taking the OLD arguments (plus the receiver unless it is a super call) and calling the NEW method */
    private MethodNode callAdapter(ClassNode cn, MethodInsnNode mi, Mappings.Evolution ev, boolean special, int index) {
        Type[] oldArgs = Type.getArgumentTypes(ev.oldDesc());
        Type[] newArgs = Type.getArgumentTypes(ev.newDesc());
        Type oldRet = Type.getReturnType(ev.oldDesc()), newRet = Type.getReturnType(ev.newDesc());
        String desc;
        int firstArg;
        if (special) {
            desc = ev.oldDesc();
            firstArg = 1;
        } else {
            Type[] withRecv = new Type[oldArgs.length + 1];
            withRecv[0] = Type.getObjectType(mi.owner);
            System.arraycopy(oldArgs, 0, withRecv, 1, oldArgs.length);
            desc = Type.getMethodDescriptor(oldRet, withRecv);
            firstArg = 1; // receiver is local 0
        }
        int access = Opcodes.ACC_SYNTHETIC | (special ? Opcodes.ACC_PRIVATE : Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0 && !special) access = Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        MethodNode m = new MethodNode(access, "campat$evo$" + index, desc, null, null);
        InsnList il = m.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int[] slots = slots(oldArgs, firstArg);
        Parsed map = parse(ev.callMap().startsWith(SUPER_ONLY) ? ev.callMap().substring(SUPER_ONLY.length()) : ev.callMap(), newArgs.length);
        for (int j = 0; j < newArgs.length; j++) load(il, map.entries.get(j), oldArgs, slots, newArgs[j]);
        int op = special ? Opcodes.INVOKESPECIAL : mi.getOpcode();
        il.add(new MethodInsnNode(op, mi.owner, ev.newName(), ev.newDesc(), mi.itf));
        convertReturn(il, map.ret, newRet, oldRet);
        il.add(new InsnNode(oldRet.getOpcode(Opcodes.IRETURN)));
        m.maxLocals = slots.length == 0 ? firstArg : slots[slots.length - 1] + oldArgs[oldArgs.length - 1].getSize();
        m.maxStack = 4 + Type.getArgumentsAndReturnSizes(ev.newDesc());
        return m;
    }

    /** method with the NEW signature calling the class's own OLD method */
    private MethodNode bridge(ClassNode cn, MethodNode old, Mappings.Evolution ev, boolean itf) {
        Type[] oldArgs = Type.getArgumentTypes(ev.oldDesc());
        Type[] newArgs = Type.getArgumentTypes(ev.newDesc());
        Type oldRet = Type.getReturnType(ev.oldDesc()), newRet = Type.getReturnType(ev.newDesc());
        int access = (old.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_SYNTHETIC;
        if ((old.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) access |= Opcodes.ACC_PUBLIC;
        MethodNode m = new MethodNode(access, ev.newName(), ev.newDesc(), null, old.exceptions == null ? null : old.exceptions.toArray(new String[0]));
        InsnList il = m.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int[] slots = slots(newArgs, 1);
        Parsed map = parse(ev.bridgeMap(), oldArgs.length);
        for (int j = 0; j < oldArgs.length; j++) load(il, map.entries.get(j), newArgs, slots, oldArgs[j]);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, cn.name, old.name, old.desc, itf));
        convertReturn(il, map.ret, oldRet, newRet);
        il.add(new InsnNode(newRet.getOpcode(Opcodes.IRETURN)));
        m.maxLocals = slots.length == 0 ? 1 : slots[slots.length - 1] + newArgs[newArgs.length - 1].getSize();
        m.maxStack = 4 + Type.getArgumentsAndReturnSizes(ev.oldDesc());
        return m;
    }

    private record Parsed(List<String> entries, String ret) {}

    private static Parsed parse(String spec, int expected) {
        String ret = null;
        int semi = spec.indexOf(";r:");
        if (semi >= 0) {
            ret = spec.substring(semi + 3);
            spec = spec.substring(0, semi);
        }
        List<String> e = spec.equals("-") || spec.isEmpty() ? List.of() : List.of(spec.split(","));
        if (e.size() != expected) throw new IllegalArgumentException("evolution map '" + spec + "' needs " + expected + " entries");
        return new Parsed(e, ret);
    }

    private static int[] slots(Type[] args, int first) {
        int[] s = new int[args.length];
        int at = first;
        for (int i = 0; i < args.length; i++) {
            s[i] = at;
            at += args[i].getSize();
        }
        return s;
    }

    /** pushes one target argument of type {@code target} described by {@code entry} */
    private static void load(InsnList il, String entry, Type[] sourceArgs, int[] slots, Type target) {
        if (entry.startsWith("s:")) {
            call(il, entry.substring(2), target);
            return;
        }
        int colon = entry.indexOf(':');
        int i = Integer.parseInt(colon < 0 ? entry.substring(1) : entry.substring(1, colon));
        il.add(new VarInsnNode(sourceArgs[i].getOpcode(Opcodes.ILOAD), slots[i]));
        if (colon >= 0) call(il, entry.substring(colon + 1), target);
    }

    private static int primitive(char from, char to) {
        char f = from == 'S' || from == 'B' ? 'I' : from, t = to;
        int op = switch ("" + f + t) {
            case "ID" -> Opcodes.I2D; case "IF" -> Opcodes.I2F; case "IJ" -> Opcodes.I2L;
            case "DI" -> Opcodes.D2I; case "DF" -> Opcodes.D2F; case "DJ" -> Opcodes.D2L;
            case "FI" -> Opcodes.F2I; case "FD" -> Opcodes.F2D; case "FJ" -> Opcodes.F2L;
            case "JI" -> Opcodes.L2I; case "JD" -> Opcodes.L2D; case "JF" -> Opcodes.L2F;
            case "IS" -> Opcodes.I2S; case "IB" -> Opcodes.I2B;
            default -> Opcodes.NOP;
        };
        return op;
    }

    private static void convertReturn(InsnList il, String conv, Type from, Type to) {
        if (conv != null && !conv.isEmpty()) call(il, conv, to);
        else if (!from.equals(to) && to.getSort() == Type.VOID) il.add(new InsnNode(from.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
    }

    /** owner.name(desc) static call, then a cast to {@code target} when the helper returns something wider */
    private static void call(InsnList il, String ref, Type target) {
        if (ref.equals("!cast")) {
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, target.getInternalName()));
            return;
        }
        if (ref.startsWith("!")) { // primitive conversion such as !D2F
            il.add(new InsnNode(primitive(ref.charAt(1), ref.charAt(3))));
            return;
        }
        int dot = ref.lastIndexOf('.', ref.indexOf('('));
        String owner = ref.substring(0, dot), rest = ref.substring(dot + 1);
        int paren = rest.indexOf('(');
        String name = rest.substring(0, paren), desc = rest.substring(paren);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false));
        Type r = Type.getReturnType(desc);
        if ((target.getSort() == Type.OBJECT || target.getSort() == Type.ARRAY) && !r.equals(target)) {
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, target.getInternalName()));
        }
    }
}
