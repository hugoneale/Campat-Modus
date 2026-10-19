package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Minecraft 1.21 threads a {@link HolderLookup.Provider} through serialisation that 1.20.1 code never
 * had. This supplies the best one available: the running server's, else the client level's, else
 * the built-in registries (enough for items, blocks, effects, ...).
 */
public final class LegacyRegistries {
    private LegacyRegistries() {}

    private static RegistryAccess builtin;

    public static RegistryAccess access() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RegistryAccess client = ClientRegistries.access();
            if (client != null) return client;
        }
        return builtin();
    }

    public static HolderLookup.Provider provider() {
        return access();
    }

    public static RegistryAccess builtin() {
        if (builtin == null) builtin = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return builtin;
    }
}
