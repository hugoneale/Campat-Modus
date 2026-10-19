package io.github.hugoneale.campat.loader.convert;

import io.github.hugoneale.campat.loader.remap.ClassConverter;
import io.github.hugoneale.campat.loader.remap.ClassHierarchy;
import io.github.hugoneale.campat.loader.remap.Mappings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Converts a NeoForge mod jar into a Forge mod jar.
 */
public final class JarConverter {
    public static final String NEO_TOML = "META-INF/neoforge.mods.toml";
    public static final String FORGE_TOML = "META-INF/mods.toml";

    /** Nested libraries that NeoForge mods bundle but that the compatibility layer provides itself. */
    private static final List<String> DROPPED_NESTED = List.of("mixinextras-neoforge", "mixinextras-common");

    private static boolean dropNested(String simpleName) {
        String n = simpleName.toLowerCase(java.util.Locale.ROOT);
        return DROPPED_NESTED.stream().anyMatch(n::startsWith) || (n.contains("mixinextras") && !n.contains("forge-") && n.contains("neoforge"));
    }

    /** Mods that only exist to give NeoForge what Forge has natively: replaced by an empty mod of the same id. */
    public static final java.util.Set<String> NATIVE_ON_FORGE = java.util.Set.of("forgeconfigapiport");

    public record Report(String fileName, List<String> modIds, List<String> warnings, Set<String> missingNeoClasses,
                         List<Report> nested) {
        public boolean clean() {
            return warnings.isEmpty() && missingNeoClasses.isEmpty() && nested.stream().allMatch(Report::clean);
        }
    }

    public record Result(byte[] jar, Report report) {}

    private final Mappings mappings;
    private final ClassHierarchy hierarchy;
    private final Predicate<String> providedByRuntime;
    private final ClassConverter classConverter;
    private final ResourceRewriter resources;

    public JarConverter(Mappings mappings, ClassHierarchy hierarchy, Predicate<String> providedByRuntime) {
        this.mappings = mappings;
        this.hierarchy = hierarchy;
        this.providedByRuntime = providedByRuntime;
        this.classConverter = new ClassConverter(mappings, hierarchy);
        this.resources = new ResourceRewriter(classConverter.remapper());
    }

