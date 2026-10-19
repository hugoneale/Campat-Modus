package io.github.hugoneale.campat.loader;


import io.github.hugoneale.campat.loader.convert.JarConverter;
import io.github.hugoneale.campat.loader.remap.ClassHierarchy;
import io.github.hugoneale.campat.loader.remap.Mappings;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Forge mod locator (loaded from the SERVICE layer because this jar lives in the mods folder and
 * declares the service) that finds NeoForge mods, converts them into Forge mods and hands them to
 * FML, together with the compatibility runtime mod embedded in this jar.
 */
public class CampatModLocator extends AbstractModProvider implements IModLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CampatModLocator.class);

    @Override
    public String name() {
        return "campat-modus";
    }

    @Override
    public List<ModFileOrException> scanMods() {
        List<ModFileOrException> result = new ArrayList<>();
        if (Boolean.getBoolean("campat.disable")) {
            LOGGER.warn("Campat Modus is disabled by -Dcampat.disable=true");
            return result;
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path modsDir = FMLPaths.MODSDIR.get();
        Path workDir = gameDir.resolve(".campat");
        try {
            CampatEnvironment env = CampatEnvironment.load(workDir);
            Path runtime = env.runtimeJar();
            result.add(createMod(runtime));

            List<Path> candidates = findNeoForgeJars(modsDir);
            if (candidates.isEmpty()) {
                LOGGER.info("Campat Modus: no NeoForge mods found in {}", modsDir);
                return result;
            }
            LOGGER.info("Campat Modus: found {} NeoForge mod jar(s), converting", candidates.size());
            ConversionService service = new ConversionService(env, workDir.resolve("cache"));
            for (ConversionService.Converted c : service.convertAll(candidates)) {
                if (c.error() != null) {
                    result.add(new ModFileOrException(null, new ModFileLoadingException(
                        "Campat Modus could not convert NeoForge mod " + c.source().getFileName() + ": " + c.error())));
                } else {
                    result.add(createMod(c.output()));
                }
            }
            service.writeReport(workDir.resolve("report.txt"));
        } catch (Exception e) {
            LOGGER.error("Campat Modus failed to initialise", e);
            result.add(new ModFileOrException(null, new ModFileLoadingException("Campat Modus failed to initialise: " + e)));
        }
        return result;
    }

    static List<Path> findNeoForgeJars(Path modsDir) throws IOException {
        if (!Files.isDirectory(modsDir)) return List.of();
        List<Path> excluded = ModDirTransformerDiscoverer.allExcluded();
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(modsDir)) {
            for (Path p : s.sorted().toList()) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".jar") || !Files.isRegularFile(p) || excluded.contains(p)) continue;
                if (JarInspector.isNeoForgeOnly(p)) out.add(p);
            }
        }
        return out;
    }

    /** Mappings, hierarchy and the extracted runtime jar. */
    record CampatEnvironment(Mappings mappings, ClassHierarchy platform, Path runtimeJar, java.util.Set<String> runtimeClasses, String fingerprint) {
        static CampatEnvironment load(Path workDir) throws IOException {
            Files.createDirectories(workDir);
            String version = CampatModLocator.class.getPackage().getImplementationVersion();
            if (version == null) version = "dev";
            byte[] runtimeBytes;
            try (InputStream in = resource("META-INF/campat/campat-runtime.jar")) {
                runtimeBytes = in.readAllBytes();
            }
            String runtimeHash = Hashing.sha256(runtimeBytes).substring(0, 16);
            Path runtime = workDir.resolve("campat-runtime-" + version + "-" + runtimeHash + ".jar");
            if (!Files.exists(runtime)) {
                Path tmp = Files.createTempFile(workDir, "runtime", ".tmp");
                Files.write(tmp, runtimeBytes);
                Files.move(tmp, runtime, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            java.util.Set<String> runtimeClasses = new java.util.HashSet<>();
            for (Map.Entry<String, byte[]> e : JarConverter.read(runtimeBytes).entrySet()) {
                String n = e.getKey();
                if (n.endsWith(".class")) runtimeClasses.add(n.substring(0, n.length() - 6));
            }
            byte[] classes, members, hierarchy;
            try (InputStream a = resource("META-INF/campat/mappings/classes.txt");
                 InputStream b = resource("META-INF/campat/mappings/members.txt");
                 InputStream c = resource("META-INF/campat/hierarchy.txt")) {
                classes = a.readAllBytes();
                members = b.readAllBytes();
                hierarchy = c.readAllBytes();
            }
            Mappings mappings = Mappings.load(List.of(new java.io.ByteArrayInputStream(classes), new java.io.ByteArrayInputStream(members)));
            // Classes the runtime ships under their NeoForge name are used as is.
            mappings.removeClassRenames(runtimeClasses::contains);
            ClassHierarchy platform = ClassHierarchy.load(new java.io.ByteArrayInputStream(hierarchy));
            String fingerprint = Hashing.sha256(classes, members, hierarchy, runtimeHash.getBytes()).substring(0, 16);
            return new CampatEnvironment(mappings, platform, runtime, runtimeClasses, fingerprint);
        }

        private static InputStream resource(String path) throws IOException {
            InputStream in = CampatModLocator.class.getClassLoader().getResourceAsStream(path);
            if (in == null) in = CampatModLocator.class.getModule().getResourceAsStream(path);
            if (in == null) throw new IOException("Missing bundled resource " + path);
            return in;
        }
    }
}
