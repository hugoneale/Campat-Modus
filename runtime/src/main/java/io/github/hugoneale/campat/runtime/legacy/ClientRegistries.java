package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

/** Client side half of {@link LegacyRegistries}; only loaded on the client. */
final class ClientRegistries {
    private ClientRegistries() {}

    static RegistryAccess access() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        if (mc.level != null) return mc.level.registryAccess();
        if (mc.getConnection() != null) return mc.getConnection().registryAccess();
        return null;
    }
}
