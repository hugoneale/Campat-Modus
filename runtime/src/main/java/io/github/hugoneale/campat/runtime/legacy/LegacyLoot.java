package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/** 1.20.1 loot conditions / functions / number providers were (de)serialized with Gson; 1.21.1 uses codecs. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyLoot {
    private LegacyLoot() {}

    /** {@code loot.Serializer} */
    public interface Serializer<T> {
        void serialize(JsonObject json, T value, JsonSerializationContext context);

        T deserialize(JsonObject json, JsonDeserializationContext context);
    }

    /** {@code LootItemConditionalFunction.Serializer} */
    public abstract static class ConditionalFunctionSerializer<T extends LootItemConditionalFunction> implements Serializer<T> {
        public ConditionalFunctionSerializer() {}

        @Override
        public void serialize(JsonObject json, T value, JsonSerializationContext context) {
            List<LootItemCondition> preds = predicatesList(value);
            if (!preds.isEmpty()) json.add("conditions", encode(LootItemCondition.DIRECT_CODEC.listOf(), preds));
        }

        @Override
        public final T deserialize(JsonObject json, JsonDeserializationContext context) {
            LootItemCondition[] conditions = json.has("conditions") ? (LootItemCondition[]) CONTEXT.deserialize(json.get("conditions"), LootItemCondition[].class) : new LootItemCondition[0];
            return deserialize(json, context, conditions);
        }

        public abstract T deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions);
    }

    /** {@code LootPoolEntryContainer.Serializer} */
    public abstract static class EntrySerializer<T extends net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer> implements Serializer<T> {
        public EntrySerializer() {}

        @Override
        public final void serialize(JsonObject json, T value, JsonSerializationContext context) {
            List<LootItemCondition> conditions = entryConditions(value);
            if (!conditions.isEmpty()) json.add("conditions", encode(LootItemCondition.DIRECT_CODEC.listOf(), conditions));
            serializeCustom(json, value, context);
        }

        @Override
        public final T deserialize(JsonObject json, JsonDeserializationContext context) {
            LootItemCondition[] conditions = json.has("conditions") ? (LootItemCondition[]) CONTEXT.deserialize(json.get("conditions"), LootItemCondition[].class) : new LootItemCondition[0];
            return deserializeCustom(json, context, conditions);
        }

        public abstract void serializeCustom(JsonObject json, T value, JsonSerializationContext context);

        public abstract T deserializeCustom(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions);
    }

    /** {@code LootPoolSingletonContainer.Serializer} */
    public abstract static class SingletonSerializer<T extends net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer> extends EntrySerializer<T> {
        public SingletonSerializer() {}

        @Override
        public void serializeCustom(JsonObject json, T value, JsonSerializationContext context) {
            int weight = singletonInt(value, "weight"), quality = singletonInt(value, "quality");
            if (weight != 1) json.addProperty("weight", weight);
            if (quality != 0) json.addProperty("quality", quality);
            List<net.minecraft.world.level.storage.loot.functions.LootItemFunction> functions = singletonFunctionList(value);
            if (!functions.isEmpty()) json.add("functions", encode(net.minecraft.world.level.storage.loot.functions.LootItemFunctions.ROOT_CODEC.listOf(), functions));
        }

        @Override
        public final T deserializeCustom(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
            int quality = json.has("quality") ? json.get("quality").getAsInt() : 0;
            net.minecraft.world.level.storage.loot.functions.LootItemFunction[] functions = json.has("functions")
                ? decode(net.minecraft.world.level.storage.loot.functions.LootItemFunctions.ROOT_CODEC.listOf(), json.get("functions")).toArray(net.minecraft.world.level.storage.loot.functions.LootItemFunction[]::new)
                : new net.minecraft.world.level.storage.loot.functions.LootItemFunction[0];
            return deserialize(json, context, weight, quality, conditions, functions);
        }

        protected abstract T deserialize(JsonObject json, JsonDeserializationContext context, int weight, int quality, LootItemCondition[] conditions,
                                         net.minecraft.world.level.storage.loot.functions.LootItemFunction[] functions);
    }

    public static net.minecraft.world.level.storage.loot.entries.LootPoolEntryType entryType(Serializer serializer) {
        return new net.minecraft.world.level.storage.loot.entries.LootPoolEntryType(mapCodec(serializer));
    }

    /** LootPoolSingletonContainer(weight, quality, conditions[], functions[]) -> lists */
    public static Object[] singletonArgs(int weight, int quality, LootItemCondition[] conditions, net.minecraft.world.level.storage.loot.functions.LootItemFunction[] functions) {
        return new Object[]{weight, quality, conditions == null ? List.of() : Arrays.asList(conditions), functions == null ? List.of() : Arrays.asList(functions)};
    }

    private static Object field(Class<?> owner, String name, Object target) {
        try {
            java.lang.reflect.Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<LootItemCondition> entryConditions(net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer entry) {
        return (List<LootItemCondition>) field(net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.class, "conditions", entry);
    }

    /** 1.20.1's {@code LootPoolEntryContainer.conditions} array */
    public static LootItemCondition[] entryConditionArray(net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer entry) {
        return entryConditions(entry).toArray(LootItemCondition[]::new);
    }

    private static int singletonInt(net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer entry, String name) {
        return (Integer) field(net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer.class, name, entry);
    }

    @SuppressWarnings("unchecked")
    private static List<net.minecraft.world.level.storage.loot.functions.LootItemFunction> singletonFunctionList(net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer entry) {
        return (List<net.minecraft.world.level.storage.loot.functions.LootItemFunction>) field(net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer.class, "functions", entry);
    }

    /** 1.20.1's {@code LootPoolSingletonContainer.functions} array */
    public static net.minecraft.world.level.storage.loot.functions.LootItemFunction[] singletonFunctions(net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer entry) {
        return singletonFunctionList(entry).toArray(net.minecraft.world.level.storage.loot.functions.LootItemFunction[]::new);
    }

    private static RegistryOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, LegacyRegistries.provider());
    }

    private static <T> T decode(Codec<T> codec, JsonElement json) {
        return codec.parse(ops(), json).getOrThrow(JsonParseException::new);
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(ops(), value).getOrThrow(JsonParseException::new);
    }

    /** a Gson context that knows the loot types 1.21.1 parses with codecs */
    public static final JsonDeserializationContext CONTEXT = new JsonDeserializationContext() {
        private final Gson gson = new Gson();

        @Override
        public <T> T deserialize(JsonElement json, Type type) throws JsonParseException {
            if (type == LootItemCondition[].class) {
                List<LootItemCondition> list = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray()) list.add(decode(LootItemCondition.DIRECT_CODEC, e));
                return (T) list.toArray(LootItemCondition[]::new);
            }
            if (type == LootItemCondition.class) return (T) decode(LootItemCondition.DIRECT_CODEC, json);
            if (type == LootItemFunction[].class) {
                List<LootItemFunction> list = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray()) list.add(decode(LootItemFunctions.ROOT_CODEC, e));
                return (T) list.toArray(LootItemFunction[]::new);
            }
            if (type == LootItemFunction.class) return (T) decode(LootItemFunctions.ROOT_CODEC, json);
            if (type == NumberProvider.class) return (T) decode(NumberProviders.CODEC, json);
            return gson.fromJson(json, type);
        }
    };

    public static final JsonSerializationContext SERIALIZE_CONTEXT = new JsonSerializationContext() {
        private final Gson gson = new Gson();

        @Override
        public JsonElement serialize(Object src) {
            return serialize(src, src.getClass());
        }

        @Override
        public JsonElement serialize(Object src, Type type) {
            if (src instanceof LootItemCondition c) return encode(LootItemCondition.DIRECT_CODEC, c);
            if (src instanceof NumberProvider n) return encode(NumberProviders.CODEC, n);
            if (src instanceof LootItemCondition[] a) {
                JsonArray arr = new JsonArray();
                for (LootItemCondition c : a) arr.add(encode(LootItemCondition.DIRECT_CODEC, c));
                return arr;
            }
            return gson.toJsonTree(src, type);
        }
    };

    /** a Gson serializer as a MapCodec */
    public static <T> MapCodec<T> mapCodec(Serializer<T> serializer) {
        return MapCodec.assumeMapUnsafe(new Codec<T>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                try {
                    return DataResult.success(Pair.of(serializer.deserialize(json.getAsJsonObject(), CONTEXT), input));
                } catch (RuntimeException e) {
                    return DataResult.error(e::toString);
                }
            }

            @Override
            public <O> DataResult<O> encode(T value, DynamicOps<O> ops, O prefix) {
                JsonObject json = new JsonObject();
                serializer.serialize(json, value, SERIALIZE_CONTEXT);
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
            }
        });
    }

    public static LootItemConditionType conditionType(Serializer serializer) {
        return new LootItemConditionType(mapCodec(serializer));
    }

    public static LootItemFunctionType functionType(Serializer serializer) {
        return new LootItemFunctionType(mapCodec(serializer));
    }

    public static LootNumberProviderType numberProviderType(Serializer serializer) {
        return new LootNumberProviderType(mapCodec(serializer));
    }

    /** LootItemConditionalFunction(conditions[]) -> (List) */
    public static Object[] conditionalArgs(LootItemCondition[] conditions) {
        return new Object[]{conditions == null ? List.of() : Arrays.asList(conditions)};
    }

    public static List<LootItemCondition> predicatesList(LootItemConditionalFunction f) {
        try {
            java.lang.reflect.Field field = LootItemConditionalFunction.class.getDeclaredField("predicates");
            field.setAccessible(true);
            return (List<LootItemCondition>) field.get(f);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    public static LootItemCondition[] predicates(LootItemConditionalFunction f) {
        return predicatesList(f).toArray(LootItemCondition[]::new);
    }

    public static LootItemConditionalFunction.Builder<?> simpleBuilder(Function<LootItemCondition[], LootItemFunction> constructor) {
        return SimpleBuilderAccess.build(list -> constructor.apply(list.toArray(LootItemCondition[]::new)));
    }

    /** reaches the protected static {@code simpleBuilder} */
    private abstract static class SimpleBuilderAccess extends LootItemConditionalFunction {
        private SimpleBuilderAccess() {
            super(List.of());
        }

        static LootItemConditionalFunction.Builder<?> build(Function<List<LootItemCondition>, LootItemFunction> f) {
            return LootItemConditionalFunction.simpleBuilder(f);
        }
    }
}
