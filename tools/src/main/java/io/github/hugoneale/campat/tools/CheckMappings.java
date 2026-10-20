package io.github.hugoneale.campat.tools;

import io.github.hugoneale.campat.loader.remap.Mappings;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Verifies members.txt against the real NeoForge classes and the built runtime: every redirect
 * target must exist as a static helper with exactly the descriptor the converter will emit.
 *
 * <p>usage: CheckMappings sourceEnv.txt runtime.jar mappingsDir [mappingFile...]</p>
 * <p>Without mapping files: classes.txt + members.txt of the NeoForge layer. With files (legacy layer:
 * srg.txt bridges.txt members.txt) the R/S lines of all but the first are checked.</p>
 */
public final class CheckMappings {
    public static void main(String[] a) throws Exception {
        PlatformEnv neo = PlatformEnv.fromList(Path.of(a[0]));
        Map<String, ClassNode> runtime = new HashMap<>();
        try (ZipFile z = new ZipFile(a[1])) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                try (InputStream in = z.getInputStream(e)) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
                    runtime.put(cn.name, cn);
                }
            }
        }
        Path dir = Path.of(a[2]);
        List<Path> files = new ArrayList<>();
        for (int i = 3; i < a.length; i++) files.add(dir.resolve(a[i]));
        if (files.isEmpty()) files = List.of(dir.resolve("classes.txt"), dir.resolve("members.txt"));
        List<InputStream> streams = new ArrayList<>();
        for (Path f : files) streams.add(Files.newInputStream(f));
        Mappings m = Mappings.load(streams);
        for (InputStream in : streams) in.close();
        m.removeClassRenames(runtime::containsKey);
        List<String> errors = new ArrayList<>();
        int checked = 0;
        List<String> lines = new ArrayList<>();
        for (Path f : files.subList(1, files.size())) lines.addAll(Files.readAllLines(f));
        for (String line : lines) {
            String l = line.contains("#") ? line.substring(0, line.indexOf('#')).strip() : line.strip();
            if (l.isEmpty()) continue;
            String[] p = l.split("\\s+");
            if (p[0].equals("A") || p[0].equals("D") || p[0].equals("V")) {
                checked++;
                List<String[]> refs = new ArrayList<>(); // {owner, name, desc}
                if (p[0].equals("A")) {
                    refs.add(new String[]{p[4], p[5], Type.getMethodDescriptor(Type.getType(Object[].class), Type.getArgumentTypes(p[2]))});
                } else if (p[0].equals("D")) {
                    Type[] args = Type.getArgumentTypes(p[3]);
                    Type[] withSelf = new Type[args.length + 1];
                    withSelf[0] = Type.getObjectType(p[1]);
                    System.arraycopy(args, 0, withSelf, 1, args.length);
                    refs.add(new String[]{p[4], p[5], Type.getMethodDescriptor(Type.getReturnType(p[3]), withSelf)});
                } else {
                    java.util.regex.Matcher mm = java.util.regex.Pattern.compile("([\\w/$]+)\\.([\\w$<>]+)(\\([^)]*\\)(?:\\[*L[^;]+;|\\[*[ZBCSIJFDV]))").matcher(p[6] + " " + p[7]);
                    while (mm.find()) refs.add(new String[]{mm.group(1), mm.group(2), mm.group(3)});
                }
                for (String[] r : refs) {
                    if (!r[0].startsWith("io/github/hugoneale/")) continue;
                    String mapped = mapDesc(r[2], m);
                    ClassNode helper = runtime.get(r[0]);
                    boolean found = helper != null && helper.methods.stream().anyMatch(mn -> mn.name.equals(r[1]) && mn.desc.equals(mapped)
                        && (mn.access & Opcodes.ACC_STATIC) != 0 && (mn.access & Opcodes.ACC_PUBLIC) != 0);
                    if (!found) errors.add("missing public static " + r[0] + "." + r[1] + mapped + "  <- " + l);
                }
                continue;
            }
            if (!p[0].equals("R") && !p[0].equals("S")) continue;
            checked++;
            String owner = p[1], name = p[2], desc = p[3], tOwner = p[4], tName = p[5];
            String explicit = p.length > 6 ? p[6] : null;
            String expected;
            if (p[0].equals("R")) {
                Boolean isStatic = staticMethod(neo, owner, name, desc);
                if (isStatic == null && !name.equals("<init>")) {
                    errors.add("not a source platform method: " + l);
                    continue;
                }
                if (explicit != null) expected = explicit;
                else if (name.equals("<init>")) expected = Type.getMethodDescriptor(Type.getObjectType(owner), Type.getArgumentTypes(desc));
                else if (isStatic) expected = desc;
                else {
                    Type[] args = Type.getArgumentTypes(desc);
                    Type[] withRecv = new Type[args.length + 1];
                    withRecv[0] = Type.getObjectType(declaring(neo, owner, name + desc));
                    System.arraycopy(args, 0, withRecv, 1, args.length);
                    expected = Type.getMethodDescriptor(Type.getReturnType(desc), withRecv);
                }
            } else {
                Boolean isStatic = staticField(neo, owner, name, desc);
                if (isStatic == null) {
                    errors.add("not a source platform field: " + l);
                    continue;
                }
                expected = explicit != null ? explicit : isStatic ? "()" + desc : "(L" + owner + ";)" + desc;
            }
            String mapped = mapDesc(expected, m);
            ClassNode helper = runtime.get(tOwner);
            if (helper == null) {
                errors.add("helper class missing: " + tOwner + "  <- " + l);
                continue;
            }
            boolean found = false;
            for (MethodNode mn : helper.methods) {
                if (mn.name.equals(tName) && mn.desc.equals(mapped) && (mn.access & Opcodes.ACC_STATIC) != 0 && (mn.access & Opcodes.ACC_PUBLIC) != 0) found = true;
            }
            if (!found) {
                StringBuilder near = new StringBuilder();
                for (MethodNode mn : helper.methods) if (mn.name.equals(tName)) near.append("\n        have ").append(mn.desc);
                errors.add("missing public static " + tOwner + "." + tName + mapped + "  <- " + l + near);
            }
        }
        for (String e : errors) System.out.println("ERROR " + e);
        System.out.println(checked + " member mappings checked, " + errors.size() + " problems");
        if (!errors.isEmpty()) System.exit(1);
    }

    private static Boolean staticMethod(PlatformEnv env, String owner, String name, String desc) {
        String decl = env.declaringMethod(owner, name + desc, new HashSet<>());
        if (decl == null) return null;
        ClassNode cn = node(env, decl);
        if (cn == null) return null;
        for (MethodNode mn : cn.methods) if (mn.name.equals(name) && mn.desc.equals(desc)) return (mn.access & Opcodes.ACC_STATIC) != 0;
        return null;
    }

    /** RedirectPass passes the receiver typed as the mapping's owner */
    private static String declaring(PlatformEnv env, String owner, String nd) {
        return owner;
    }

    private static Boolean staticField(PlatformEnv env, String owner, String name, String desc) {
        String decl = env.declaringField(owner, name + ":" + desc, new HashSet<>());
        if (decl == null) return null;
        ClassNode cn = node(env, decl);
        if (cn == null) return null;
        for (FieldNode fn : cn.fields) if (fn.name.equals(name) && fn.desc.equals(desc)) return (fn.access & Opcodes.ACC_STATIC) != 0;
        return null;
    }

    private static final Map<String, ClassNode> NODES = new HashMap<>();

    private static ClassNode node(PlatformEnv env, String name) {
        return NODES.computeIfAbsent(name, n -> {
            try {
                for (Path jar : env.jars) {
                    try (ZipFile z = new ZipFile(jar.toFile())) {
                        var e = z.getEntry(n + ".class");
                        if (e == null) continue;
                        try (InputStream in = z.getInputStream(e)) {
                            ClassNode cn = new ClassNode();
                            new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
                            return cn;
                        }
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    private static final Pattern OBJ = Pattern.compile("L([^;]+);");

    private static String mapDesc(String desc, Mappings m) {
        Matcher mt = OBJ.matcher(desc);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) mt.appendReplacement(sb, Matcher.quoteReplacement("L" + m.mapClass(mt.group(1)) + ";"));
        mt.appendTail(sb);
        return sb.toString();
    }
}
