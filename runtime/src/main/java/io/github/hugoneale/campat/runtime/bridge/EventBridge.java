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

    // ---- NeoForge additions to vanilla classes ----

    public static java.util.Set<net.minecraft.world.level.block.Block> getValidBlocks(net.minecraft.world.level.block.entity.BlockEntityType<?> type) {
        return ((io.github.hugoneale.campat.runtime.mixin.accessor.BlockEntityTypeAccessor) type).campat$getValidBlocks();
    }

    public static net.minecraft.server.network.ConfigurationTask.Type configurationTaskType(net.minecraft.resources.ResourceLocation id) {
        return new net.minecraft.server.network.ConfigurationTask.Type(id.toString());
    }

    public static boolean loadingErrors(net.minecraftforge.fml.loading.LoadingModList list) {
        return !list.getErrors().isEmpty();
    }

    /** DynamicOps for NeoForge conditions tested through Forge's two argument ICondition.test */
    public static com.mojang.serialization.DynamicOps<?> conditionOps() {
        return com.mojang.serialization.JsonOps.INSTANCE;
    }

    /** NeoForge's AddPackFindersEvent.addPackFinders: a built in pack from a folder inside a mod jar */
    public static void addPackFinders(net.minecraftforge.event.AddPackFindersEvent event, net.minecraft.resources.ResourceLocation folder,
                                      net.minecraft.server.packs.PackType type, net.minecraft.network.chat.Component title,
                                      net.minecraft.server.packs.repository.PackSource source, boolean alwaysActive,
                                      net.minecraft.server.packs.repository.Pack.Position position) {
        if (event.getPackType() != type) return;
        var modFile = net.minecraftforge.fml.ModList.get().getModFileById(folder.getNamespace());
        if (modFile == null) return;
        java.nio.file.Path path = modFile.getFile().findResource(folder.getPath());
        if (path == null || !java.nio.file.Files.exists(path)) return;
        String id = "mod/" + folder;
        var info = new net.minecraft.server.packs.PackLocationInfo(id, title, source, java.util.Optional.empty());
        var pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(info,
            new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(path), type,
            new net.minecraft.server.packs.PackSelectionConfig(alwaysActive, position, false));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    public static boolean isTrusted(net.minecraftforge.event.AddPackFindersEvent event) {
        return true;
    }

    // ---- NeoForge FML's ModLoadingException(ModLoadingIssue...) ----

    public static net.minecraftforge.fml.ModLoadingException modLoadingException(net.neoforged.fml.ModLoadingIssue issue) {
        return new net.minecraftforge.fml.ModLoadingException(issue.affectedMod(), net.minecraftforge.fml.ModLoadingStage.CONSTRUCT,
            issue.translationKey(), issue.cause(), issue.translationArgs().toArray());
    }

    public static net.minecraftforge.fml.ModLoadingException modLoadingException(java.util.List<net.neoforged.fml.ModLoadingIssue> issues) {
        return modLoadingException(issues.get(0));
    }
}
