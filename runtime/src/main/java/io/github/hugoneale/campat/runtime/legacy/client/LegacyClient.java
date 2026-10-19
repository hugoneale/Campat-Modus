package io.github.hugoneale.campat.runtime.legacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.FastColor;

/** Client side Minecraft 1.20.1 APIs that 1.21.1 removed or changed. */
public final class LegacyClient {
    private LegacyClient() {}

    public static float getFrameTime(Minecraft mc) {
        return mc.getTimer().getGameTimeDeltaPartialTick(true);
    }

    public static float getDeltaFrameTime(Minecraft mc) {
        return mc.getTimer().getGameTimeDeltaTicks();
    }

    public static float getPartialTick(Minecraft mc) {
        return mc.getTimer().getGameTimeDeltaPartialTick(true);
    }

    public static boolean renderDebug(Options options) {
        return Minecraft.getInstance().getDebugOverlay().showDebugScreen();
    }

    // ---- colour as four floats became one packed ARGB int ----

    public static int argb(float r, float g, float b, float a) {
        return FastColor.ARGB32.colorFromFloat(a, r, g, b);
    }

    public static void render(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        part.render(pose, consumer, light, overlay, argb(r, g, b, a));
    }

    public static void renderToBuffer(Model model, PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        model.renderToBuffer(pose, consumer, light, overlay, argb(r, g, b, a));
    }
}
