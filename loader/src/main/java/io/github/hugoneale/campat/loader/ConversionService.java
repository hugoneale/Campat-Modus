package io.github.hugoneale.campat.loader;

import io.github.hugoneale.campat.loader.convert.JarConverter;
import io.github.hugoneale.campat.loader.convert.LegacyJarConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Converts NeoForge jars, caching the result keyed by the input jar and the mapping fingerprint so
 * that only changed mods are converted again.
 */
final class ConversionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionService.class);
    /** Bump whenever the converter output changes for the same input. */
    static final int CONVERTER_VERSION = 5;

    record Converted(Path source, Path output, JarConverter.Report report, String error) {}

    private final CampatModLocator.CampatEnvironment env;
    private final Path cacheDir;
    private final List<Converted> results = new ArrayList<>();

    ConversionService(CampatModLocator.CampatEnvironment env, Path cacheDir) {
        this.env = env;
        this.cacheDir = cacheDir;
    }

    List<Converted> convertAll(List<Path> jars) throws IOException {
        Files.createDirectories(cacheDir);
        // Read everything first: every mod's classes go into the hierarchy so that mods extending
        // classes of other NeoForge mods (libraries) resolve correctly.
        Map<Path, byte[]> contents = new LinkedHashMap<>();
        for (Path p : jars) contents.put(p, Files.readAllBytes(p));
        for (byte[] bytes : contents.values()) {
            try {
                JarConverter.index(JarConverter.read(bytes), env.platform());
            } catch (IOException ignored) {
                // reported when converting
            }
        }
        JarConverter converter = new JarConverter(env.mappings(), env.platform(), env.runtimeClasses()::contains);
        LegacyJarConverter legacy = null;
        List<Path> keep = new ArrayList<>();
        for (var e : contents.entrySet()) {
            Path src = e.getKey();
            String key = Hashing.sha256(e.getValue(), env.fingerprint().getBytes(), Integer.toString(CONVERTER_VERSION).getBytes()).substring(0, 20);
            String base = src.getFileName().toString();
            base = base.substring(0, base.length() - 4);
            Path out = cacheDir.resolve(base + "-campat-" + key + ".jar");
            Path report = cacheDir.resolve(base + "-campat-" + key + ".report");
            keep.add(out);
            keep.add(report);
            try {
                JarConverter.Report r;
                if (Files.exists(out) && Files.exists(report)) {
                    r = ReportIO.read(Files.readString(report));
                } else {
                    long t = System.nanoTime();
                    JarConverter.Result res;
                    if (JarConverter.isNeoForgeMod(JarConverter.read(e.getValue()))) {
                        res = converter.convert(src.getFileName().toString(), e.getValue());
                    } else {
                        if (legacy == null) legacy = new LegacyJarConverter(env.legacyMappings().get(), env.platform());
                        res = legacy.convert(src.getFileName().toString(), e.getValue());
                    }
                    Path tmp = Files.createTempFile(cacheDir, base, ".tmp");
                    Files.write(tmp, res.jar());
                    Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    Files.writeString(report, ReportIO.write(res.report()), StandardCharsets.UTF_8);
                    r = res.report();
                    LOGGER.info("Converted {} in {} ms", src.getFileName(), (System.nanoTime() - t) / 1_000_000);
                }
                logReport(r);
                results.add(new Converted(src, out, r, null));
            } catch (Exception ex) {
                LOGGER.error("Failed to convert {}", src, ex);
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                results.add(new Converted(src, null, null, ex.toString()));
            }
        }
        // drop stale cache entries
        try (Stream<Path> s = Files.list(cacheDir)) {
            for (Path p : s.toList()) if (!keep.contains(p)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
        return results;
    }

    private static void logReport(JarConverter.Report r) {
        if (!r.missingNeoClasses().isEmpty()) {
            LOGGER.warn("{} uses {} NeoForge class(es) that Campat Modus does not provide yet; parts of it may not work. See .campat/report.txt",
                r.fileName(), r.missingNeoClasses().size());
        }
        for (String w : r.warnings()) LOGGER.warn("{}: {}", r.fileName(), w);
        for (JarConverter.Report n : r.nested()) logReport(n);
    }

    void writeReport(Path file) {
        StringBuilder sb = new StringBuilder();
        sb.append("Campat Modus conversion report\n");
        sb.append("==============================\n\n");
        for (Converted c : results) {
            if (c.error() != null) {
                sb.append("[FAILED] ").append(c.source().getFileName()).append(": ").append(c.error()).append("\n\n");
                continue;
            }
            appendReport(sb, c.report(), "");
        }
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    private static void appendReport(StringBuilder sb, JarConverter.Report r, String indent) {
        sb.append(indent).append(r.clean() ? "[OK]   " : "[WARN] ").append(r.fileName()).append("  mods=").append(r.modIds()).append('\n');
        for (String w : r.warnings()) sb.append(indent).append("    - ").append(w).append('\n');
        if (!r.missingNeoClasses().isEmpty()) {
            sb.append(indent).append("    missing NeoForge API (").append(r.missingNeoClasses().size()).append("):\n");
            for (String m : r.missingNeoClasses()) sb.append(indent).append("      ").append(m).append('\n');
        }
        for (JarConverter.Report n : r.nested()) appendReport(sb, n, indent + "    ");
        if (indent.isEmpty()) sb.append('\n');
    }
}
