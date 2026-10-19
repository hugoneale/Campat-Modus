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

    public static boolean isNeoForgeMod(Map<String, byte[]> entries) {
        return entries.containsKey(NEO_TOML) && !entries.containsKey(FORGE_TOML);
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
            } else if (n.startsWith("META-INF/jarjar/") && n.endsWith(".jar")) {
                Map<String, byte[]> nested = read(e.getValue());
                if (isNeoForgeMod(nested)) index(nested, hierarchy);
            }
        }
    }

    public Result convert(String fileName, byte[] jar) throws IOException {
        Map<String, byte[]> in = read(jar);
        if (!isNeoForgeMod(in)) throw new IOException(fileName + " is not a NeoForge mod");
        index(in, hierarchy);

        List<String> warnings = new ArrayList<>();
        Set<String> missing = new TreeSet<>();
        List<Report> nestedReports = new ArrayList<>();

        ModsTomlConverter.Result toml = new ModsTomlConverter().convert(new String(in.get(NEO_TOML), StandardCharsets.UTF_8));
        warnings.addAll(toml.warnings());
        if (!toml.enumExtensions().isEmpty()) {
            warnings.add("uses NeoForge enum extensions (" + String.join(", ", toml.enumExtensions()) + "); these are not supported yet");
        }

        Map<String, byte[]> out = new LinkedHashMap<>();
        List<ClassConverter.Entrypoint> entrypoints = new ArrayList<>();
        Manifest manifest = new Manifest();
        if (in.containsKey("META-INF/MANIFEST.MF")) manifest.read(new ByteArrayInputStream(in.get("META-INF/MANIFEST.MF")));

        for (var e : in.entrySet()) {
            String name = e.getKey();
            byte[] data = e.getValue();
            if (name.equals("META-INF/MANIFEST.MF")) continue;
            if (isSignature(name)) continue;
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
            } else if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                String simple = name.substring(name.lastIndexOf('/') + 1);
                if (DROPPED_NESTED.stream().anyMatch(simple::startsWith)) continue;
                Map<String, byte[]> nested = read(data);
                if (isNeoForgeMod(nested)) {
                    Result r = convert(simple, data);
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
        if (!toml.mixinConfigs().isEmpty()) {
            List<String> all = new ArrayList<>();
            String existing = main.getValue("MixinConfigs");
            if (existing != null) for (String s : existing.split(",")) if (!s.isBlank()) all.add(s.trim());
            for (String c : toml.mixinConfigs()) if (!all.contains(c)) all.add(c);
            main.putValue("MixinConfigs", String.join(",", all));
        }
        // NeoForge reads this for libraries; Forge would load such a jar a second time as a library.
        main.remove(new Attributes.Name("FMLModType"));
        main.putValue("Campat-Converted-From", fileName);
        manifest.getEntries().clear(); // per entry digests are invalid now

        return new Result(write(manifest, out), new Report(fileName, modIds, warnings, missing, nestedReports));
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
