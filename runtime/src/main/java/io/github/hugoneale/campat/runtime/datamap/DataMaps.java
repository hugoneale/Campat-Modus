package io.github.hugoneale.campat.runtime.datamap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import io.github.hugoneale.campat.runtime.CampatModus;
import io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapEntry;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * NeoForge registry data maps on Forge: types are collected through {@link RegisterDataMapTypesEvent},
 * files ({@code data/<ns>/data_maps/<registry>/<path>.json}) are read by a server reload listener,
 * and lookups are answered from here (the converter redirects {@code Registry.getData},
 * {@code Holder.getData}, ...). Tag entries are resolved once tags are bound, which in 1.21 happens
 * after all reload listeners ran.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class DataMaps {
    private DataMaps() {}

    private static final Map<ResourceKey<Registry<?>>, Map<ResourceLocation, DataMapType<?, ?>>> TYPES = new HashMap<>();
    /** loaded but not yet materialized operations, per type, in pack order */
    private static volatile Map<DataMapType<?, ?>, List<Op>> pending = Map.of();
    private static volatile Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> data = Map.of();
    private static volatile boolean dirty;
    private static RegistryAccess access;

    private record Op(Either<TagKey<?>, ResourceKey<?>> target, Object value, boolean replaceAll, boolean remove) {}

    // ---- lifecycle ----

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(DataMaps::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> dirty = true);
        MinecraftForge.EVENT_BUS.addListener(DataMaps::onFurnaceFuel);
    }

    /** mod bus, once registries exist */
    public static void fireRegisterTypes() {
        RegisterDataMapTypesEvent event = new RegisterDataMapTypesEvent(TYPES);
        for (DataMapType<?, ?> t : List.of(NeoForgeDataMaps.COMPOSTABLES, NeoForgeDataMaps.FURNACE_FUELS, NeoForgeDataMaps.MONSTER_ROOM_MOBS,
            NeoForgeDataMaps.OXIDIZABLES, NeoForgeDataMaps.PARROT_IMITATIONS, NeoForgeDataMaps.RAID_HERO_GIFTS, NeoForgeDataMaps.STRIPPABLES,
            NeoForgeDataMaps.VIBRATION_FREQUENCIES, NeoForgeDataMaps.VILLAGER_TYPES, NeoForgeDataMaps.WAXABLES)) {
            event.register((DataMapType) t);
        }
        NeoLifecycle.postModBus(event);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader(event.getRegistryAccess(), event.getConditionContext()));
    }

    private static String folder(ResourceLocation registry) {
        return registry.getNamespace().equals("minecraft") ? registry.getPath() : registry.getNamespace() + "/" + registry.getPath();
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<DataMapType<?, ?>, List<Op>>> {
        private final RegistryAccess registries;
        private final ICondition.IContext conditions;

        Loader(RegistryAccess registries, ICondition.IContext conditions) {
            this.registries = registries;
            this.conditions = conditions;
        }

        @Override
        protected Map<DataMapType<?, ?>, List<Op>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<DataMapType<?, ?>, List<Op>> out = new LinkedHashMap<>();
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            for (var reg : TYPES.entrySet()) {
                FileToIdConverter files = FileToIdConverter.json("data_maps/" + folder(reg.getKey().location()));
                for (var stack : files.listMatchingResourceStacks(manager).entrySet()) {
                    ResourceLocation id = files.fileToId(stack.getKey());
                    DataMapType<?, ?> type = reg.getValue().get(id);
                    if (type == null) {
                        CampatModus.LOGGER.debug("Ignoring data map file {} for unknown type {}", stack.getKey(), id);
                        continue;
                    }
                    List<Op> list = out.computeIfAbsent(type, k -> new ArrayList<>());
                    for (Resource r : stack.getValue()) {
                        try (Reader reader = r.openAsReader()) {
                            read(type, JsonParser.parseReader(reader).getAsJsonObject(), ops, list);
                        } catch (Exception e) {
                            CampatModus.LOGGER.error("Failed to read data map {} from {}", id, r.sourcePackId(), e);
                        }
                    }
                }
            }
            return out;
        }

        private void read(DataMapType<?, ?> type, JsonObject json, RegistryOps<JsonElement> ops, List<Op> list) {
            if (!conditionsPass(json)) return;
            if (json.has("replace") && json.get("replace").getAsBoolean()) list.add(new Op(null, null, true, false));
            var entryCodec = DataMapEntry.codec((DataMapType) type);
            if (json.has("values")) {
                for (var e : json.getAsJsonObject("values").entrySet()) {
                    if (e.getValue().isJsonObject() && !conditionsPass(e.getValue().getAsJsonObject())) continue;
                    Either<TagKey<?>, ResourceKey<?>> target = target(type, e.getKey());
                    var result = entryCodec.parse(ops, e.getValue());
                    if (result.isError()) {
                        CampatModus.LOGGER.error("Invalid data map {} value for {}: {}", type.id(), e.getKey(), result.error().map(err -> ((com.mojang.serialization.DataResult.Error<?>) err).message()).orElse("?"));
                        continue;
                    }
                    DataMapEntry<?> entry = (DataMapEntry<?>) result.getOrThrow();
                    if (entry.replace()) list.add(new Op(target, null, false, true));
                    list.add(new Op(target, entry.value(), false, false));
                }
            }
            if (json.has("remove")) {
                for (JsonElement el : json.getAsJsonArray("remove")) {
                    String key = el.isJsonPrimitive() ? el.getAsString() : el.getAsJsonObject().get("key").getAsString();
                    list.add(new Op(target(type, key), null, false, true));
                }
            }
        }

        private boolean conditionsPass(JsonObject json) {
            JsonElement cond = json.has("forge:condition") ? json.get("forge:condition") : null;
            if (cond == null) return true;
            var parsed = ICondition.SAFE_CODEC.parse(JsonOps.INSTANCE, cond);
            return parsed.result().map(c -> ((ICondition) c).test(conditions, JsonOps.INSTANCE)).orElse(Boolean.TRUE);
        }

        @Override
        protected void apply(Map<DataMapType<?, ?>, List<Op>> loaded, ResourceManager manager, ProfilerFiller profiler) {
            pending = loaded;
            access = registries;
            dirty = true;
            materialize();
            for (ResourceKey<Registry<?>> key : TYPES.keySet()) {
                registries.registry((ResourceKey) key).ifPresent(r -> MinecraftForge.EVENT_BUS.post(
                    new DataMapsUpdatedEvent(registries, (Registry<?>) r, DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD)));
            }
        }
    }

    private static Either<TagKey<?>, ResourceKey<?>> target(DataMapType<?, ?> type, String key) {
        if (key.startsWith("#")) return Either.left(TagKey.create((ResourceKey) type.registryKey(), ResourceLocation.parse(key.substring(1))));
        return Either.right(ResourceKey.create((ResourceKey) type.registryKey(), ResourceLocation.parse(key)));
    }

    private static synchronized void materialize() {
        if (!dirty) return;
        Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> out = new HashMap<>();
        for (var e : pending.entrySet()) {
            DataMapType<?, ?> type = e.getKey();
            Optional<Registry<Object>> registry = registry(type.registryKey());
            Map<ResourceKey<?>, Object> values = new LinkedHashMap<>();
            for (Op op : e.getValue()) {
                if (op.replaceAll()) { values.clear(); continue; }
                List<ResourceKey<?>> keys = new ArrayList<>();
                op.target().ifRight(keys::add).ifLeft(tag -> registry.ifPresent(r -> r.getTag((TagKey) tag)
                    .ifPresent(set -> ((Iterable<Holder<Object>>) set).forEach(h -> h.unwrapKey().ifPresent(keys::add)))));
                for (ResourceKey<?> k : keys) {
                    if (op.remove()) values.remove(k);
                    else values.put(k, op.value());
                }
            }
            out.put(type, values);
        }
        data = out;
        dirty = false;
        applyBuiltins();
    }

    private static Optional<Registry<Object>> registry(ResourceKey<?> key) {
        if (access != null) {
            Optional<Registry<Object>> r = access.registry((ResourceKey) key);
            if (r.isPresent()) return r;
        }
        return (Optional) BuiltInRegistries.REGISTRY.getOptional((ResourceKey) key);
    }

    /** NeoForge reads these data maps in its patches of vanilla; on Forge they feed vanilla's own tables. */
    private static void applyBuiltins() {
        Map<ResourceKey<?>, Object> compost = data.getOrDefault(NeoForgeDataMaps.COMPOSTABLES, Map.of());
        for (var e : compost.entrySet()) {
            Item item = BuiltInRegistries.ITEM.get((ResourceKey<Item>) e.getKey());
            if (item != null) ComposterBlock.COMPOSTABLES.put(item, ((Compostable) e.getValue()).chance());
        }
    }

    private static void onFurnaceFuel(FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().isEmpty() || event.getBurnTime() > 0) return;
        Object fuel = getData(NeoForgeDataMaps.FURNACE_FUELS, event.getItemStack().getItemHolder());
        if (fuel instanceof FurnaceFuel f) event.setBurnTime(f.burnTime());
    }

    // ---- lookups (redirect targets) ----

    private static Map<ResourceKey<?>, Object> map(DataMapType<?, ?> type) {
        if (dirty) materialize();
        return data.getOrDefault(type, Map.of());
    }

    public static Object getData(DataMapType type, Holder holder) {
        if (holder == null) return null;
        Optional<ResourceKey> key = holder.unwrapKey();
        return key.map(k -> map(type).get(k)).orElse(null);
    }

    public static Object getData(Registry registry, DataMapType type, ResourceKey key) {
        return map(type).get(key);
    }

    public static Map getDataMap(Registry registry, DataMapType type) {
        return java.util.Collections.unmodifiableMap(map(type));
    }

    public static Object holderGetData(Holder holder, DataMapType type) {
        return getData(type, holder);
    }

    public static ResourceKey holderGetKey(Holder holder) {
        return (ResourceKey) holder.unwrapKey().orElse(null);
    }

    public static Holder holderGetDelegate(Holder holder) {
        return holder;
    }

    public static ResourceLocation getKeyOrNull(Registry registry, Object element) {
        return (ResourceLocation) registry.getResourceKey(element).map(k -> ((ResourceKey) k).location()).orElse(null);
    }

    static {
        // keep a reference so Registries is initialised before data maps are looked up from early code
        Registries.ITEM.location();
    }
}
