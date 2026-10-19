package io.github.hugoneale.campat.runtime.mixin;

import io.github.hugoneale.campat.runtime.capability.CapabilityInterop;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge capability queries fall back to NeoForge capability providers. */
@Mixin(value = CapabilityProvider.class, remap = false)
public abstract class CapabilityProviderMixin {
    @Inject(method = "getCapability", at = @At("RETURN"), cancellable = true)
    private <T> void campat$neoFallback(Capability<T> cap, Direction side, CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (!cir.getReturnValue().isPresent()) {
            LazyOptional<T> neo = CapabilityInterop.neo(this, cap, side);
            if (neo.isPresent()) cir.setReturnValue(neo);
        }
    }
}
