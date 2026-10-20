package io.github.hugoneale.campat.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/**
 * Generates emulation bridges for Minecraft 1.20.1 members whose signature changed in 1.21.1 only by
 * turning registry objects into {@code Holder}s (MobEffect, Attribute, GameEvent, ...), plus the
 * constructors that changed the same way.
 *
 * <p>Writes a Java class with one static helper per member and the matching R/S redirect lines
 * (keyed on the SRG names and 1.20.1 descriptors the converter sees before renaming).</p>
 *
 * <p>usage: LegacyGen changed.tsv srg.txt forge120_env.txt forge_env.txt out.java bridges.txt</p>
 */
public final class LegacyGen {
    private static final String HOLDER = "net/minecraft/core/Holder";
    private static final String PKG = "io.github.hugoneale.campat.runtime.legacy.gen";
    private static final String CLS = "HolderBridges";
    private static final String INTERNAL = PKG.replace('.', '/') + "/" + CLS;

    private final Map<String, String> classRenames = new HashMap<>();
    private final Map<String, String> srgNames = new HashMap<>();
    private final java.util.TreeSet<String> accessLines = new java.util.TreeSet<>();
    private final Jars old, now;
    private final StringBuilder code = new StringBuilder();
    private final List<String> lines = new ArrayList<>();
    private final Set<String> helperNames = new HashSet<>();
    private final Map<String, Integer> skipped = new TreeMap<>();
    private int evolutions;

    private static final String RT = "io/github/hugoneale/campat/runtime/legacy/";
    private static final String WRAP = RT + "LegacyHolders.wrap(Ljava/lang/Object;)Lnet/minecraft/core/Holder;";
    private static final String UNWRAP = RT + "LegacyHolders.value(Lnet/minecraft/core/Holder;)Ljava/lang/Object;";
    /** old type -> new type conversions: "old>new" -> helper */
    private static final Map<String, String> CONV = Map.of(
        "com/mojang/serialization/Codec>com/mojang/serialization/MapCodec", RT + "LegacyCodecs.mapCodec(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/MapCodec;",
        "com/mojang/serialization/MapCodec>com/mojang/serialization/Codec", RT + "LegacyCodecs.codec(Lcom/mojang/serialization/MapCodec;)Lcom/mojang/serialization/Codec;",
        "net/minecraft/world/level/Level>net/minecraft/world/item/Item$TooltipContext", RT + "Evo.tooltipContext(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/item/Item$TooltipContext;",
        "net/minecraft/world/item/Item$TooltipContext>net/minecraft/world/level/Level", RT + "Evo.level(Lnet/minecraft/world/item/Item$TooltipContext;)Lnet/minecraft/world/level/Level;");
    /** values for arguments only one side has */
    private static final Map<String, String> SUPPLY = Map.of(
        "net/minecraft/core/HolderLookup$Provider", RT + "LegacyRegistries.provider()Lnet/minecraft/core/HolderLookup$Provider;",
        "net/minecraft/core/RegistryAccess", RT + "LegacyRegistries.access()Lnet/minecraft/core/RegistryAccess;",
        "net/minecraft/world/InteractionHand", RT + "Evo.mainHand()Lnet/minecraft/world/InteractionHand;",
        "net/minecraft/commands/CommandBuildContext", RT + "LegacyRegistries.buildContext()Lnet/minecraft/commands/CommandBuildContext;");

    /** conversion helper from {@code a} to {@code b}, "" when equal, null when not convertible */
    private String conv(Type a, Type b) {
        if (a.equals(b)) return "";
        if (numeric(a) && numeric(b)) return "!" + a.getDescriptor() + "2" + b.getDescriptor();
        if (a.getSort() != Type.OBJECT || b.getSort() != Type.OBJECT) return null;
        if (b.getInternalName().equals(HOLDER)) return WRAP;
        if (a.getInternalName().equals(HOLDER)) return UNWRAP;
        String c = CONV.get(a.getInternalName() + ">" + b.getInternalName());
        if (c != null) return c;
        if (subtype(a.getInternalName(), b.getInternalName())) return "";
        if (subtype(b.getInternalName(), a.getInternalName())) return "!cast";
        return null;
    }

