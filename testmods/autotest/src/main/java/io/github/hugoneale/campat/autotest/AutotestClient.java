package io.github.hugoneale.campat.autotest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

final class AutotestClient {
    private static boolean joined;
    private static int ticksInWorld;
    private static boolean done;

    static void init() {
        String target = System.getProperty("campat.autotest.join");
        if (target == null) return;
        int seconds = Integer.getInteger("campat.autotest.seconds", 20);
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> {
            if (e.getNewScreen() != null) Autotest.LOG.info("[AUTOTEST] screen {}", e.getNewScreen().getClass().getName());
            if (e.getNewScreen() instanceof net.minecraftforge.client.gui.LoadingErrorScreen) {
                Autotest.LOG.error("[AUTOTEST] FAIL mod loading warnings/errors screen shown");
                Minecraft mc = Minecraft.getInstance();
                mc.tell(() -> mc.setScreen(new TitleScreen()));
            }
            if (!joined && (e.getNewScreen() instanceof TitleScreen || e.getNewScreen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)) {
                joined = true;
                Minecraft mc = Minecraft.getInstance();
                Autotest.LOG.info("[AUTOTEST] {} reached, joining {}", e.getNewScreen().getClass().getSimpleName(), target);
                mc.execute(() -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                    new ServerData("autotest", target, ServerData.Type.OTHER), false, null));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (done || mc.level == null || mc.player == null) return;
            ticksInWorld++;
            if (ticksInWorld == 1) Autotest.LOG.info("[AUTOTEST] joined world");
            if (ticksInWorld == seconds * 20 - 20) {
                Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), c -> Autotest.LOG.info("[AUTOTEST] screenshot: {}", c.getString()));
            }
            if (ticksInWorld >= seconds * 20) {
                done = true;
                Autotest.LOG.info("[AUTOTEST] done, quitting");
                mc.level.disconnect();
                mc.disconnect();
                mc.stop();
            }
        });
    }
}
