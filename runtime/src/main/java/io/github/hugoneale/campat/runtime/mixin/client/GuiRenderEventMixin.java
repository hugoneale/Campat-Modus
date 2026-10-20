package io.github.hugoneale.campat.runtime.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.common.MinecraftForge;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's whole-HUD {@link RenderGuiEvent}s; Forge 52 has no equivalent. */
@Mixin(Gui.class)
public abstract class GuiRenderEventMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void campat$pre(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new RenderGuiEvent.Pre(graphics, delta))) ci.cancel();
        else if (io.github.hugoneale.campat.runtime.legacy.client.LegacyGuiOverlays.firePre(graphics, delta)) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void campat$post(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RenderGuiEvent.Post(graphics, delta));
        io.github.hugoneale.campat.runtime.legacy.client.LegacyGuiOverlays.firePost(graphics, delta);
    }
}
