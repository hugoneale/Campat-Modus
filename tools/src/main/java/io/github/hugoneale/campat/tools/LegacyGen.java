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
        "net/minecraft/world/level/Level>net/minecraft/world/item/Item$TooltipContext", RT + "Evo.tooltipContext(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/item/Item$TooltipContext;",
        "net/minecraft/world/item/Item$TooltipContext>net/minecraft/world/level/Level", RT + "Evo.level(Lnet/minecraft/world/item/Item$TooltipContext;)Lnet/minecraft/world/level/Level;");
    /** values for arguments only one side has */
    private static final Map<String, String> SUPPLY = Map.of(
        "net/minecraft/core/HolderLookup$Provider", RT + "LegacyRegistries.provider()Lnet/minecraft/core/HolderLookup$Provider;",
        "net/minecraft/core/RegistryAccess", RT + "LegacyRegistries.access()Lnet/minecraft/core/RegistryAccess;",
        "net/minecraft/world/InteractionHand", RT + "Evo.mainHand()Lnet/minecraft/world/InteractionHand;");

    /** conversion helper from {@code a} to {@code b}, "" when equal, null when not convertible */
    private String conv(Type a, Type b) {
        if (a.equals(b)) return "";
        if (a.getSort() != Type.OBJECT || b.getSort() != Type.OBJECT) return null;
        if (b.getInternalName().equals(HOLDER)) return WRAP;
        if (a.getInternalName().equals(HOLDER)) return UNWRAP;
        return CONV.get(a.getInternalName() + ">" + b.getInternalName());
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
        }
        for (String l : Files.readAllLines(Path.of(a[0]))) {
            String[] p = l.split("\t", -1);
            if (p.length < 7 || p[2].isEmpty()) continue;
            g.member(p[0], p[1], p[2], p[3], p[4], p[5], p[6]);
        }
        g.constructors();
        g.write(Path.of(a[4]), Path.of(a[5]));
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

    /** per position: 0 same, 1 value -> Holder (wrap), 2 Holder -> value (unwrap); null if not a pure Holder change */
    private int[] holderDiff(Type[] o, Type[] n) {
        if (o.length != n.length) return null;
        int[] r = new int[o.length];
        boolean any = false;
        for (int i = 0; i < o.length; i++) {
            Type a = mapType(o[i]), b = n[i];
            if (a.equals(b)) continue;
            if (b.getSort() == Type.OBJECT && b.getInternalName().equals(HOLDER) && a.getSort() == Type.OBJECT) { r[i] = 1; any = true; }
            else if (a.getSort() == Type.OBJECT && a.getInternalName().equals(HOLDER) && b.getSort() == Type.OBJECT) { r[i] = 2; any = true; }
            else return null;
        }
        return any ? r : new int[0];
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

    void member(String srg, String owner120, String name120, String desc120, String owner121, String name121, String desc121) {
        ClassNode nc = now.node(owner121);
        if (nc == null) { skip("owner gone"); return; }
        if (!linkable(desc120)) { skip("uses a class gone in 1.21.1"); return; }
        boolean field = srg.startsWith("f_");
        if (field) {
            FieldNode fn = nc.fields.stream().filter(f -> f.name.equals(name121) && f.desc.equals(desc121)).findFirst().orElse(null);
            if (fn == null || (fn.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_PUBLIC) == 0) { skip("field not public"); return; }
            int[] d = holderDiff(new Type[]{Type.getType(desc120)}, new Type[]{Type.getType(desc121)});
            if (d == null || d.length == 0 || d[0] != 1) { skip("field: not a holder change"); return; }
            boolean st = (fn.access & Opcodes.ACC_STATIC) != 0;
            Type value = mapType(Type.getType(desc120));
            String h = helperName(owner121, name121);
            String recv = st ? "" : src(Type.getObjectType(owner121)) + " self";
            code.append("    public static ").append(src(value)).append(' ').append(h).append('(').append(recv).append(") {\n")
                .append("        return (").append(src(value)).append(") ").append(st ? src(Type.getObjectType(owner121)) : "self").append('.').append(name121).append(".value();\n    }\n\n");
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
        if ((mn.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_PUBLIC) == 0) { skip("method not public"); return; }
        Type[] oa = Type.getArgumentTypes(desc120), na = Type.getArgumentTypes(desc121);
        int[] args = holderDiff(oa, na);
        int[] ret = holderDiff(new Type[]{Type.getReturnType(desc120)}, new Type[]{Type.getReturnType(desc121)});
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
            call.append(k == 1 ? "(net.minecraft.core.Holder) LegacyHolders.wrap(a" + i + ")" : k == 2 ? "(" + src(na[i]) + ") a" + i + ".value()" : "a" + i);
        }
        String target = (st ? src(Type.getObjectType(owner121)) : "self") + "." + name121 + "(" + call + ")";
        int rk = ret.length == 0 ? 0 : ret[0];
        code.append("    public static ").append(src(r)).append(' ').append(h).append('(').append(sig).append(") {\n        ");
        if (r.getSort() == Type.VOID) code.append(target).append(";\n");
        else if (rk == 1) code.append("return (").append(src(r)).append(") ((net.minecraft.core.Holder) ").append(target).append(").value();\n");
        else if (rk == 2) code.append("return (").append(src(r)).append(") LegacyHolders.wrap(").append(target).append(");\n");
        else code.append("return ").append(target).append(";\n");
        code.append("    }\n\n");
        lines.add("R " + owner120 + " " + srg + " " + desc120 + " " + INTERNAL + " " + h);
        if (itf && !st) skip("(info) interface instance bridges");
    }

    /** constructors whose arguments changed by Holder only */
    void constructors() {
        for (String c : old.classNames()) {
            if (!c.startsWith("net/minecraft/") && !c.startsWith("com/mojang/")) continue;
            ClassNode oc = old.node(c), nc = now.node(mapClass(c));
            if (oc == null || nc == null || (nc.access & Opcodes.ACC_PUBLIC) == 0 || (nc.access & Opcodes.ACC_ABSTRACT) != 0) continue;
            for (MethodNode om : oc.methods) {
                if (!om.name.equals("<init>") || (om.access & Opcodes.ACC_PUBLIC) == 0) continue;
                String mapped = Type.getMethodDescriptor(Type.VOID_TYPE, java.util.Arrays.stream(Type.getArgumentTypes(om.desc)).map(this::mapType).toArray(Type[]::new));
                if (nc.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(mapped))) continue;
                for (MethodNode nm : nc.methods) {
                    if (!nm.name.equals("<init>") || (nm.access & Opcodes.ACC_PUBLIC) == 0) continue;
                    int[] d = holderDiff(Type.getArgumentTypes(om.desc), Type.getArgumentTypes(nm.desc));
                    if (d == null || d.length == 0 || !linkable(om.desc)) continue;
                    Type[] oa = Type.getArgumentTypes(om.desc), na = Type.getArgumentTypes(nm.desc);
                    String owner = src(Type.getObjectType(nc.name));
                    String h = helperName(nc.name, "new");
                    StringBuilder sig = new StringBuilder(), call = new StringBuilder();
                    for (int i = 0; i < oa.length; i++) {
                        if (i > 0) { sig.append(", "); call.append(", "); }
                        sig.append(src(mapType(oa[i]))).append(" a").append(i);
                        call.append(d[i] == 1 ? "(net.minecraft.core.Holder) LegacyHolders.wrap(a" + i + ")" : d[i] == 2 ? "(" + src(na[i]) + ") a" + i + ".value()" : "a" + i);
                    }
                    code.append("    public static ").append(owner).append(' ').append(h).append('(').append(sig).append(") {\n        return new ")
                        .append(owner).append('(').append(call).append(");\n    }\n\n");
                    lines.add("R " + c + " <init> " + om.desc + " " + INTERNAL + " " + h);
                    break;
                }
            }
        }
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
        System.out.println(lines.size() + " lines (" + evolutions + " evolutions); skipped " + skipped);
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
