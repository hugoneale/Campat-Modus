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

    /** Forge 47's {@code RenderTickEvent.renderTickTime} */
    public static float renderTickTime(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        return event.getTimer().getGameTimeDeltaPartialTick(true);
    }

    // ---- client 1.20.1 members (see tools/legacy/rules.txt) ----

    public static com.mojang.blaze3d.vertex.VertexFormat positionColorTex() {
        return com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR;
    }

    public static net.minecraft.resources.ResourceLocation skinTexture(net.minecraft.client.player.AbstractClientPlayer player) {
        return player.getSkin().texture();
    }

    public static net.minecraft.resources.ResourceLocation capeTexture(net.minecraft.client.player.AbstractClientPlayer player) {
        return player.getSkin().capeTexture();
    }

    public static net.minecraft.resources.ResourceLocation skinLocation(net.minecraft.client.multiplayer.PlayerInfo info) {
        return info.getSkin().texture();
    }

    public static net.minecraft.resources.ResourceLocation defaultSkin(java.util.UUID id) {
        return net.minecraft.client.resources.DefaultPlayerSkin.get(id).texture();
    }

    /** Forge 52's RenderLevelStageEvent hands out the model view matrix */
    public static com.mojang.blaze3d.vertex.PoseStack poseStack(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        com.mojang.blaze3d.vertex.PoseStack stack = new com.mojang.blaze3d.vertex.PoseStack();
        stack.last().pose().mul(event.getPoseStack());
        return stack;
    }

    public static int integerSize(com.mojang.blaze3d.vertex.VertexFormat format) {
        return format.getVertexSize() / 4;
    }

    public static float partialTick(net.minecraft.client.Minecraft minecraft) {
        return minecraft.getTimer().getGameTimeDeltaPartialTick(true);
    }

    public static boolean isConnectedToRealms(net.minecraft.client.Minecraft minecraft) {
        return false;
    }

    public static com.mojang.authlib.GameProfile gameProfile(net.minecraft.client.User user) {
        return new com.mojang.authlib.GameProfile(user.getProfileId(), user.getName());
    }

    public static void moveCursorTo(net.minecraft.client.gui.components.EditBox box, int position) {
        box.moveCursorTo(position, false);
    }

    public static double scrollDelta(net.minecraftforge.client.event.ScreenEvent.MouseScrolled event) {
        return event.getDeltaY();
    }

    public static net.minecraft.client.renderer.RenderType glintDirect() {
        return net.minecraft.client.renderer.RenderType.glint();
    }

    public static net.minecraft.client.renderer.ShaderInstance positionColorTexShader() {
        return net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader();
    }

    public static net.minecraft.resources.ResourceLocation backgroundLocation() {
        return net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/gui/menu_background.png");
    }
}
