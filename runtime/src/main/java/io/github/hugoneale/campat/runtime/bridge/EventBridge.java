package io.github.hugoneale.campat.runtime.bridge;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/** NeoForge event members that Forge's events of the same name lack. */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class EventBridge {
    private EventBridge() {}

    // ---- RegisterEvent ----

    public static Registry getRegistry(RegisterEvent event) {
        return event.getVanillaRegistry();
    }

    @Nullable
    public static Registry getRegistry(RegisterEvent event, ResourceKey key) {
        return event.getRegistryKey().equals(key) ? event.getVanillaRegistry() : null;
    }

    // ---- OnDatapackSyncEvent ----

    public static Stream<ServerPlayer> getRelevantPlayers(OnDatapackSyncEvent event) {
        return event.getPlayer() != null ? Stream.of(event.getPlayer()) : event.getPlayerList().getPlayers().stream();
    }

    // ---- BuildCreativeModeTabContentsEvent ----

    public static void insertAfter(BuildCreativeModeTabContentsEvent event, ItemStack existing, ItemStack entry, CreativeModeTab.TabVisibility visibility) {
        event.getEntries().putAfter(existing, entry, visibility);
    }

    public static void insertBefore(BuildCreativeModeTabContentsEvent event, ItemStack existing, ItemStack entry, CreativeModeTab.TabVisibility visibility) {
        event.getEntries().putBefore(existing, entry, visibility);
    }

    public static void insertFirst(BuildCreativeModeTabContentsEvent event, ItemStack entry, CreativeModeTab.TabVisibility visibility) {
        event.getEntries().putFirst(entry, visibility);
    }

    public static void remove(BuildCreativeModeTabContentsEvent event, ItemStack existing, CreativeModeTab.TabVisibility visibility) {
        event.getEntries().remove(existing);
    }

    public static ObjectSortedSet<ItemStack> getParentEntries(BuildCreativeModeTabContentsEvent event) {
        return entries(event, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
    }

    public static ObjectSortedSet<ItemStack> getSearchEntries(BuildCreativeModeTabContentsEvent event) {
        return entries(event, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
    }

    /** NeoForge exposes read-only views per tab; Forge keeps one map with a visibility per entry. */
    private static ObjectSortedSet<ItemStack> entries(BuildCreativeModeTabContentsEvent event, CreativeModeTab.TabVisibility excluded) {
        ObjectSortedSet<ItemStack> set = new ObjectLinkedOpenCustomHashSet<>(net.minecraft.world.item.ItemStackLinkedSet.TYPE_AND_TAG);
        for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> e : event.getEntries()) {
            if (e.getValue() != excluded) set.add(e.getKey());
        }
        return set;
    }

    // ---- ReloadableServerResources ----

    public static HolderLookup.Provider getRegistryLookup(ReloadableServerResources resources) {
        try {
            if (registryLookupField == null) {
                java.lang.reflect.Field f = ReloadableServerResources.class.getDeclaredField("registryLookup");
                f.setAccessible(true);
                registryLookupField = f;
            }
            return (HolderLookup.Provider) registryLookupField.get(resources);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static java.lang.reflect.Field registryLookupField;
}
