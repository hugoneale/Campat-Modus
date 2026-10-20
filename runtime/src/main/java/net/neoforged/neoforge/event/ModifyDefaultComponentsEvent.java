package net.neoforged.neoforge.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge's event for changing items' default data components (mod bus). On Forge the collected
 * changes are applied through Forge's {@code GatherComponentsEvent.Item}, which builds each item's
 * component map lazily.
 */
public final class ModifyDefaultComponentsEvent extends Event implements IModBusEvent {
    public record Change(Predicate<? super Item> items, Consumer<DataComponentPatch.Builder> patch) {}

    private final List<Change> changes = new ArrayList<>();

    public ModifyDefaultComponentsEvent() {}

    public void modify(ItemLike item, Consumer<DataComponentPatch.Builder> patch) {
        Item target = item.asItem();
        changes.add(new Change(i -> i == target, patch));
    }

    public void modifyMatching(Predicate<? super Item> predicate, Consumer<DataComponentPatch.Builder> patch) {
        changes.add(new Change(predicate, patch));
    }

    public Stream<Item> getAllItems() {
        return BuiltInRegistries.ITEM.stream();
    }

    public List<Change> changes() {
        return changes;
    }
}
