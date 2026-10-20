package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.20.1 recipes worked on {@link Container}s, carried their id and (de)serialized themselves from
 * JSON / the network; 1.21.1 recipes take {@link RecipeInput}s and their serializers expose codecs.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyRecipes {
    private LegacyRecipes() {}

    public static final ResourceLocation UNKNOWN_ID = ResourceLocation.fromNamespaceAndPath("campat", "unknown_recipe");
    private static final ThreadLocal<ResourceLocation> CURRENT_ID = new ThreadLocal<>();

    /** set while {@code RecipeManager.fromJson} parses a recipe (see the mixin) */
    public static void setCurrentId(ResourceLocation id) {
        if (id == null) CURRENT_ID.remove();
        else CURRENT_ID.set(id);
    }

    private static ResourceLocation currentId() {
        ResourceLocation id = CURRENT_ID.get();
        return id == null ? UNKNOWN_ID : id;
    }

    // ---- Container <-> RecipeInput ----

    public static RecipeInput toInput(Container container) {
        if (container == null) return null;
        if (container instanceof LegacyContainer lc) return lc.input;
        if (container instanceof CraftingContainer cc) return cc.asCraftInput();
        if (container.getContainerSize() == 1) return new SingleRecipeInput(container.getItem(0));
        return new ContainerInput(container);
    }

    public static CraftingInput toCraftingInput(CraftingContainer container) {
        if (container == null) return null;
        if (container instanceof LegacyCraftingContainer lc) return lc.input;
        return container.asCraftInput();
    }

    public static Container toContainer(RecipeInput input) {
        if (input == null) return null;
        if (input instanceof ContainerInput ci) return ci.container;
        if (input instanceof CraftingInput c) return new LegacyCraftingContainer(c);
        return new LegacyContainer(input);
    }

    public static CraftingContainer toCraftingContainer(CraftingInput input) {
        return input == null ? null : new LegacyCraftingContainer(input);
    }

    /** a container seen through 1.21.1's RecipeInput */
    public record ContainerInput(Container container) implements RecipeInput {
        @Override
        public ItemStack getItem(int i) {
            return container.getItem(i);
        }

        @Override
        public int size() {
            return container.getContainerSize();
        }
    }

    /** a RecipeInput seen as a 1.20.1 container */
    public static class LegacyContainer extends SimpleContainer {
        final RecipeInput input;

        LegacyContainer(RecipeInput input) {
            super(items(input));
            this.input = input;
        }

        private static ItemStack[] items(RecipeInput input) {
            ItemStack[] items = new ItemStack[input.size()];
            for (int i = 0; i < items.length; i++) items[i] = input.getItem(i);
            return items;
        }
    }

    /** a CraftingInput seen as a 1.20.1 CraftingContainer */
    public static final class LegacyCraftingContainer extends LegacyContainer implements CraftingContainer {
        final CraftingInput input;

        LegacyCraftingContainer(CraftingInput input) {
            super(input);
            this.input = input;
        }

        @Override
        public int getWidth() {
            return input.width();
        }

        @Override
        public int getHeight() {
            return input.height();
        }

        @Override
        public CraftingInput asCraftInput() {
            return input;
        }
    }

    // ---- recipe ids ----

    private static final Map<Class<?>, Method> GET_ID = new ConcurrentHashMap<>();

    public static ResourceLocation getId(Recipe<?> recipe) {
        Method m = GET_ID.computeIfAbsent(recipe.getClass(), c -> {
            for (String name : new String[]{"getId", "m_6423_"}) {
                try {
                    Method x = c.getMethod(name);
                    if (x.getReturnType() == ResourceLocation.class) return x;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return NONE;
        });
        if (m != NONE) {
            try {
                return (ResourceLocation) m.invoke(recipe);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) if (h.value() == recipe) return h.id();
        }
        return UNKNOWN_ID;
    }

    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- serializers: fromJson / fromNetwork / toNetwork -> codec / streamCodec ----

    private static Method find(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != params.length || m.isBridge()) continue;
                boolean ok = true;
                for (int i = 0; i < params.length; i++) ok &= m.getParameterTypes()[i].isAssignableFrom(params[i]) || params[i].isAssignableFrom(m.getParameterTypes()[i]);
                if (ok) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    public static MapCodec serializerCodec(RecipeSerializer serializer) {
        Method fromJson = find(serializer.getClass(), "fromJson", ResourceLocation.class, JsonObject.class);
        Codec codec = new Codec() {
            @Override
            public DataResult decode(DynamicOps ops, Object input) {
                if (fromJson == null) return DataResult.error(() -> serializer + " has no 1.20.1 fromJson");
                JsonElement json = (JsonElement) ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "not a JSON object: " + json);
                try {
                    Object recipe = fromJson.invoke(serializer, currentId(), json.getAsJsonObject());
                    return recipe == null ? DataResult.error(() -> "recipe skipped by its serializer") : DataResult.success(Pair.of(recipe, input));
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable t = e.getCause();
                    return DataResult.error(() -> String.valueOf(t));
                } catch (ReflectiveOperationException e) {
                    return DataResult.error(e::toString);
                }
            }

            @Override
            public DataResult encode(Object value, DynamicOps ops, Object prefix) {
                return DataResult.error(() -> "1.20.1 recipe serializers cannot write JSON");
            }
        };
        return MapCodec.assumeMapUnsafe(codec);
    }

    public static StreamCodec serializerStreamCodec(RecipeSerializer serializer) {
        Method fromNetwork = find(serializer.getClass(), "fromNetwork", ResourceLocation.class, FriendlyByteBuf.class);
        Method toNetwork = find(serializer.getClass(), "toNetwork", FriendlyByteBuf.class, Recipe.class);
        return new StreamCodec<RegistryFriendlyByteBuf, Object>() {
            @Override
            public Object decode(RegistryFriendlyByteBuf buf) {
                try {
                    return fromNetwork.invoke(serializer, UNKNOWN_ID, buf);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot read 1.20.1 recipe of " + serializer, e);
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Object recipe) {
                try {
                    toNetwork.invoke(serializer, buf, recipe);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot write 1.20.1 recipe of " + serializer, e);
                }
            }
        };
    }

    public static NonNullList<ItemStack> emptyRemaining(RecipeInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }
}
