package io.github.hugoneale.campat.autotest;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Drives automated sessions:
 * <ul>
 *   <li>client: {@code -Dcampat.autotest.join=host:port} joins the server from the title screen,
 *   stays {@code campat.autotest.seconds} seconds, then quits the game.</li>
 *   <li>server: {@code -Dcampat.autotest.stopAfterLeave=true} stops the server once a player left.</li>
 * </ul>
 */
@Mod("campat_autotest")
public final class Autotest {
    static final Logger LOG = LogUtils.getLogger();

    public Autotest(FMLJavaModLoadingContext ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AutotestClient.init();
        }
        // Forge side of the capability interop test: a Forge mod querying a NeoForge mod's block entity
        int[] ticks = {0};
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post e) -> {
            if (++ticks[0] != 60) return;
            var be = e.getServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 100, 0));
            if (be == null) { LOG.info("[AUTOTEST] no test block entity, skipping Forge capability interop check"); return; }
            var h = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.UP).resolve();
            boolean ok = h.isPresent() && h.get().getStackInSlot(0).getCount() == 3;
            if (ok) LOG.info("[CAMPATTEST] PASS capability.forge-interop");
            else LOG.error("[CAMPATTEST] FAIL capability.forge-interop: {}", h);
        });
        if (Boolean.getBoolean("campat.autotest.stopAfterLeave")) {
            MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
                LOG.info("[AUTOTEST] player left, stopping server");
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) server.execute(() -> server.halt(false));
            });
        }
    }
}
