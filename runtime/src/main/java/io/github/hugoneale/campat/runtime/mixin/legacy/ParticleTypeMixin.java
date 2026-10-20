package io.github.hugoneale.campat.runtime.mixin.legacy;

import io.github.hugoneale.campat.runtime.legacy.LegacyParticles;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Associates a 1.20.1 particle deserializer (passed to the old constructor) with the new type. */
@Mixin(ParticleType.class)
public abstract class ParticleTypeMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void campat$constructed(boolean overrideLimiter, CallbackInfo ci) {
        LegacyParticles.constructed((ParticleType<?>) (Object) this);
    }
}