    /** align old and new arguments; returns per-target-argument entries using prefix p ("o" or "n") */
    private List<String> align(Type[] from, Type[] to, String p) {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (Type t : to) {
            String c = i < from.length ? conv(from[i], t) : null;
            if (c != null) {
                out.add(p + i + (c.isEmpty() ? "" : ":" + c));
                i++;
                continue;
            }
            // a later source argument may match: skip over droppable source args
            int k = i + 1;
            while (k < from.length && conv(from[k], t) == null) k++;
            if (k < from.length && conv(from[k], t) != null && dropAllowed(from, i, k)) {
                String c2 = conv(from[k], t);
                out.add(p + k + (c2.isEmpty() ? "" : ":" + c2));
                i = k + 1;
                continue;
            }
            String s = t.getSort() == Type.OBJECT ? SUPPLY.get(t.getInternalName()) : null;
            if (s == null) return null;
            out.add("s:" + s);
        }
        return out;
    }

    private static boolean dropAllowed(Type[] from, int i, int k) {
        return true; // source arguments without a counterpart are simply not passed on
    }

    String evolution(String owner120, String name, String desc120, String name121, String desc121) {
        Type[] oa = java.util.Arrays.stream(Type.getArgumentTypes(desc120)).map(this::mapType).toArray(Type[]::new);
        Type[] na = Type.getArgumentTypes(desc121);
        List<String> call = align(oa, na, "o");
        List<String> bridge = align(na, oa, "n");
        if (call == null || bridge == null) return null;
        Type or = mapType(Type.getReturnType(desc120)), nr = Type.getReturnType(desc121);
        String rc = conv(nr, or), rb = conv(or, nr);
        if (rc == null || rb == null) return null;
        String cm = (call.isEmpty() ? "-" : String.join(",", call)) + (rc.isEmpty() ? "" : ";r:" + rc);
        String bm = (bridge.isEmpty() ? "-" : String.join(",", bridge)) + (rb.isEmpty() ? "" : ";r:" + rb);
        return "V " + owner120 + " " + name + " " + desc120 + " " + name121 + " " + desc121 + " " + cm + " " + bm;
    }

    LegacyGen(Jars old, Jars now) {
        this.old = old;
        this.now = now;
    }

    public static void main(String[] a) throws Exception {
        LegacyGen g = new LegacyGen(new Jars(Path.of(a[2])), new Jars(Path.of(a[3])));
        for (String l : Files.readAllLines(Path.of(a[1]))) {
            String[] p = l.split(" ");
            if (p.length == 3 && p[0].equals("C")) g.classRenames.put(p[1], p[2]);
            if (p.length == 3 && p[0].equals("N")) g.srgNames.put(p[1], p[2]);
        }
        for (String l : Files.readAllLines(Path.of(a[0]))) {
            String[] p = l.split("\t", -1);
            if (p.length < 7 || p[2].isEmpty()) continue;
            g.member(p[0], p[1], p[2], p[3], p[4], p[5], p[6]);
        }
        if (a.length > 7) {
            // classes the hand written rules rename are emulated: nothing generated may mention them
            for (String l : Files.readAllLines(Path.of(a[7]))) {
                String[] p = l.strip().split("\\s+");
                if (p.length >= 3 && p[0].equals("C")) EMULATED.add(p[1]);
            }
        }
        g.forgeMembers();
        g.constructors();
        g.packets();
        g.kindChanges();
        g.accessWidening();
        g.write(Path.of(a[4]), Path.of(a[5]));
        if (a.length > 6) g.writeAccess(Path.of(a[6]));
    }

    private String mapClass(String c) {
        String m = classRenames.get(c);
        if (m != null) return m;
        int d = c.lastIndexOf('$');
        if (d > 0) {
            String o = mapClass(c.substring(0, d));
            if (!o.equals(c.substring(0, d))) return o + c.substring(d);
        }
        return c;
    }

    private Type mapType(Type t) {
        if (t.getSort() == Type.OBJECT) return Type.getObjectType(mapClass(t.getInternalName()));
        if (t.getSort() == Type.ARRAY) {
            Type e = mapType(t.getElementType());
            return Type.getType("[".repeat(t.getDimensions()) + e.getDescriptor());
        }
        return t;
    }

    /** per position: 0 same, 1 value -> Holder (wrap), 2 Holder -> value (unwrap), 3 cast; null if not mechanical */
    private int[] holderDiff(Type[] o, Type[] n) {
        if (o.length != n.length) return null;
        int[] r = new int[o.length];
        boolean any = false;
        for (int i = 0; i < o.length; i++) {
            Type a = mapType(o[i]), b = n[i];
            if (a.equals(b)) continue;
            if (b.getSort() == Type.OBJECT && isHolder(b.getInternalName()) && a.getSort() == Type.OBJECT) { r[i] = 1; any = true; }
            else if (a.getSort() == Type.OBJECT && isHolder(a.getInternalName()) && b.getSort() == Type.OBJECT) { r[i] = 2; any = true; }
            else if (b.getSort() == Type.OBJECT && b.getInternalName().equals(RESOURCE_KEY) && a.getSort() == Type.OBJECT && !isHolder(a.getInternalName())
                && !a.getInternalName().startsWith("java/") && allowKeys) { r[i] = 4; any = true; }
            else if (castable(a, b)) { r[i] = 3; any = true; }
            else return null;
        }
        return any ? r : new int[0];
    }