    private static final byte[] NEO_MOD = "Lnet/neoforged/fml/common/Mod;".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] FORGE_MOD = "Lnet/minecraftforge/fml/common/Mod;".getBytes(StandardCharsets.ISO_8859_1);

    /** a NeoForge @Mod class and no Forge one */
    public static boolean usesNeoModAnnotation(java.util.Iterator<byte[]> classFiles) {
        boolean neo = false;
        while (classFiles.hasNext()) {
            byte[] c = classFiles.next();
            if (contains(c, FORGE_MOD)) return false;
            if (!neo && contains(c, NEO_MOD)) neo = true;
        }
        return neo;
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    /** jar-in-jar entries: metadata.json may point anywhere, in practice somewhere under META-INF/ */
    public static boolean isNestedJar(String name) {
        return name.startsWith("META-INF/") && name.endsWith(".jar");
    }

    public static boolean isNeoForgeMod(Map<String, byte[]> entries) {
        byte[] toml = entries.get(FORGE_TOML);
        // jars shipping both files: the mods.toml decides whether it is meant for Forge; when it names
        // neither loader, the @Mod annotation in the classes does
        if (entries.containsKey(NEO_TOML)) {
            if (toml == null) return true;
            String t = new String(toml, StandardCharsets.UTF_8);
            if (ModsTomlConverter.dependsOnNeoForge(t)) return true;
            if (ModsTomlConverter.dependsOnForge(t)) return false;
            return usesNeoModAnnotation(entries.entrySet().stream()
                .filter(e -> e.getKey().endsWith(".class")).map(Map.Entry::getValue).iterator());
        }
        // NeoForge 20.2 - 20.4 era metadata: a mods.toml that depends on "neoforge" (and is not a 1.20.1 mod)
        return toml != null && ModsTomlConverter.dependsOnNeoForge(new String(toml, StandardCharsets.UTF_8))
            && !LegacyTomlConverter.isLegacy(new String(toml, StandardCharsets.UTF_8));
    }

    private static String neoToml(Map<String, byte[]> entries) {
        byte[] t = entries.containsKey(NEO_TOML) ? entries.get(NEO_TOML) : entries.get(FORGE_TOML);
        return new String(t, StandardCharsets.UTF_8);
    }

    public static Map<String, byte[]> read(byte[] jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                entries.putIfAbsent(e.getName(), zin.readAllBytes());
            }
        }
        return entries;
    }

    /** Adds every class of the jar (and nested NeoForge jars) to the hierarchy. */
    public static void index(Map<String, byte[]> entries, ClassHierarchy hierarchy) throws IOException {
        for (var e : entries.entrySet()) {
            String n = e.getKey();
            if (n.endsWith(".class") && !n.startsWith("META-INF/")) {
                try {
                    ClassConverter.index(e.getValue(), hierarchy);
                } catch (RuntimeException ignored) {
                    // broken or non standard class files are copied as is later
                }
            } else if (isNestedJar(n)) {
                Map<String, byte[]> nested = read(e.getValue());
                if (isNeoForgeMod(nested)) index(nested, hierarchy);
            }
        }
    }

    public Result convert(String fileName, byte[] jar) throws IOException {
        return convert(fileName, jar, List.of());
    }

    /** @param inheritedMixins mixin configs declared by the parent jar's metadata that live in this jar */
    private Result convert(String fileName, byte[] jar, List<String> inheritedMixins) throws IOException {
        Map<String, byte[]> in = read(jar);
        if (!isNeoForgeMod(in)) throw new IOException(fileName + " is not a NeoForge mod");
        index(in, hierarchy);

        List<String> warnings = new ArrayList<>();
        Set<String> missing = new TreeSet<>();
        List<Report> nestedReports = new ArrayList<>();

        ModsTomlConverter.Result toml = new ModsTomlConverter().convert(neoToml(in));
        if (toml.mods().stream().anyMatch(m -> NATIVE_ON_FORGE.contains(m.modId()))) return nativeStub(fileName, toml);
        warnings.addAll(toml.warnings());
        if (!toml.enumExtensions().isEmpty()) {
            warnings.add("uses NeoForge enum extensions (" + String.join(", ", toml.enumExtensions()) + "); these are not supported yet");
        }

        Map<String, byte[]> out = new LinkedHashMap<>();
        List<ClassConverter.Entrypoint> entrypoints = new ArrayList<>();
        // accumulated across jars (keys are class qualified): nested jars are converted in the middle of their parent
        for (var e : in.entrySet()) {
            if (e.getKey().endsWith(".class") && !e.getKey().startsWith("META-INF/")) {
                try {
                    classConverter.mixinRetypes().scan(e.getValue());
                } catch (RuntimeException ignored) {
                    // reported when converting
                }
            }
        }
        Manifest manifest = new Manifest();
        if (in.containsKey("META-INF/MANIFEST.MF")) manifest.read(new ByteArrayInputStream(in.get("META-INF/MANIFEST.MF")));

        for (var e : in.entrySet()) {
            String name = e.getKey();
            byte[] data = e.getValue();
            if (name.equals("META-INF/MANIFEST.MF") || name.equals(NEO_TOML)) continue;
            if (isSignature(name)) continue;
            // Forge's secure jar loader validates module descriptors strictly; NeoForge mods are
            // loaded as automatic modules anyway
            if (name.equals("module-info.class") || (name.startsWith("META-INF/versions/") && name.endsWith("/module-info.class"))) continue;
            if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                try {
                    ClassConverter.Output o = classConverter.convert(data);
                    entrypoints.addAll(o.entrypoints());
                    for (String p : o.problems()) warnings.add(p);
                    for (String ref : o.referencedNeoClasses()) {
                        if (mappings.mapClass(ref).equals(ref) && !providedByRuntime.test(ref)) missing.add(ref);
                    }
                    out.put(name, o.bytes());
                } catch (RuntimeException ex) {
                    warnings.add("failed to convert " + name + ": " + ex);
                    out.put(name, data);
                }
            } else if (isNestedJar(name)) {
                String simple = name.substring(name.lastIndexOf('/') + 1);
                if (dropNested(simple)) continue;
                Map<String, byte[]> nested = read(data);
                if (isNeoForgeMod(nested)) {
                    List<String> inherited = new ArrayList<>();
                    for (String c : toml.mixinConfigs()) if (!in.containsKey(c) && nested.containsKey(c)) inherited.add(c);
                    Result r = convert(simple, data, inherited);
                    nestedReports.add(r.report());
                    out.put(name, r.jar());
                } else {
                    out.put(name, data);
                }
            } else if (name.equals("META-INF/jarjar/metadata.json")) {
                out.put(name, resources.filterJarJarMetadata(data, DROPPED_NESTED));
            } else {
                ResourceRewriter.Rewritten r = resources.rewrite(name, data, toml.mixinConfigs());
                if (r != null) out.put(r.name(), r.data());
            }
        }

        // entrypoints
        EntrypointGenerator gen = new EntrypointGenerator();
        List<String> modIds = new ArrayList<>();
        for (ModsTomlConverter.ModEntry mod : toml.mods()) {
            modIds.add(mod.modId());
            List<ClassConverter.Entrypoint> mine = entrypoints.stream().filter(ep -> mod.modId().equals(ep.modId())).toList();
            EntrypointGenerator.Generated g = gen.generate(mod.modId(), mine);
            out.put(g.internalName() + ".class", g.bytes());
        }
        for (ClassConverter.Entrypoint ep : entrypoints) {
            if (!modIds.contains(ep.modId())) warnings.add("@Mod class " + ep.className() + " references unknown mod id " + ep.modId());
        }
        out.put(FORGE_TOML, toml.modsToml().getBytes(StandardCharsets.UTF_8));
        // NeoForge synthesises pack metadata for mod jars; Forge requires a pack.mcmeta and shows a
        // loading warning (and skips the mod's resources) without one.
        if (!out.containsKey("pack.mcmeta")) {
            String desc = (modIds.isEmpty() ? fileName : String.join(", ", modIds)) + " resources";
            out.put("pack.mcmeta", ("{\"pack\":{\"description\":\"" + desc.replace("\"", "'")
                + "\",\"pack_format\":34,\"supported_formats\":[34,48]}}").getBytes(StandardCharsets.UTF_8));
        }

        Attributes main = manifest.getMainAttributes();
        if (main.getValue(Attributes.Name.MANIFEST_VERSION) == null) main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        // configs must be read from the jar that holds them (NeoForge finds them anywhere in the mod)
        List<String> mixinConfigs = new ArrayList<>(inheritedMixins);
        for (String c : toml.mixinConfigs()) if (in.containsKey(c) && !mixinConfigs.contains(c)) mixinConfigs.add(c);
        if (!mixinConfigs.isEmpty()) {
            List<String> all = new ArrayList<>();
            String existing = main.getValue("MixinConfigs");
            if (existing != null) for (String s : existing.split(",")) if (!s.isBlank()) all.add(s.trim());
            for (String c : mixinConfigs) if (!all.contains(c)) all.add(c);
            main.putValue("MixinConfigs", String.join(",", all));
        }
        // NeoForge reads this for libraries; Forge would load such a jar a second time as a library.
        main.remove(new Attributes.Name("FMLModType"));
        main.putValue("Campat-Converted-From", fileName);
        manifest.getEntries().clear(); // per entry digests are invalid now

        return new Result(write(manifest, out), new Report(fileName, modIds, warnings, missing, nestedReports));
    }

    /** An empty Forge mod carrying the ids of a mod whose purpose Forge fulfils natively. */
    private Result nativeStub(String fileName, ModsTomlConverter.Result toml) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        EntrypointGenerator gen = new EntrypointGenerator();
        List<String> ids = new ArrayList<>();
        for (ModsTomlConverter.ModEntry mod : toml.mods()) {
            ids.add(mod.modId());
            EntrypointGenerator.Generated g = gen.generate(mod.modId(), List.of());
            out.put(g.internalName() + ".class", g.bytes());
        }
        out.put(FORGE_TOML, toml.modsToml().getBytes(StandardCharsets.UTF_8));
        out.put("pack.mcmeta", "{\"pack\":{\"description\":\"stub\",\"pack_format\":34}}".getBytes(StandardCharsets.UTF_8));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Campat-Converted-From", fileName);
        return new Result(write(manifest, out), new Report(fileName, ids, List.of("provided natively by Forge; the NeoForge port was replaced by an empty mod"), new java.util.TreeSet<>(), List.of()));
    }

    static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', 9) >= 0) return false;
        String u = name.toUpperCase();
        return u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC");
    }

    static byte[] write(Manifest manifest, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(bos, manifest)) {
            Set<String> dirs = new TreeSet<>();
            for (String name : entries.keySet()) {
                for (int i = name.indexOf('/'); i >= 0; i = name.indexOf('/', i + 1)) dirs.add(name.substring(0, i + 1));
            }
            dirs.remove("META-INF/");
            for (String d : dirs) {
                JarEntry je = new JarEntry(d);
                je.setTime(0);
                jos.putNextEntry(je);
                jos.closeEntry();
            }
            for (var e : entries.entrySet()) {
                JarEntry je = new JarEntry(e.getKey());
                je.setTime(0);
                jos.putNextEntry(je);
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
