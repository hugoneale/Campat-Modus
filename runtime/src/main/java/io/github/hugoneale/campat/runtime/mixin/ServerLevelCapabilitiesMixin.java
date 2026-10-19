package io.github.hugoneale.campat.runtime.mixin;

import io.github.hugoneale.campat.runtime.capability.ServerLevelCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import net.neoforged.neoforge.common.extensions.ILevelExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** NeoForge's capability invalidation listeners on server levels. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelCapabilitiesMixin implements ServerLevelCapabilities, ILevelExtension {
    @Unique
    private final CapabilityListenerHolder campat$capListeners = new CapabilityListenerHolder();

    @Override
    public void invalidateCapabilities(BlockPos pos) {
        campat$capListeners.invalidatePos(pos);
    }

    @Override
    public void invalidateCapabilities(ChunkPos pos) {
        campat$capListeners.invalidateChunk(pos);
    }

    @Override
    public void registerCapabilityListener(BlockPos pos, ICapabilityInvalidationListener listener) {
        campat$capListeners.addListener(pos, listener);
    }

    @Override
    public void cleanCapabilityListenerReferences() {
        campat$capListeners.clean();
    }
}
