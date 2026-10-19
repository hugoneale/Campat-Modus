package io.github.hugoneale.campat.tools;

import io.github.hugoneale.campat.loader.convert.JarConverter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Static link check of converted mods against Forge + the Campat runtime: every class, field and
 * method a converted mod references on the platform must exist, and every platform method a mod
 * overrode on NeoForge must still be overridden after conversion.
 *
 * <p>usage: Audit forge_env.txt runtime.jar originalModsDir convertedModsDir outDir</p>
 */
public final class Audit {
    private static boolean platform(String n) {
        return n.startsWith("net/minecraft/") || n.startsWith("com/mojang/") || n.startsWith("net/neoforged/")
            || n.startsWith("net/minecraftforge/") || n.startsWith("io/github/hugoneale/campat/runtime/");
    }

    private final PlatformEnv forge;
    private final PlatformEnv neo;
    final Map<String, Set<String>> missing = new TreeMap<>(); // ref -> mods
    final Map<String, Set<String>> lost = new TreeMap<>();
    final Map<String, Set<String>> perMod = new TreeMap<>();

    Audit(PlatformEnv forge, PlatformEnv neo) {
        this.forge = forge;
        this.neo = neo;
    }

    public static void main(String[] a) throws Exception {
        PlatformEnv forge = PlatformEnv.fromList(Path.of(a[0]));
        forge.load(Path.of(a[1]));
        forge.applyMixins(Path.of(a[1]));
        PlatformEnv neo = a.length > 5 ? PlatformEnv.fromList(Path.of(a[5])) : null;
        Path converted = Path.of(a[3]);
        Path out = Path.of(a[4]);
        Files.createDirectories(out);
        Audit audit = new Audit(forge, neo);
        List<Path> jars;
        try (Stream<Path> s = Files.list(converted)) {
            jars = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
        for (Path j : jars) audit.auditJar(j.getFileName().toString(), JarConverter.read(Files.readAllBytes(j)),
            a[2].equals("-") ? null : originals(Path.of(a[2]).resolve(j.getFileName())));
        audit.write(out, jars.size());
    }

    private static Map<String, byte[]> originals(Path p) throws IOException {
        return Files.exists(p) ? JarConverter.read(Files.readAllBytes(p)) : null;
    }

    void auditJar(String mod, Map<String, byte[]> entries, Map<String, byte[]> original) throws IOException {
        if (!entries.containsKey("META-INF/mods.toml") || !new String(entries.getOrDefault("META-INF/MANIFEST.MF", new byte[0])).contains("Campat-Converted-From")) {
            return;
        }
        Set<String> problems = new TreeSet<>();
        perMod.put(mod, problems);
        Map<String, ClassNode> own = new HashMap<>();
        for (var e : entries.entrySet()) {
            if (e.getKey().endsWith(".class") && !e.getKey().startsWith("META-INF/")) {
                ClassNode cn = new ClassNode();
                try { new ClassReader(e.getValue()).accept(cn, 0); } catch (RuntimeException ex) { continue; }
                own.put(cn.name, cn);
            } else if (e.getKey().startsWith("META-INF/jarjar/") && e.getKey().endsWith(".jar")) {
                Map<String, byte[]> nested = JarConverter.read(e.getValue());
                Map<String, byte[]> origNested = null;
                if (original != null && original.containsKey(e.getKey())) origNested = JarConverter.read(original.get(e.getKey()));
                auditJar(mod + "!" + e.getKey().substring(16), nested, origNested);
            }
        }
        PlatformEnv local = new PlatformEnv();
        for (var e : entries.entrySet()) if (e.getKey().endsWith(".class") && !e.getKey().startsWith("META-INF/")) {
            try { local.add(e.getValue()); } catch (RuntimeException ignored) {}
        }
        for (ClassNode cn : own.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode mi) checkMethod(mod, mi.owner, mi.name + mi.desc, own, local, problems);
                    else if (insn instanceof FieldInsnNode fi) checkField(mod, fi.owner, fi.name + ":" + fi.desc, own, local, problems);
                    else if (insn instanceof TypeInsnNode ti) checkType(mod, elem(ti.desc), problems);
                    else if (insn instanceof LdcInsnNode l && l.cst instanceof Type t && t.getSort() != Type.METHOD) checkType(mod, elem(t.getDescriptor()), problems);
                    else if (insn instanceof InvokeDynamicInsnNode id) {
                        for (Object o : id.bsmArgs) if (o instanceof Handle h) {
                            if (h.getTag() <= Opcodes.H_PUTSTATIC) checkField(mod, h.getOwner(), h.getName() + ":" + h.getDesc(), own, local, problems);
                            else checkMethod(mod, h.getOwner(), h.getName() + h.getDesc(), own, local, problems);
                        }
                    }
                }
            }
            checkType(mod, cn.superName, problems);
            for (String i : cn.interfaces) checkType(mod, i, problems);
        }
        if (original != null && neo != null) checkOverrides(mod, original, own, local, problems);
    }

    /** methods that overrode a NeoForge platform method must still override a Forge/runtime one */
    private void checkOverrides(String mod, Map<String, byte[]> original, Map<String, ClassNode> converted, PlatformEnv local, Set<String> problems) {
        PlatformEnv origLocal = new PlatformEnv();
        Map<String, ClassNode> orig = new HashMap<>();
        for (var e : original.entrySet()) if (e.getKey().endsWith(".class") && !e.getKey().startsWith("META-INF/")) {
            try {
                origLocal.add(e.getValue());
                ClassNode cn = new ClassNode();
                new ClassReader(e.getValue()).accept(cn, ClassReader.SKIP_CODE);
                orig.put(cn.name, cn);
            } catch (RuntimeException ignored) {}
        }
        for (ClassNode ocn : orig.values()) {
            ClassNode ccn = converted.get(ocn.name);
            if (ccn == null || ccn.methods.size() != ocn.methods.size()) continue;
            Map<String, String> neoMethods = new HashMap<>();
            collectPlatform(ocn, origLocal, neo, neoMethods);
            Map<String, String> forgeMethods = new HashMap<>();
            collectPlatform(ccn, local, forge, forgeMethods);
            for (int i = 0; i < ocn.methods.size(); i++) {
                MethodNode om = ocn.methods.get(i), cm = ccn.methods.get(i);
                if ((om.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0 || om.name.startsWith("<")) continue;
                String decl = neoMethods.get(om.name + om.desc);
                if (decl == null || decl.startsWith("java/")) continue;
                if (!forgeMethods.containsKey(cm.name + cm.desc)) {
                    String k = "override " + decl + "." + om.name + om.desc;
                    lost.computeIfAbsent(k, x -> new TreeSet<>()).add(mod);
                    problems.add(k);
                }
            }
        }
    }

    private static void collectPlatform(ClassNode cn, PlatformEnv local, PlatformEnv platform, Map<String, String> out) {
        List<String> parents = new ArrayList<>();
        parents.add(cn.superName);
        parents.addAll(cn.interfaces);
        Set<String> seen = new HashSet<>();
        while (!parents.isEmpty()) {
            String p = parents.remove(0);
            if (p == null || !seen.add(p)) continue;
            PlatformEnv.ClassInfo li = local.classes.get(p);
            if (li != null) {
                parents.add(li.superName());
                if (li.interfaces() != null) parents.addAll(List.of(li.interfaces()));
                continue;
            }
            if (platform(p)) platform.collectMethods(p, out, new HashSet<>());
        }
    }

    private static String elem(String desc) {
        if (desc == null) return null;
        if (!desc.startsWith("[")) return desc;
        Type t = Type.getType(desc);
        while (t.getSort() == Type.ARRAY) t = t.getElementType();
        return t.getSort() == Type.OBJECT ? t.getInternalName() : null;
    }

    private void checkType(String mod, String t, Set<String> problems) {
        if (t == null || !platform(t)) return;
        if (forge.get(t) == null) add("class " + t, mod, problems);
    }

    private String ownerForLookup(String owner, Map<String, ClassNode> own, PlatformEnv local) {
        // walk the mod's own classes up to the first platform type
        Set<String> seen = new HashSet<>();
        String c = owner;
        while (c != null && own.containsKey(c) && seen.add(c)) c = own.get(c).superName;
        return c;
    }

    private void checkMethod(String mod, String owner, String nd, Map<String, ClassNode> own, PlatformEnv local, Set<String> problems) {
        if (owner.startsWith("[")) return;
        if (own.containsKey(owner)) {
            // declared locally or inherited from the platform; only check what the mod does not declare
            if (local.declaringMethod(owner, nd, new HashSet<>()) != null) return;
            String p = ownerForLookup(owner, own, local);
            if (p == null || !platform(p)) return;
            if (!forge.hasMethod(p, nd) && !interfacesHave(owner, nd, own)) add("method " + p + "." + nd + " (via " + owner + ")", mod, problems);
            return;
        }
        if (!platform(owner)) return;
        if (forge.get(owner) == null) { add("class " + owner, mod, problems); return; }
        if (!forge.hasMethod(owner, nd)) add("method " + owner + "." + nd, mod, problems);
    }

    private boolean interfacesHave(String owner, String nd, Map<String, ClassNode> own) {
        Set<String> seen = new HashSet<>();
        List<String> q = new ArrayList<>(List.of(owner));
        while (!q.isEmpty()) {
            String c = q.remove(0);
            if (c == null || !seen.add(c)) continue;
            ClassNode cn = own.get(c);
            if (cn != null) { q.add(cn.superName); q.addAll(cn.interfaces); continue; }
            if (forge.hasMethod(c, nd)) return true;
        }
        return false;
    }

    private void checkField(String mod, String owner, String nd, Map<String, ClassNode> own, PlatformEnv local, Set<String> problems) {
        if (own.containsKey(owner)) {
            if (local.declaringField(owner, nd, new HashSet<>()) != null) return;
            String p = ownerForLookup(owner, own, local);
            if (p == null || !platform(p)) return;
            if (!forge.hasField(p, nd)) add("field " + p + "." + nd + " (via " + owner + ")", mod, problems);
            return;
        }
        if (!platform(owner)) return;
        if (forge.get(owner) == null) { add("class " + owner, mod, problems); return; }
        if (!forge.hasField(owner, nd)) add("field " + owner + "." + nd, mod, problems);
    }

    private void add(String k, String mod, Set<String> problems) {
        missing.computeIfAbsent(k, x -> new TreeSet<>()).add(mod);
        problems.add(k);
    }

    void write(Path out, int jars) throws IOException {
        int clean = 0;
        for (Set<String> p : perMod.values()) if (p.isEmpty()) clean++;
        try (Writer w = Files.newBufferedWriter(out.resolve("audit-summary.txt"))) {
            w.write("mod jars audited: " + perMod.size() + "\n");
            w.write("link-clean (no unresolved platform references, no lost overrides): " + clean + "\n");
            w.write("distinct unresolved references: " + missing.size() + ", distinct lost overrides: " + lost.size() + "\n\n");
            w.write("== per mod ==\n");
            for (var e : perMod.entrySet()) w.write(String.format("%4d  %s%n", e.getValue().size(), e.getKey()));
        }
        dump(out.resolve("audit-missing.txt"), missing);
        dump(out.resolve("audit-lost-overrides.txt"), lost);
        try (Writer w = Files.newBufferedWriter(out.resolve("audit-per-mod.txt"))) {
            for (var e : perMod.entrySet()) {
                w.write(e.getKey() + "\n");
                for (String p : e.getValue()) w.write("    " + p + "\n");
            }
        }
        System.out.println("audited " + perMod.size() + " mod jars, " + clean + " link-clean, " + missing.size() + " unresolved refs, " + lost.size() + " lost overrides");
    }

    private static void dump(Path p, Map<String, Set<String>> m) throws IOException {
        try (Writer w = Files.newBufferedWriter(p)) {
            List<Map.Entry<String, Set<String>>> l = new ArrayList<>(m.entrySet());
            l.sort((x, y) -> y.getValue().size() - x.getValue().size());
            for (var e : l) w.write(e.getValue().size() + "\t" + e.getKey() + "\t" + String.join(",", e.getValue()) + "\n");
        }
    }
}
