package io.github.hugoneale.campat.runtime.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

public final class ClientTickBridge {
    private ClientTickBridge() {}

    public static void register() {
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, true, TickEvent.ClientTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) bus.post(new ClientTickEvent.Pre());
            else bus.post(new ClientTickEvent.Post());
        });
        bus.addListener(EventPriority.HIGHEST, true, TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) bus.post(new RenderFrameEvent.Pre(e.getTimer()));
            else bus.post(new RenderFrameEvent.Post(e.getTimer()));
        });
    }
}
