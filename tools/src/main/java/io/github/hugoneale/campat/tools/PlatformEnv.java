package io.github.hugoneale.campat.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Member level index of a set of jars (first definition of a class wins). */
public final class PlatformEnv {
    public record ClassInfo(String name, String superName, String[] interfaces, Set<String> methods, Set<String> fields, int access) {}

    public final Map<String, ClassInfo> classes = new HashMap<>();
    public final List<Path> jars = new java.util.ArrayList<>();

    public static PlatformEnv fromList(Path list) throws IOException {
        PlatformEnv env = new PlatformEnv();
        for (String l : Files.readAllLines(list)) if (!l.isBlank()) env.load(Path.of(l.strip()));
        return env;
    }

    public static PlatformEnv of(List<Path> jars) throws IOException {
        PlatformEnv env = new PlatformEnv();
        for (Path p : jars) env.load(p);
        return env;
    }

    public void load(Path jar) throws IOException {
        jars.add(jar);
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || n.startsWith("META-INF/") || n.equals("module-info.class")) continue;
                if (classes.containsKey(n.substring(0, n.length() - 6))) continue;
                try (InputStream in = z.getInputStream(e)) {
                    add(in.readAllBytes());
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    public void add(byte[] b) {
        ClassReader cr = new ClassReader(b);
        Set<String> m = new HashSet<>(), f = new HashSet<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) { m.add(n + d); return null; }
            @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) { f.add(n + ":" + d); return null; }
        }, ClassReader.SKIP_CODE);
        classes.putIfAbsent(cr.getClassName(), new ClassInfo(cr.getClassName(), cr.getSuperName(), cr.getInterfaces(), m, f, cr.getAccess()));
    }

    /**
     * Applies the runtime's mixins to the index: interfaces a mixin implements and the members it
     * adds become part of its target classes, as they do at runtime.
     */
    public void applyMixins(Path jar) throws IOException {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
                try (InputStream in = z.getInputStream(e)) {
                    new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
                }
                if (cn.invisibleAnnotations == null) continue;
                for (var an : cn.invisibleAnnotations) {
                    if (!an.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || an.values == null) continue;
                    java.util.List<String> targets = new java.util.ArrayList<>();
                    for (int i = 0; i < an.values.size(); i += 2) {
                        Object v = an.values.get(i + 1);
                        if (v instanceof java.util.List<?> l) for (Object o : l) {
                            if (o instanceof org.objectweb.asm.Type t) targets.add(t.getInternalName());
                            else if (o instanceof String str) targets.add(str.replace('.', '/'));
                        }
                    }
                    for (String t : targets) {
                        ClassInfo ti = get(t);
                        if (ti == null) continue;
                        java.util.List<String> itf = new java.util.ArrayList<>(java.util.List.of(ti.interfaces()));
                        itf.addAll(cn.interfaces);
                        Set<String> m = new HashSet<>(ti.methods()), f = new HashSet<>(ti.fields());
                        for (var mn : cn.methods) if ((mn.access & Opcodes.ACC_PRIVATE) == 0 && !mn.name.startsWith("<")) m.add(mn.name + mn.desc);
                        for (var fn : cn.fields) if ((fn.access & Opcodes.ACC_PRIVATE) == 0) f.add(fn.name + ":" + fn.desc);
                        classes.put(t, new ClassInfo(t, ti.superName(), itf.toArray(new String[0]), m, f, ti.access()));
                    }
                }
            }
        }
    }

    public ClassInfo get(String n) {
        ClassInfo c = classes.get(n);
        if (c == null && (n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("jdk/") || n.startsWith("sun/"))) {
            try (InputStream in = ClassLoader.getSystemResourceAsStream(n + ".class")) {
                if (in != null) { add(in.readAllBytes()); c = classes.get(n); }
            } catch (IOException ignored) {
            }
        }
        return c;
    }

    public boolean hasMethod(String owner, String nameDesc) {
        return declaringMethod(owner, nameDesc, new HashSet<>()) != null;
    }

    public boolean hasField(String owner, String nameDesc) {
        return declaringField(owner, nameDesc, new HashSet<>()) != null;
    }

    public String declaringMethod(String owner, String nd, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        ClassInfo c = get(owner);
        if (c == null) return null;
        if (c.methods.contains(nd)) return owner;
        String r = declaringMethod(c.superName, nd, seen);
        if (r != null) return r;
        if (c.interfaces != null) for (String i : c.interfaces) if ((r = declaringMethod(i, nd, seen)) != null) return r;
        return null;
    }

    public String declaringField(String owner, String nd, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        ClassInfo c = get(owner);
        if (c == null) return null;
        if (c.fields.contains(nd)) return owner;
        String r = declaringField(c.superName, nd, seen);
        if (r != null) return r;
        if (c.interfaces != null) for (String i : c.interfaces) if ((r = declaringField(i, nd, seen)) != null) return r;
        return null;
    }

    /** every non-static method visible in the hierarchy -> declaring class */
    public void collectMethods(String owner, Map<String, String> out, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return;
        ClassInfo c = get(owner);
        if (c == null) return;
        for (String m : c.methods) out.putIfAbsent(m, owner);
        collectMethods(c.superName, out, seen);
        if (c.interfaces != null) for (String i : c.interfaces) collectMethods(i, out, seen);
    }
}
