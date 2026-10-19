package io.github.hugoneale.campat.runtime.bridge;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ModConfig;

/** NeoForge config behaviour that Forge lacks. */
public final class ConfigBridge {
    private ConfigBridge() {}

    /** NeoForge's {@code ModConfig.Type.STARTUP} does not exist on Forge; it is mapped to COMMON. */
    public static ModConfig.Type startupType() {
        return ModConfig.Type.COMMON;
    }

    static void afterRegister(ModContainer container, ModConfig.Type type) {
        // Forge loads COMMON and CLIENT configs right after mod construction, which is what most
        // NeoForge mods expect. STARTUP configs (mapped to COMMON) are read later than on NeoForge.
    }
}
