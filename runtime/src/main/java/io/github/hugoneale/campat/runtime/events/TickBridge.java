package io.github.hugoneale.campat.runtime.events;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge's split Pre/Post tick events, fired from Forge's phased tick events. */
public final class TickBridge {
    private TickBridge() {}

    public static void register() {
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, true, TickEvent.ServerTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) bus.post(new ServerTickEvent.Pre(e::haveTime, e.getServer()));
            else bus.post(new ServerTickEvent.Post(e::haveTime, e.getServer()));
        });
        bus.addListener(EventPriority.HIGHEST, true, TickEvent.LevelTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) bus.post(new LevelTickEvent.Pre(e::haveTime, e.level));
            else bus.post(new LevelTickEvent.Post(e::haveTime, e.level));
        });
        bus.addListener(EventPriority.HIGHEST, true, TickEvent.PlayerTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) bus.post(new PlayerTickEvent.Pre(e.player));
            else bus.post(new PlayerTickEvent.Post(e.player));
        });
    }

    /** Called by the level mixins around every entity tick. @return false if the tick was canceled */
    public static boolean entityTickPre(Entity entity) {
        return !MinecraftForge.EVENT_BUS.post(new EntityTickEvent.Pre(entity));
    }

    public static void entityTickPost(Entity entity) {
        MinecraftForge.EVENT_BUS.post(new EntityTickEvent.Post(entity));
    }
}