    /** registry objects that became ResourceKeys can only be resolved for field reads */
    private boolean allowKeys;

    private static final String RESOURCE_KEY = "net/minecraft/resources/ResourceKey";

    private static boolean isHolder(String c) {
        return c.equals(HOLDER) || c.equals(HOLDER + "$Reference");
    }

    /** related reference types (either way) or numeric primitives */
    private boolean castable(Type a, Type b) {
        if (a.getSort() == Type.OBJECT && b.getSort() == Type.OBJECT) return subtype(a.getInternalName(), b.getInternalName()) || subtype(b.getInternalName(), a.getInternalName());
        return numeric(a) && numeric(b);
    }

    private static boolean numeric(Type t) {
        int s = t.getSort();
        return s == Type.INT || s == Type.LONG || s == Type.FLOAT || s == Type.DOUBLE || s == Type.SHORT || s == Type.BYTE;
    }

    boolean subtype(String c, String sup) {
        if (c.equals(sup) || sup.equals("java/lang/Object")) return true;
        ClassNode n = now.node(c);
        if (n == null) return false;
        if (n.superName != null && subtype(n.superName, sup)) return true;
        for (String i : n.interfaces) if (subtype(i, sup)) return true;
        return false;
    }

    /** every class named in the descriptor must exist on 1.21.1 after the class renames */
    private boolean linkable(String desc) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("L([^;]+);").matcher(desc);
        while (m.find()) {
            String c = mapClass(m.group(1));
            if ((c.startsWith("net/minecraft/") || c.startsWith("com/mojang/")) && now.node(c) == null) return false;
        }
        return true;
    }

    private void skip(String why) {
        skipped.merge(why, 1, Integer::sum);
    }

    /** classes emulated by hand written rules (tools/legacy/rules.txt); nothing is generated that mentions them */
    private static final List<String> EMULATED = new ArrayList<>(List.of("net/minecraft/world/item/ArmorMaterial"));

    private static boolean emulated(String... texts) {
        for (String t : texts) for (String e : EMULATED) if (t.contains(e + ";") || t.equals(e) || t.contains("L" + e + "$") || t.equals(e + "$")) return true;
        return false;
    }

    void member(String srg, String owner120, String name120, String desc120, String owner121, String name121, String desc121) {
        if (emulated(owner120, desc120)) { skip("emulated by hand"); return; }
        ClassNode nc = now.node(owner121);
        if (nc == null) { skip("owner gone"); return; }
        if (!linkable(desc120)) { skip("uses a class gone in 1.21.1"); return; }
        boolean field = srg.startsWith("f_");
        if (field) {
            FieldNode fn = nc.fields.stream().filter(f -> f.name.equals(name121) && f.desc.equals(desc121)).findFirst().orElse(null);
            if (fn == null || (fn.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_PUBLIC) == 0) { skip("field not public"); return; }
            allowKeys = true;
            int[] d = holderDiff(new Type[]{Type.getType(desc120)}, new Type[]{Type.getType(desc121)});
            allowKeys = false;
            if (d == null || d.length == 0 || (d[0] != 1 && d[0] != 3 && d[0] != 4)) { skip("field: not a holder change"); return; }
            boolean st = (fn.access & Opcodes.ACC_STATIC) != 0;
            Type value = mapType(Type.getType(desc120));
            String h = helperName(owner121, name121);
            String recv = st ? "" : src(Type.getObjectType(owner121)) + " self";
            code.append("    public static ").append(src(value)).append(' ').append(h).append('(').append(recv).append(") {\n")
                .append("        return (").append(src(value)).append(") ").append(d[0] == 4 ? "io.github.hugoneale.campat.runtime.legacy.LegacyRegistries.resolveKey("
                    : value.getInternalName().equals("net/minecraft/core/DefaultedRegistry") ? "io.github.hugoneale.campat.runtime.legacy.LegacyRegistries.defaulted(" : "(Object) ")
                .append(st ? src(Type.getObjectType(owner121)) : "self").append('.').append(name121)
                .append(d[0] == 1 ? ".value();\n    }\n\n" : d[0] == 4 || value.getInternalName().equals("net/minecraft/core/DefaultedRegistry") ? ");\n    }\n\n" : ";\n    }\n\n");
            lines.add("S " + owner120 + " " + srg + " " + desc120 + " " + INTERNAL + " " + h);
            return;
        }
        MethodNode mn = nc.methods.stream().filter(m -> m.name.equals(name121) && m.desc.equals(desc121)).findFirst().orElse(null);
        if (mn == null) { skip("method gone"); return; }
        boolean overridable = (mn.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) == 0
            && (mn.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && (nc.access & Opcodes.ACC_FINAL) == 0;
        if (overridable) {
            String v = evolution(owner120, srg, desc120, name121, desc121);
            if (v != null) { lines.add(v); evolutions++; return; }
            skip("evolution: no argument alignment");
        }
        if ((mn.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_PUBLIC) == 0) {
            // public in 1.20.1: widen it again so the generated helper may call it
            boolean wasPublic = old.node(owner120) != null && old.node(owner120).methods.stream()
                .anyMatch(m -> m.name.equals(srg) && m.desc.equals(desc120) && (m.access & Opcodes.ACC_PUBLIC) != 0 && (m.access & Opcodes.ACC_STATIC) != 0);
            if (!wasPublic || (mn.access & Opcodes.ACC_STATIC) == 0) { skip("method not public"); return; }
            accessLines.add("public " + nc.name.replace('/', '.') + " " + name121 + desc121);
            if ((nc.access & Opcodes.ACC_PUBLIC) == 0) accessLines.add("public " + nc.name.replace('/', '.'));
        }
        Type[] oa = Type.getArgumentTypes(desc120), na = Type.getArgumentTypes(desc121);
        int[] args = holderDiff(oa, na);
        int[] ret = holderDiff(new Type[]{Type.getReturnType(desc120)}, new Type[]{Type.getReturnType(desc121)});
        if (args == null && ret != null && !(nc.access == 0)) {
            int[][] al = align(oa, na);
            boolean sameOwner = mapClass(owner120).equals(owner121) || (mn.access & Opcodes.ACC_STATIC) != 0;
            ClassNode oldOwner = old.node(owner120);
            boolean oldStatic = oldOwner != null && oldOwner.methods.stream().anyMatch(m -> m.name.equals(srg) && m.desc.equals(desc120) && (m.access & Opcodes.ACC_STATIC) != 0);
            if (oldStatic != ((mn.access & Opcodes.ACC_STATIC) != 0)) sameOwner = false;
            if (al != null && sameOwner) { alignedRedirect(srg, owner120, desc120, owner121, name121, desc121, mn, nc, al, ret); return; }
        }
        if (args == null || ret == null) { skip("method: not a holder change"); return; }
        if (args.length == 0 && ret.length == 0) { skip("method: unchanged"); return; }
        boolean st = (mn.access & Opcodes.ACC_STATIC) != 0;
        boolean itf = (nc.access & Opcodes.ACC_INTERFACE) != 0;
        String h = helperName(owner121, name121);
        Type r = mapType(Type.getReturnType(desc120));
        StringBuilder sig = new StringBuilder(), call = new StringBuilder();
        if (!st) sig.append(src(Type.getObjectType(owner121))).append(" self");
        for (int i = 0; i < oa.length; i++) {
            if (sig.length() > 0) sig.append(", ");
            sig.append(src(mapType(oa[i]))).append(" a").append(i);
            if (i > 0) call.append(", ");
            int k = args.length == 0 ? 0 : args[i];
            call.append(k == 1 ? "(net.minecraft.core.Holder) LegacyHolders.wrap(a" + i + ")" : k == 2 ? "(" + src(na[i]) + ") a" + i + ".value()" : k == 3 ? "(" + src(na[i]) + ") (Object) a" + i : "a" + i);
        }
        String target = (st ? src(Type.getObjectType(owner121)) : "self") + "." + name121 + "(" + call + ")";
        if (st && ((mn.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_PUBLIC) == 0)) {
            // only public at runtime (access transformer): call through a method handle
            Type nr = Type.getReturnType(desc121);
            String inv = "io.github.hugoneale.campat.runtime.legacy.LegacyAccess.invokeStatic(\"" + owner121 + "\", \"" + name121 + "\", \"" + desc121 + "\"" + (call.length() > 0 ? ", " + call : "") + ")";
            target = nr.getSort() == Type.VOID ? inv : "((" + src(boxed(nr)) + ") " + inv + ")";
        }
        int rk = ret.length == 0 ? 0 : ret[0];
        code.append("    public static ").append(src(r)).append(' ').append(h).append('(').append(sig).append(") throws Throwable {\n        ");
        if (r.getSort() == Type.VOID) code.append(target).append(";\n");
        else if (rk == 1) code.append("return (").append(src(r)).append(") ((net.minecraft.core.Holder) ").append(target).append(").value();\n");
        else if (rk == 2) code.append("return (").append(src(r)).append(") LegacyHolders.wrap(").append(target).append(");\n");
        else if (rk == 3) code.append("return (").append(src(r)).append(") (Object) ").append(target).append(";\n");
        else code.append("return ").append(target).append(";\n");
        code.append("    }\n\n");
        lines.add("R " + owner120 + " " + srg + " " + desc120 + " " + INTERNAL + " " + h);
        if (itf && !st) skip("(info) interface instance bridges");
    }

    /** Forge 47 -> Forge 52 methods (same class and name, changed parameters); Forge names are not obfuscated */
    void forgeMembers() {
        int before = lines.size();
        for (String c : old.classNames()) {
            if (!c.startsWith("net/minecraftforge/") || emulated(c)) continue;
            ClassNode oc = old.node(c), nc = now.node(mapClass(c));
            if (oc == null || nc == null) continue;
            for (MethodNode om : oc.methods) {
                if ((om.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 || (om.access & Opcodes.ACC_SYNTHETIC) != 0 || om.name.startsWith("<")) continue;
                if (om.name.startsWith("m_") || om.name.startsWith("f_")) continue; // vanilla overrides, handled through SRG
                String desc = mapDesc(om.desc);
                if (nc.methods.stream().anyMatch(m -> m.name.equals(om.name) && m.desc.equals(desc))) continue;
                for (MethodNode nm : nc.methods) {
                    if (!nm.name.equals(om.name) || (nm.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) continue;
                    if (((nm.access ^ om.access) & Opcodes.ACC_STATIC) != 0) continue;
                    int n = lines.size();
                    member(om.name, c, om.name, om.desc, nc.name, nm.name, nm.desc);
                    if (lines.size() > n) break;
                }
            }
        }
        System.out.println((lines.size() - before) + " Forge 47 -> 52 member lines");
    }

    /** a public method whose parameters were reordered, dropped, converted or gained a registry access */
    private void alignedRedirect(String srg, String owner120, String desc120, String owner121, String name121, String desc121, MethodNode mn, ClassNode nc, int[][] al, int[] ret) {
        Type[] oa = Type.getArgumentTypes(desc120), na = Type.getArgumentTypes(desc121);
        boolean st = (mn.access & Opcodes.ACC_STATIC) != 0;
        String h = helperName(owner121, name121);
        Type r = mapType(Type.getReturnType(desc120));
        StringBuilder sig = new StringBuilder(), call = new StringBuilder();
        if (!st) sig.append(src(Type.getObjectType(owner121))).append(" self");
        for (int i = 0; i < oa.length; i++) {
            if (sig.length() > 0) sig.append(", ");
            sig.append(src(mapType(oa[i]))).append(" a").append(i);
        }
        for (int i = 0; i < al.length; i++) {
            if (i > 0) call.append(", ");
            String e = argExpr(al[i], na[i]);
            // explicit casts keep overload resolution and generics happy
            call.append(al[i][1] == 9 ? e : "(" + src(na[i]) + ") " + (na[i].getSort() == Type.OBJECT || na[i].getSort() == Type.ARRAY ? "(Object) " : "") + e);
        }
        String target = (st ? src(Type.getObjectType(owner121)) : "self") + "." + name121 + "(" + call + ")";
        if (st && ((mn.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_PUBLIC) == 0)) {
            // only public at runtime (access transformer): call through a method handle
            Type nr = Type.getReturnType(desc121);
            String inv = "io.github.hugoneale.campat.runtime.legacy.LegacyAccess.invokeStatic(\"" + owner121 + "\", \"" + name121 + "\", \"" + desc121 + "\"" + (call.length() > 0 ? ", " + call : "") + ")";
            target = nr.getSort() == Type.VOID ? inv : "((" + src(boxed(nr)) + ") " + inv + ")";
        }
        int rk = ret.length == 0 ? 0 : ret[0];
        code.append("    @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n    public static ").append(src(r)).append(' ').append(h).append('(').append(sig).append(") throws Throwable {\n        ");
        if (r.getSort() == Type.VOID) code.append(target).append(";\n");
        else if (rk == 1) code.append("return (").append(src(r)).append(") ((net.minecraft.core.Holder) ").append(target).append(").value();\n");
        else if (rk == 2) code.append("return (").append(src(r)).append(") LegacyHolders.wrap(").append(target).append(");\n");
        else if (rk == 3) code.append("return (").append(src(r)).append(") (Object) ").append(target).append(";\n");
        else code.append("return ").append(target).append(";\n");
        code.append("    }\n\n");
        lines.add("R " + owner120 + " " + srg + " " + desc120 + " " + INTERNAL + " " + h);
        aligned++;
    }

    private int aligned;

    private static Type boxed(Type t) {
        return switch (t.getSort()) {
            case Type.BOOLEAN -> Type.getObjectType("java/lang/Boolean");
            case Type.INT -> Type.getObjectType("java/lang/Integer");
            case Type.LONG -> Type.getObjectType("java/lang/Long");
            case Type.FLOAT -> Type.getObjectType("java/lang/Float");
            case Type.DOUBLE -> Type.getObjectType("java/lang/Double");
            case Type.SHORT -> Type.getObjectType("java/lang/Short");
            case Type.BYTE -> Type.getObjectType("java/lang/Byte");
            case Type.CHAR -> Type.getObjectType("java/lang/Character");
            default -> t;
        };
    }

    /** classes that became interfaces (DFU's DataResult, ...) or the other way round */
    void kindChanges() {
        int n = 0;
        for (String c : old.classNames()) {
            ClassNode oc = old.node(c), nc = now.node(mapClass(c));
            if (oc == null || nc == null) continue;
            boolean wasItf = (oc.access & Opcodes.ACC_INTERFACE) != 0, isItf = (nc.access & Opcodes.ACC_INTERFACE) != 0;
            if (wasItf != isItf && !emulated(c)) {
                lines.add((isItf ? "I " : "K ") + c);
                n++;
            }
        }
        System.out.println(n + " classes changed between class and interface");
    }

    /** constructors whose arguments changed by Holder only: argument adapters (A lines, also used by super calls) */
    void constructors() {
        for (String c : old.classNames()) {
            if (!c.startsWith("net/minecraft/") && !c.startsWith("com/mojang/")) continue;
            ClassNode oc = old.node(c), nc = now.node(mapClass(c));
            if (oc == null || nc == null || (nc.access & Opcodes.ACC_PUBLIC) == 0) continue;
            for (MethodNode om : oc.methods) {
                if (!om.name.equals("<init>") || (om.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 || emulated(c, om.desc)) continue;
                String mapped = Type.getMethodDescriptor(Type.VOID_TYPE, java.util.Arrays.stream(Type.getArgumentTypes(om.desc)).map(this::mapType).toArray(Type[]::new));
                if (nc.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(mapped))) continue;
                MethodNode best = null;
                int[][] bestAlign = null;
                for (MethodNode nm : nc.methods) {
                    if (!nm.name.equals("<init>") || (nm.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) continue;
                    if (!linkable(om.desc)) continue;
                    int[][] al = align(Type.getArgumentTypes(om.desc), Type.getArgumentTypes(nm.desc));
                    if (al == null) continue;
                    // prefer the candidate that keeps the most old arguments
                    if (best == null || Type.getArgumentTypes(nm.desc).length > Type.getArgumentTypes(best.desc).length) {
                        best = nm;
                        bestAlign = al;
                    }
                }
                if (best == null) continue;
                Type[] oa = Type.getArgumentTypes(om.desc);
                String h = helperName(nc.name, "args");
                StringBuilder sig = new StringBuilder(), call = new StringBuilder();
                for (int i = 0; i < oa.length; i++) {
                    if (i > 0) sig.append(", ");
                    sig.append(src(mapType(oa[i]))).append(" a").append(i);
                }
                Type[] nbest = Type.getArgumentTypes(best.desc);
                for (int i = 0; i < bestAlign.length; i++) {
                    if (i > 0) call.append(", ");
                    call.append(argExpr(bestAlign[i], nbest[i]));
                }
                code.append("    public static Object[] ").append(h).append('(').append(sig).append(") {\n        return new Object[]{")
                    .append(call).append("};\n    }\n\n");
                lines.add("A " + c + " " + om.desc + " " + best.desc + " " + INTERNAL + " " + h);
            }
        }
    }

    /**
     * Members that were public in 1.20.1 and still exist (same name after SRG mapping, same descriptor
     * after class renames) but are no longer public: an access transformer widens them again.
     */
    void accessWidening() {
        for (String c : old.classNames()) {
            if (!c.startsWith("net/minecraft/") && !c.startsWith("com/mojang/")) continue;
            ClassNode oc = old.node(c), nc = now.node(mapClass(c));
            if (oc == null || nc == null || (oc.access & Opcodes.ACC_PUBLIC) == 0) continue;
            String dotted = nc.name.replace('/', '.');
            if ((nc.access & Opcodes.ACC_PUBLIC) == 0) accessLines.add("public " + dotted);
            for (MethodNode om : oc.methods) {
                if ((om.access & Opcodes.ACC_PUBLIC) == 0 || (om.access & Opcodes.ACC_SYNTHETIC) != 0 || om.name.equals("<clinit>")) continue;
                String name = srgNames.getOrDefault(om.name, om.name);
                String desc = mapDesc(om.desc);
                for (MethodNode nm : nc.methods) {
                    if (nm.name.equals(name) && nm.desc.equals(desc) && (nm.access & Opcodes.ACC_PUBLIC) == 0) {
                        accessLines.add("public " + dotted + " " + name + desc);
                    }
                }
            }
            for (FieldNode of : oc.fields) {
                if ((of.access & Opcodes.ACC_PUBLIC) == 0 || (of.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
                String name = srgNames.getOrDefault(of.name, of.name);
                for (FieldNode nf : nc.fields) {
                    if (nf.name.equals(name) && (nf.access & Opcodes.ACC_PUBLIC) == 0) accessLines.add("public " + dotted + " " + name);
                }
            }
        }
        System.out.println(accessLines.size() + " access widenings");
    }

    private String mapDesc(String desc) {
        Type t = Type.getMethodType(desc);
        Type[] args = java.util.Arrays.stream(t.getArgumentTypes()).map(this::mapType).toArray(Type[]::new);
        return Type.getMethodDescriptor(mapType(t.getReturnType()), args);
    }

    void writeAccess(Path at) throws java.io.IOException {
        String begin = "# BEGIN Minecraft 1.20.1 public members (generated by tools LegacyGen)", end = "# END Minecraft 1.20.1 public members";
        String text = Files.readString(at);
        int b = text.indexOf(begin), e = text.indexOf(end);
        if (b >= 0 && e > b) text = text.substring(0, b) + text.substring(e + end.length()).stripLeading();
        if (!text.endsWith("\n")) text += "\n";
        text += begin + "\n" + String.join("\n", accessLines) + "\n" + end + "\n";
        Files.writeString(at, text);
    }

    /**
     * Packets (and other network types) whose 1.20.1 {@code (FriendlyByteBuf)} constructor and
     * {@code write(FriendlyByteBuf)} method became a private part of a {@code STREAM_CODEC}.
     */
    void packets() {
        String fbb = "(Lnet/minecraft/network/FriendlyByteBuf;)V";
        for (String c : old.classNames()) {
            if (!c.startsWith("net/minecraft/")) continue;
            ClassNode oc = old.node(c), nc = now.node(mapClass(c));
            if (oc == null || nc == null || (nc.access & Opcodes.ACC_PUBLIC) == 0) continue;
            boolean oldCtor = oc.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(fbb) && (m.access & Opcodes.ACC_PUBLIC) != 0);
            if (!oldCtor) continue;
            boolean newCtor = nc.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(fbb) && (m.access & Opcodes.ACC_PUBLIC) != 0);
            if (newCtor) continue;
            boolean codec = nc.fields.stream().anyMatch(f -> f.name.equals("STREAM_CODEC") && (f.access & Opcodes.ACC_STATIC) != 0 && (f.access & Opcodes.ACC_PUBLIC) != 0);
            if (!codec) { skip("packet without public STREAM_CODEC"); continue; }
            String owner = src(Type.getObjectType(nc.name));
            String h = helperName(nc.name, "fromBuf");
            code.append("    @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n    public static ").append(owner).append(' ').append(h)
                .append("(net.minecraft.network.FriendlyByteBuf buf) {\n        return (").append(owner).append(") ((net.minecraft.network.codec.StreamCodec) ")
                .append(owner).append(".STREAM_CODEC).decode(io.github.hugoneale.campat.runtime.legacy.LegacyBufs.registryBuf(buf));\n    }\n\n");
            lines.add("R " + c + " <init> " + fbb + " " + INTERNAL + " " + h);
        }
    }

    /**
     * Matches every new parameter to an old one of a compatible type (same, Holder wrap/unwrap, related
     * reference type), in order; old parameters may be reordered or dropped (only primitives and
     * booleans may be dropped). @return per new parameter {oldIndex, kind}, or null
     */
    /** new parameters that can be supplied when old callers do not have them */
    private static final Map<String, String> SUPPLIERS = Map.of(
        "Lnet/minecraft/core/HolderLookup$Provider;", "io.github.hugoneale.campat.runtime.legacy.LegacyRegistries.provider()",
        "Lnet/minecraft/core/RegistryAccess;", "io.github.hugoneale.campat.runtime.legacy.LegacyRegistries.access()",
        "Lnet/minecraft/commands/CommandBuildContext;", "io.github.hugoneale.campat.runtime.legacy.LegacyRegistries.buildContext()");

    private static String argExpr(int[] al, Type target) {
        int j = al[0], k = al[1];
        if (k == 9) return SUPPLIERS.get(target.getDescriptor());
        if (k == 5) return "io.github.hugoneale.campat.runtime.legacy.LegacyCodecs.mapCodec(a" + j + ")";
        return k == 1 ? "LegacyHolders.wrap(a" + j + ")" : k == 2 ? "a" + j + ".value()" : "a" + j;
    }

    private int[][] align(Type[] o, Type[] n) {
        int[][] r = new int[n.length][];
        boolean[] used = new boolean[o.length];
        boolean changed = n.length != o.length;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n.length; i++) {
                if (r[i] != null) continue;
                for (int j = 0; j < o.length; j++) {
                    if (used[j]) continue;
                    Type a = mapType(o[j]);
                    int kind;
                    if (a.equals(n[i])) kind = 0;
                    else if (pass == 0) continue;
                    else if (a.getDescriptor().equals("Lcom/mojang/serialization/Codec;") && n[i].getDescriptor().equals("Lcom/mojang/serialization/MapCodec;")) kind = 5;
                    else {
                        int[] d = holderDiff(new Type[]{o[j]}, new Type[]{n[i]});
                        // numeric conversions only for parameters that stay in place
                        if (d == null || d.length == 0 || d[0] == 3 && a.getSort() != Type.OBJECT && j != i) continue;
                        kind = d[0];
                        if (kind == 4) continue;
                    }
                    r[i] = new int[]{j, kind};
                    used[j] = true;
                    if (j != i || kind != 0) changed = true;
                    break;
                }
            }
        }
        for (int i = 0; i < n.length; i++) {
            if (r[i] != null) continue;
            String supply = SUPPLIERS.get(n[i].getDescriptor());
            if (supply == null) return null;
            r[i] = new int[]{-1, 9};
            changed = true;
        }
        for (int j = 0; j < o.length; j++) {
            if (used[j]) continue;
            if (o[j].getSort() == Type.OBJECT || o[j].getSort() == Type.ARRAY) return null; // never drop objects silently
        }
        return changed ? r : null;
    }

    private String helperName(String owner, String name) {
        String base = owner.substring(owner.lastIndexOf('/') + 1).replace('$', '_') + "_" + name.replace("<", "").replace(">", "");
        String h = base;
        for (int i = 2; !helperNames.add(h); i++) h = base + i;
        return h;
    }

    private static String src(Type t) {
        return switch (t.getSort()) {
            case Type.ARRAY -> src(t.getElementType()) + "[]".repeat(t.getDimensions());
            case Type.OBJECT -> t.getClassName().replace('$', '.');
            default -> t.getClassName();
        };
    }

    void write(Path javaOut, Path linesOut) throws Exception {
        Files.createDirectories(javaOut.getParent());
        String header = "package " + PKG + ";\n\nimport io.github.hugoneale.campat.runtime.legacy.LegacyHolders;\n\n"
            + "/** Generated by tools LegacyGen: Minecraft 1.20.1 members whose registry objects became Holders in 1.21.1. */\n"
            + "@SuppressWarnings({\"unchecked\", \"rawtypes\", \"deprecation\", \"removal\"})\npublic final class " + CLS + " {\n    private " + CLS + "() {}\n\n";
        Files.writeString(javaOut, header + code + "}\n");
        Files.writeString(linesOut, "# generated by tools LegacyGen (Holder bridges)\n" + String.join("\n", lines) + "\n");
        System.out.println(lines.size() + " lines (" + evolutions + " evolutions, " + aligned + " aligned redirects); skipped " + skipped);
    }

    /** class lookup over a platform's jars */
    static final class Jars {
        private final Map<String, Path> where = new HashMap<>();
        private final Map<String, ClassNode> cache = new HashMap<>();

        Jars(Path list) throws Exception {
            for (String l : Files.readAllLines(list)) {
                if (l.isBlank()) continue;
                Path j = Path.of(l.strip());
                try (ZipFile z = new ZipFile(j.toFile())) {
                    var en = z.entries();
                    while (en.hasMoreElements()) {
                        String n = en.nextElement().getName();
                        if (n.endsWith(".class")) where.putIfAbsent(n.substring(0, n.length() - 6), j);
                    }
                }
            }
        }

        Set<String> classNames() {
            return where.keySet();
        }

        ClassNode node(String name) {
            return cache.computeIfAbsent(name, n -> {
                Path j = where.get(n);
                if (j == null) return null;
                try (ZipFile z = new ZipFile(j.toFile()); InputStream in = z.getInputStream(z.getEntry(n + ".class"))) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
                    return cn;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
