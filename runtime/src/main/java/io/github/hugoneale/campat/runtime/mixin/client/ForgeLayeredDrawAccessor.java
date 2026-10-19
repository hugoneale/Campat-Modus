package io.github.hugoneale.campat.runtime.mixin.client;

import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ForgeLayeredDraw.class, remap = false)
public interface ForgeLayeredDrawAccessor {
    @Accessor("namedLayers")
    Map<ResourceLocation, LayeredDraw.Layer> campat$namedLayers();

    @Accessor("subLayerStacks")
    Map<ResourceLocation, Map.Entry<LayeredDraw, BooleanSupplier>> campat$subLayerStacks();
}
