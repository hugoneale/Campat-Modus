package io.github.hugoneale.campat.runtime.legacy.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import io.github.hugoneale.campat.runtime.legacy.LegacyApi;
import io.github.hugoneale.campat.runtime.legacy.LegacyBufs;
import io.github.hugoneale.campat.runtime.legacy.LegacyItemStacks;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * Forge 47's {@code CraftingHelper} and its JSON based ingredient / condition serializers, over
 * Forge 52's codec based {@link IIngredientSerializer} and {@link ICondition}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyForgeCrafting {
    private LegacyForgeCrafting() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Forge 47's {@code IIngredientSerializer} */
    public interface IngredientSerializer<T extends Ingredient> {
        T parse(FriendlyByteBuf buf);

        T parse(JsonObject json);

        void write(FriendlyByteBuf buf, T ingredient);
    }

    /** Forge 47's {@code IConditionSerializer} */
    public interface ConditionSerializer<T extends ICondition> {
        void write(JsonObject json, T value);

        T read(JsonObject json);

        ResourceLocation getID();

        default JsonObject getJson(T value) {
            JsonObject json = new JsonObject();
            write(json, value);
            json.addProperty("type", getID().toString());
            return json;
        }
    }

    private static final Map<IngredientSerializer<?>, IIngredientSerializer<?>> INGREDIENTS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, IIngredientSerializer<?>> PENDING_INGREDIENTS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ConditionSerializer<?>> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, MapCodec<? extends ICondition>> CONDITION_CODECS = new ConcurrentHashMap<>();
    private static boolean registered;

    // ---- registration ----

    public static ConditionSerializer<?> register(ConditionSerializer<?> serializer) {
        CONDITIONS.put(serializer.getID(), serializer);
        CONDITION_CODECS.put(serializer.getID(), conditionCodec(serializer));
        if (registered) LOGGER.warn("1.20.1 condition serializer {} registered after registration", serializer.getID());
        return serializer;
    }

    public static <T extends Ingredient> IngredientSerializer<T> register(ResourceLocation id, IngredientSerializer<T> serializer) {
        PENDING_INGREDIENTS.put(id, wrap(serializer));
        if (registered) LOGGER.warn("1.20.1 ingredient serializer {} registered after registration", id);
        return serializer;
    }

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS)) {
            PENDING_INGREDIENTS.forEach((id, s) -> event.register(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS, id, () -> (IIngredientSerializer) s));
            registered = true;
        }
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.CONDITION_SERIALIZERS)) {
            CONDITION_CODECS.forEach((id, c) -> event.register(ForgeRegistries.Keys.CONDITION_SERIALIZERS, id, () -> (MapCodec) c));
        }
    }

    // ---- ingredients ----

    public static <T extends Ingredient> IIngredientSerializer<T> wrap(IngredientSerializer<T> legacy) {
        return (IIngredientSerializer<T>) INGREDIENTS.computeIfAbsent(legacy, l -> new IIngredientSerializer<T>() {
            private final MapCodec<T> codec = MapCodec.assumeMapUnsafe(new Codec<T>() {
                @Override
                public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) return DataResult.error(() -> "not a JSON object: " + json);
                    try {
                        return DataResult.success(Pair.of(((IngredientSerializer<T>) l).parse(json.getAsJsonObject()), input));
                    } catch (RuntimeException e) {
                        return DataResult.error(e::toString);
                    }
                }

                @Override
                public <O> DataResult<O> encode(T value, DynamicOps<O> ops, O prefix) {
                    try {
                        Method toJson = value.getClass().getMethod("toJson");
                        JsonElement json = (JsonElement) toJson.invoke(value);
                        return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
                    } catch (ReflectiveOperationException e) {
                        return DataResult.error(() -> "1.20.1 ingredient cannot be written: " + value);
                    }
                }
            });

            @Override
            public MapCodec<? extends T> codec() {
                return codec;
            }

            @Override
            public void write(RegistryFriendlyByteBuf buf, T value) {
                ((IngredientSerializer<T>) l).write(buf, value);
            }

            @Override
            public T read(RegistryFriendlyByteBuf buf) {
                return ((IngredientSerializer<T>) l).parse(buf);
            }
        });
    }

    /** AbstractIngredient.serializer() for 1.20.1 ingredients, which only have getSerializer() */
    public static IIngredientSerializer<?> serializer(AbstractIngredient ingredient) {
        for (Method m : ingredient.getClass().getMethods()) {
            if (m.getName().equals("getSerializer") && m.getParameterCount() == 0 && IngredientSerializer.class.isAssignableFrom(m.getReturnType())) {
                try {
                    return wrap((IngredientSerializer) m.invoke(ingredient));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("1.20.1 ingredient without serializer: " + ingredient.getClass().getName());
    }

    public static Ingredient getIngredient(JsonElement json, boolean allowEmpty) {
        return LegacyApi.ingredientFromJson(json);
    }

    public static Ingredient getIngredient(ResourceLocation type, FriendlyByteBuf buf) {
        return LegacyApi.ingredientFromNetwork(buf);
    }

    public static <T extends Ingredient> void write(FriendlyByteBuf buf, T ingredient) {
        LegacyApi.ingredientToNetwork(ingredient, buf);
    }

    public static ResourceLocation getID(IngredientSerializer<?> serializer) {
        IIngredientSerializer<?> s = INGREDIENTS.get(serializer);
        return s == null ? null : ForgeRegistries.INGREDIENT_SERIALIZERS.get().getKey(s);
    }

    // ---- item stacks ----

    public static ItemStack getItemStack(JsonObject json, boolean readNbt) {
        return getItemStack(json, readNbt, false);
    }

    public static ItemStack getItemStack(JsonObject json, boolean readNbt, boolean disallowsAirInRecipe) {
        String name = json.has("item") ? json.get("item").getAsString() : json.get("id").getAsString();
        Item item = getItem(name, disallowsAirInRecipe);
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        ItemStack stack = new ItemStack(item, count);
        if (readNbt && json.has("nbt")) {
            CompoundTag tag = getNBT(json.get("nbt"));
            LegacyItemStacks.setTag(stack, tag);
        }
        return stack;
    }

    public static Item getItem(String name, boolean disallowsAirInRecipe) {
        ResourceLocation id = ResourceLocation.parse(name);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new JsonSyntaxException("Unknown item '" + name + "'");
        Item item = BuiltInRegistries.ITEM.get(id);
        if (disallowsAirInRecipe && item == net.minecraft.world.item.Items.AIR) throw new JsonSyntaxException("Invalid item: " + name);
        return item;
    }

    public static CompoundTag getNBT(JsonElement element) {
        try {
            if (element.isJsonObject()) return TagParser.parseTag(element.toString());
            return TagParser.parseTag(element.getAsString());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new JsonSyntaxException("Invalid NBT Entry: " + e);
        }
    }

    // ---- conditions ----

    private static MapCodec<? extends ICondition> conditionCodec(ConditionSerializer<?> serializer) {
        return MapCodec.assumeMapUnsafe(new Codec<ICondition>() {
            @Override
            public <O> DataResult<Pair<ICondition, O>> decode(DynamicOps<O> ops, O input) {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                try {
                    return DataResult.success(Pair.of(serializer.read(json.getAsJsonObject()), input));
                } catch (RuntimeException e) {
                    return DataResult.error(e::toString);
                }
            }

            @Override
            public <O> DataResult<O> encode(ICondition value, DynamicOps<O> ops, O prefix) {
                JsonObject json = new JsonObject();
                ((ConditionSerializer) serializer).write(json, value);
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
            }
        });
    }

    /** ICondition.codec() for 1.20.1 conditions: the codec of the serializer registered for getID() */
    public static MapCodec<? extends ICondition> conditionCodec(ICondition condition) {
        ResourceLocation id = legacyId(condition);
        MapCodec<? extends ICondition> codec = id == null ? null : CONDITION_CODECS.get(id);
        if (codec == null) throw new IllegalStateException("no 1.20.1 condition serializer for " + condition);
        return codec;
    }

    /** ICondition.test(context, ops) for 1.20.1 conditions, which only have test(context) */
    public static boolean test(ICondition condition, ICondition.IContext context, DynamicOps<?> ops) {
        try {
            Method m = condition.getClass().getMethod("test", ICondition.IContext.class);
            return (Boolean) m.invoke(condition, context);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("1.20.1 condition {} failed", condition, e);
            return false;
        }
    }

    private static ResourceLocation legacyId(ICondition condition) {
        try {
            Method m = condition.getClass().getMethod("getID");
            return (ResourceLocation) m.invoke(condition);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static boolean processConditions(JsonObject json, String member, ICondition.IContext context) {
        return !json.has(member) || processConditions(json.getAsJsonArray(member), context);
    }

    public static boolean processConditions(JsonArray conditions, ICondition.IContext context) {
        for (JsonElement e : conditions) {
            ICondition c = getCondition(e.getAsJsonObject());
            if (!c.test(context, JsonOps.INSTANCE)) return false;
        }
        return true;
    }

    public static ICondition getCondition(JsonObject json) {
        return ICondition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(JsonSyntaxException::new);
    }

    public static JsonObject serialize(ICondition condition) {
        return ICondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow().getAsJsonObject();
    }

    public static JsonArray serialize(ICondition... conditions) {
        JsonArray out = new JsonArray();
        for (ICondition c : conditions) out.add(serialize(c));
        return out;
    }

    static List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add(CONDITIONS.size() + " condition serializers, " + PENDING_INGREDIENTS.size() + " ingredient serializers");
        return out;
    }
}
