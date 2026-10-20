package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * 1.20.1 advancement triggers: registered by instance (the trigger knew its id), instances parsed from
 * JSON by {@code createInstance(json, player, context)}. 1.21.1 registers triggers in a registry and
 * parses instances with a codec.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyCriteria {
    private LegacyCriteria() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<CriterionTrigger<?>> PENDING = new ArrayList<>();
    private static boolean registered;

    /** {@code DeserializationContext} */
    public static class DeserializationContext {
        private final ResourceLocation id;

        public DeserializationContext(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation getAdvancementId() {
            return id;
        }
    }

    /** {@code SerializationContext} */
    public static class SerializationContext {
        public static final SerializationContext INSTANCE = new SerializationContext();
    }

    // ---- registration ----

    public static synchronized CriterionTrigger register(CriterionTrigger trigger) {
        if (registered) {
            ResourceLocation id = idOf(trigger);
            LOGGER.warn("1.20.1 advancement trigger {} registered after registration; it is not usable in advancements", id);
        } else {
            PENDING.add(trigger);
        }
        return trigger;
    }

    public static synchronized void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.TRIGGER_TYPE)) return;
        for (CriterionTrigger<?> t : PENDING) {
            ResourceLocation id = idOf(t);
            if (id == null) continue;
            event.register(Registries.TRIGGER_TYPE, id, () -> (CriterionTrigger) t);
        }
        PENDING.clear();
        registered = true;
    }

    private static ResourceLocation idOf(CriterionTrigger<?> trigger) {
        for (Method m : trigger.getClass().getMethods()) {
            if ((m.getName().equals("getId") || m.getName().equals("m_7295_")) && m.getParameterCount() == 0 && m.getReturnType() == ResourceLocation.class) {
                try {
                    return (ResourceLocation) m.invoke(trigger);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    // ---- instances ----

    private static ContextAwarePredicate ANY;

    public static synchronized ContextAwarePredicate anyPlayer() {
        if (ANY == null) ANY = ContextAwarePredicate.create();
        return ANY;
    }

    private static <T> DynamicOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, LegacyRegistries.provider());
    }

    public static Codec codec(SimpleCriterionTrigger trigger) {
        Method create = null;
        for (Class<?> c = trigger.getClass(); c != null && create == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if ((m.getName().equals("createInstance") || m.getName().equals("m_7214_")) && m.getParameterCount() == 3 && m.getParameterTypes()[0] == JsonObject.class) {
                    m.setAccessible(true);
                    create = m;
                    break;
                }
            }
        }
        Method fcreate = create;
        return new Codec() {
            @Override
            public DataResult decode(DynamicOps ops, Object input) {
                if (fcreate == null) return DataResult.error(() -> trigger + " has no 1.20.1 createInstance");
                JsonElement json = (JsonElement) ops.convertTo(JsonOps.INSTANCE, input);
                JsonObject obj = json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
                try {
                    ContextAwarePredicate player = entityPredicate(obj, "player", null);
                    Object instance = fcreate.invoke(trigger, obj, player, new DeserializationContext(idOf(trigger)));
                    return DataResult.success(Pair.of(instance, input));
                } catch (java.lang.reflect.InvocationTargetException e) {
                    return DataResult.error(() -> String.valueOf(e.getCause()));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return DataResult.error(e::toString);
                }
            }

            @Override
            public DataResult encode(Object value, DynamicOps ops, Object prefix) {
                JsonObject json = value instanceof LegacyCriterionInstance l ? l.serializeToJson(SerializationContext.INSTANCE) : new JsonObject();
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
            }
        };
    }

    /** SimpleCriterionTrigger.createInstance(json, context): the public 1.20.1 entry point */
    public static LegacyCriterionInstance createInstance(SimpleCriterionTrigger trigger, JsonObject json, DeserializationContext context) {
        return (LegacyCriterionInstance) codec(trigger).parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    // ---- predicates: JSON -> codecs ----

    public static ContextAwarePredicate entityPredicate(JsonObject json, String key, DeserializationContext context) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return anyPlayer();
        return EntityPredicate.ADVANCEMENT_CODEC.parse(ops(), e).result().orElse(anyPlayer());
    }

    public static ItemPredicate itemPredicate(JsonElement json) {
        if (json == null || json.isJsonNull()) return anyItem();
        return ItemPredicate.CODEC.parse(ops(), json).result().orElse(anyItem());
    }

    public static ItemPredicate[] itemPredicates(JsonElement json) {
        if (json == null || json.isJsonNull()) return new ItemPredicate[0];
        List<ItemPredicate> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray()) out.add(itemPredicate(e));
        return out.toArray(ItemPredicate[]::new);
    }

    public static ItemPredicate anyItem() {
        return ItemPredicate.Builder.item().build();
    }

    public static LocationPredicate locationPredicate(JsonElement json) {
        if (json == null || json.isJsonNull()) return anyLocation();
        return LocationPredicate.CODEC.parse(ops(), json).result().orElse(anyLocation());
    }

    public static LocationPredicate anyLocation() {
        return LocationPredicate.Builder.location().build();
    }

    public static MinMaxBounds.Ints ints(JsonElement json) {
        if (json == null || json.isJsonNull()) return MinMaxBounds.Ints.ANY;
        return MinMaxBounds.Ints.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(MinMaxBounds.Ints.ANY);
    }

    public static MinMaxBounds.Doubles doubles(JsonElement json) {
        if (json == null || json.isJsonNull()) return MinMaxBounds.Doubles.ANY;
        return MinMaxBounds.Doubles.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(MinMaxBounds.Doubles.ANY);
    }

    public static JsonElement serialize(Object predicate) {
        return JsonNull.INSTANCE;
    }
}
