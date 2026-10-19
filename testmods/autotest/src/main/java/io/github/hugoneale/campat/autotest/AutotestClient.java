package io.github.hugoneale.campat.autotest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Tick driven: join from the title screen, stay a while in the world, then quit. */
final class AutotestClient {
    private static boolean joined;
    private static int ticksInWorld;
    private static boolean done;
    private static Class<?> lastScreen;

    static void init() {
        String target = System.getProperty("campat.autotest.join");
        if (target == null) return;
        int seconds = Integer.getInteger("campat.autotest.seconds", 20);
        Autotest.LOG.info("[AUTOTEST] client autotest armed, target {}", target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority(), false, TickEvent.ClientTickEvent.Post.class, e -> {
            Minecraft mc = Minecraft.getInstance();
            Class<?> screen = mc.screen == null ? null : mc.screen.getClass();
            if (screen != lastScreen) {
                lastScreen = screen;
                Autotest.LOG.info("[AUTOTEST] screen {}", screen == null ? "none" : screen.getName());
                if (mc.screen instanceof net.minecraftforge.client.gui.LoadingErrorScreen) {
                    Autotest.LOG.error("[AUTOTEST] FAIL mod loading warnings/errors screen shown");
                    mc.setScreen(new TitleScreen());
                    return;
                }
            }
            if (!joined && mc.screen instanceof TitleScreen) {
                joined = true;
                Autotest.LOG.info("[AUTOTEST] joining {}", target);
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(target), new ServerData("autotest", target, ServerData.Type.OTHER), false, null);
                return;
            }
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

    private static net.minecraftforge.eventbus.api.EventPriority EventPriority() {
        return net.minecraftforge.eventbus.api.EventPriority.NORMAL;
    }
}
