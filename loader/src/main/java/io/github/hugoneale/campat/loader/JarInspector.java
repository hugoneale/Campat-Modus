package io.github.hugoneale.campat.loader;

import java.io.IOException;
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
            return z.getEntry("META-INF/neoforge.mods.toml") != null && z.getEntry("META-INF/mods.toml") == null;
        } catch (IOException e) {
            return false;
        }
    }
}
