package io.github.hugoneale.campat.runtime.capability;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

/** Methods NeoForge patches into {@code ServerLevel} for capability invalidation; mixed in. */
public interface ServerLevelCapabilities {
    void registerCapabilityListener(BlockPos pos, ICapabilityInvalidationListener listener);

    void cleanCapabilityListenerReferences();

    static ServerLevelCapabilities of(Object level) {
        return (ServerLevelCapabilities) level;
    }
}
