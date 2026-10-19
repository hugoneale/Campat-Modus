package io.github.hugoneale.campat.loader;

import cpw.mods.modlauncher.api.NamedPath;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

/**
 * Keeps Forge's own mods folder locator away from the jars this locator converts: 1.20.1 mods and
 * old style NeoForge mods carry a {@code mods.toml} Forge would load unconverted, and Forge's secure
 * jar handling rejects some NeoForge jars outright (strict module-info checks). Forge skips jars listed by
 * {@link ModDirTransformerDiscoverer#allExcluded()}, which is backed by a static mutable list
 * filled before any locator scans; the converted copies are handed to FML by this locator instead.
 *
 * <p>The list is private and fmlloader's package is not opened to us, so the field is read
 * through {@code sun.misc.Unsafe}.</p>
 */
final class ScanExclusion {
    private ScanExclusion() {}

    @SuppressWarnings({"unchecked", "removal", "deprecation"})
    static void exclude(List<Path> jars) throws ReflectiveOperationException {
        Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        Field found = ModDirTransformerDiscoverer.class.getDeclaredField("found");
        List<NamedPath> list = (List<NamedPath>) unsafe.getObject(unsafe.staticFieldBase(found), unsafe.staticFieldOffset(found));
        for (Path p : jars) {
            if (!excluded(List.of(p))) list.add(new NamedPath("campat:" + p.getFileName(), p));
        }
    }

    static boolean excluded(List<Path> jars) {
        return ModDirTransformerDiscoverer.allExcluded().containsAll(jars);
    }
}
