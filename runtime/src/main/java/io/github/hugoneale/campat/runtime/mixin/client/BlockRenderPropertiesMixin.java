package io.github.hugoneale.campat.runtime.mixin.client;

import io.github.hugoneale.campat.runtime.client.RenderPropertiesAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = net.minecraft.world.level.block.Block.class, remap = false)
public abstract class BlockRenderPropertiesMixin implements RenderPropertiesAccessor {
    @Shadow
    private Object renderProperties;

    @Override
    public void campat$setRenderProperties(Object properties) {
        this.renderProperties = properties;
    }
}
