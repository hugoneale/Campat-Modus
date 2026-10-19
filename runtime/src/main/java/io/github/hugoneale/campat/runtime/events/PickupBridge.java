package io.github.hugoneale.campat.runtime.events;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** NeoForge's ItemEntityPickupEvent.Pre/Post from Forge's EntityItemPickupEvent / ItemPickupEvent. */
final class PickupBridge {
    private PickupBridge() {}

    static void register() {
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, true, EntityItemPickupEvent.class, e -> {
            ItemEntityPickupEvent.Pre neo = new ItemEntityPickupEvent.Pre(e.getEntity(), e.getItem());
            bus.post(neo);
            if (neo.canPickup() == TriState.FALSE) e.setCanceled(true);
            else if (neo.canPickup() == TriState.TRUE) e.setResult(Event.Result.ALLOW);
        });
        bus.addListener(EventPriority.HIGHEST, false, PlayerEvent.ItemPickupEvent.class,
            e -> bus.post(new ItemEntityPickupEvent.Post(e.getEntity(), e.getOriginalEntity(), e.getStack())));
    }
}
