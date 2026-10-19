package io.github.hugoneale.campat.runtime.mixin.client;

import io.github.hugoneale.campat.runtime.client.RenderPropertiesAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = net.minecraft.world.effect.MobEffect.class, remap = false)
public abstract class MobEffectRenderPropertiesMixin implements RenderPropertiesAccessor {
    @Shadow
    private Object effectRenderer;

    @Override
    public void campat$setRenderProperties(Object properties) {
        this.effectRenderer = properties;
    }
}
