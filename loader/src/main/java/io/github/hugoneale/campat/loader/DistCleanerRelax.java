package io.github.hugoneale.campat.loader;

import java.lang.reflect.Field;
import java.util.EnumSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21 runs dedicated servers from the same joined Minecraft jar as Forge but no longer
 * enforces {@code @OnlyIn}: client classes load on the server (and mods rely on that, from
 * {@code Create}'s static initialiser to Mixin metadata lookups). Forge's RuntimeDistCleaner instead
 * throws when one is loaded. With NeoForge mods installed on a dedicated server the cleaner is
 * switched off the way NeoForge has it, by emptying the phase set its {@code handlesClass}
 * returns. {@code -Dcampat.keepDistCleaner=true} keeps Forge's behaviour.
 */
final class DistCleanerRelax {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistCleanerRelax.class);

    private DistCleanerRelax() {}

    @SuppressWarnings({"removal", "deprecation"})
    static void apply() {
        if (Boolean.getBoolean("campat.keepDistCleaner") || FMLEnvironment.dist != Dist.DEDICATED_SERVER) return;
        try {
            Class<?> cleaner = Class.forName("net.minecraftforge.fml.loading.RuntimeDistCleaner");
            Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
            Field yay = cleaner.getDeclaredField("YAY");
            Object set = unsafe.getObject(unsafe.staticFieldBase(yay), unsafe.staticFieldOffset(yay));
            ((EnumSet<?>) set).clear();
            LOGGER.info("Campat Modus: client classes may load on this dedicated server, as on NeoForge (@OnlyIn is not enforced)");
        } catch (Throwable t) {
            LOGGER.warn("Campat Modus: could not relax Forge's RuntimeDistCleaner; NeoForge mods touching client classes on a server may fail", t);
        }
    }
}
