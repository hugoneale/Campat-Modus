package io.github.hugoneale.campat.runtime.events;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GatherComponentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** Applies NeoForge's ModifyDefaultComponentsEvent changes when Forge gathers an item's components. */
final class ComponentsBridge {
    private ComponentsBridge() {}

    private static volatile List<ModifyDefaultComponentsEvent.Change> changes;

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GatherComponentsEvent.Item.class, ComponentsBridge::onGather);
    }

    /** NeoForge fires the event once registries are frozen; collected lazily at the first item. */
    private static List<ModifyDefaultComponentsEvent.Change> changes() {
        List<ModifyDefaultComponentsEvent.Change> c = changes;
        if (c == null) {
            synchronized (ComponentsBridge.class) {
                if (changes == null) {
                    ModifyDefaultComponentsEvent event = new ModifyDefaultComponentsEvent();
                    io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle.postModBus(event);
                    changes = List.copyOf(event.changes());
                }
                c = changes;
            }
        }
        return c;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGather(GatherComponentsEvent.Item event) {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        boolean any = false;
        for (ModifyDefaultComponentsEvent.Change c : changes()) {
            if (c.items().test(event.getOwner())) {
                c.patch().accept(builder);
                any = true;
            }
        }
        if (!any) return;
        for (var e : builder.build().entrySet()) {
            Optional<?> v = e.getValue();
            if (v.isPresent()) event.register((DataComponentType) e.getKey(), v.get());
        }
    }
}
