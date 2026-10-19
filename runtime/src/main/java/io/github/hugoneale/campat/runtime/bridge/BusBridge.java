package io.github.hugoneale.campat.runtime.bridge;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

/**
 * NeoForge {@code IEventBus} methods without a binary compatible Forge counterpart. Call sites in
 * converted mods are redirected here (see {@code members.txt}).
 */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class BusBridge {
    private BusBridge() {}

    /** NeoForge returns the posted event. */
    public static Event post(IEventBus bus, Event event) {
        bus.post(event);
        return event;
    }

    /** NeoForge's phased post; Forge has no phases, listeners of the requested priority run in order with the rest. */
    public static Event post(IEventBus bus, EventPriority phase, Event event) {
        bus.post(event);
        return event;
    }

    public static void addListener(IEventBus bus, Class type, Consumer consumer) {
        bus.addListener(EventPriority.NORMAL, false, type, consumer);
    }

    public static void addListener(IEventBus bus, EventPriority priority, Class type, Consumer consumer) {
        bus.addListener(priority, false, type, consumer);
    }

    public static void addListener(IEventBus bus, boolean receiveCanceled, Consumer consumer) {
        bus.addListener(EventPriority.NORMAL, receiveCanceled, consumer);
    }

    public static void addListener(IEventBus bus, boolean receiveCanceled, Class type, Consumer consumer) {
        bus.addListener(EventPriority.NORMAL, receiveCanceled, type, consumer);
    }

    /** NeoForge's {@code ICancellableEvent#setCanceled} called through the interface. */
    public static void setCanceled(Object event, boolean canceled) {
        ((Event) event).setCanceled(canceled);
    }

    public static boolean isCanceled(Object event) {
        return ((Event) event).isCanceled();
    }
}
