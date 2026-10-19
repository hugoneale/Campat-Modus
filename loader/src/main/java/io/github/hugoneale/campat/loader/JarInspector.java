package io.github.hugoneale.campat.loader;

import io.github.hugoneale.campat.loader.convert.LegacyTomlConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Cheap checks on jars in the mods folder. */
final class JarInspector {
    private JarInspector() {}

    /**
     * A jar is converted when it carries NeoForge metadata and no Forge metadata. Multi-loader jars
     * that already ship a {@code mods.toml} are left to Forge.
     */
    static boolean isNeoForgeOnly(Path jar) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var mods = z.getEntry("META-INF/mods.toml");
            boolean neoToml = z.getEntry("META-INF/neoforge.mods.toml") != null;
            if (mods == null) return neoToml;
            // NeoForge 20.2 - 20.4 metadata format, or both files with a mods.toml meant for NeoForge
            String text;
            try (var in = z.getInputStream(mods)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean forNeo = io.github.hugoneale.campat.loader.convert.ModsTomlConverter.dependsOnNeoForge(text);
            return neoToml ? forNeo : forNeo && !LegacyTomlConverter.isLegacy(text);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** A Forge / NeoForge 47 mod for Minecraft 1.20.1. */
    static boolean isLegacyForge(Path jar) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var toml = z.getEntry("META-INF/mods.toml");
            if (toml == null || z.getEntry("META-INF/neoforge.mods.toml") != null) return false;
            String text;
            try (var in = z.getInputStream(toml)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (LegacyTomlConverter.isLegacy(text)) return true;
            if (!LegacyTomlConverter.accepts1201(text)) return false;
            var en = z.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                try (var in = z.getInputStream(e)) {
                    if (LegacyTomlConverter.hasSrgNames(in.readAllBytes())) return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
