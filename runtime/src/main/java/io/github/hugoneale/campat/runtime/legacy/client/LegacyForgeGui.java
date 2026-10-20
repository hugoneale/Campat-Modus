package io.github.hugoneale.campat.runtime.legacy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Forge 47's {@code ForgeGui}, handed to 1.20.1 GUI overlays. Forge 52 has no ForgeGui; this instance
 * only carries the per frame state overlays share ({@link #leftHeight} / {@link #rightHeight}).
 */
public class LegacyForgeGui extends Gui {
    public static double rayTraceDistance = 0.0D;
    public int leftHeight = 39;
    public int rightHeight = 39;
    private static LegacyForgeGui instance;

    public LegacyForgeGui(Minecraft mc) {
        super(mc);
    }

    public static LegacyForgeGui get() {
        if (instance == null) instance = new LegacyForgeGui(Minecraft.getInstance());
        return instance;
    }

    /** start of a HUD frame */
    public static void beginFrame() {
        LegacyForgeGui gui = get();
        gui.leftHeight = 39;
        gui.rightHeight = 39;
    }

    public Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }

    public void setupOverlayRenderState(boolean blend, boolean depthTest) {
        if (blend) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        if (depthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public boolean shouldDrawSurvivalElements() {
        return Minecraft.getInstance().gameMode != null && Minecraft.getInstance().gameMode.canHurtPlayer() && Minecraft.getInstance().getCameraEntity() instanceof net.minecraft.world.entity.player.Player;
    }
}
