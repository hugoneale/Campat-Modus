package io.github.hugoneale.campat.runtime.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.neoforged.neoforge.common.crafting.BlockTagIngredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge custom ingredients on Forge. NeoForge patches {@link Ingredient} to wrap an
 * {@link ICustomIngredient}; on Forge the wrapper is an {@link AbstractIngredient} whose serializer,
 * registered in Forge's ingredient serializer registry under the {@link IngredientType}'s id,
 * defers to the type's codecs. Types are resolved lazily so the order in which Forge fires
 * the registry events does not matter.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class CustomIngredients {
    private CustomIngredients() {}

    // NeoForge's own types (NeoForgeMod.*_INGREDIENT_TYPE)
    public static final IngredientType<CompoundIngredient> COMPOUND = new IngredientType<>(CompoundIngredient.CODEC);
    public static final IngredientType<DataComponentIngredient> DATA_COMPONENT = new IngredientType<>(DataComponentIngredient.CODEC);
    public static final IngredientType<DifferenceIngredient> DIFFERENCE = new IngredientType<>(DifferenceIngredient.CODEC);
    public static final IngredientType<IntersectionIngredient> INTERSECTION = new IngredientType<>(IntersectionIngredient.CODEC);
    public static final IngredientType<BlockTagIngredient> BLOCK_TAG = new IngredientType<>(BlockTagIngredient.CODEC);

    public static final net.neoforged.neoforge.registries.DeferredHolder COMPOUND_HOLDER = holder("compound");
    public static final net.neoforged.neoforge.registries.DeferredHolder DATA_COMPONENT_HOLDER = holder("components");
    public static final net.neoforged.neoforge.registries.DeferredHolder DIFFERENCE_HOLDER = holder("difference");
    public static final net.neoforged.neoforge.registries.DeferredHolder INTERSECTION_HOLDER = holder("intersection");
    public static final net.neoforged.neoforge.registries.DeferredHolder BLOCK_TAG_HOLDER = holder("block_tag");

    private static net.neoforged.neoforge.registries.DeferredHolder holder(String path) {
        return net.neoforged.neoforge.registries.DeferredHolder.create((net.minecraft.resources.ResourceKey) NeoForgeRegistries.Keys.INGREDIENT_TYPES, id(path));
    }

    /** registers NeoForge's own ingredient types in NeoForge's ingredient type registry (mod bus) */
    public static void registerBuiltins(net.minecraftforge.eventbus.api.IEventBus modBus) {
        net.neoforged.neoforge.registries.DeferredRegister<IngredientType<?>> dr =
            net.neoforged.neoforge.registries.DeferredRegister.create((net.minecraft.resources.ResourceKey) NeoForgeRegistries.Keys.INGREDIENT_TYPES, "neoforge");
        for (var e : BUILTIN.entrySet()) dr.register(e.getKey().getPath(), () -> e.getValue());
        dr.register(modBus);
    }

    private static final Map<ResourceLocation, IngredientType<?>> BUILTIN = Map.of(
        id("compound"), COMPOUND, id("components"), DATA_COMPONENT, id("difference"), DIFFERENCE,
        id("intersection"), INTERSECTION, id("block_tag"), BLOCK_TAG);

    /** ids declared by NeoForge mods for the ingredient type registry */
    private static final List<ResourceLocation> DECLARED = new ArrayList<>();
    private static final Map<ResourceLocation, Serializer> SERIALIZERS = new ConcurrentHashMap<>();

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoforge", path);
    }

    public static <T extends ICustomIngredient> Supplier<IngredientType<T>> builtin(IngredientType<T> type) {
        return () -> type;
    }

    /** Called by NeoForge DeferredRegisters of the ingredient type registry for each entry name. */
    public static synchronized void declare(ResourceLocation id) {
        if (!DECLARED.contains(id)) DECLARED.add(id);
    }

    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS)) return;
        for (ResourceLocation id : List.copyOf(DECLARED)) event.register(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS, id, () -> serializer(id));
    }

    static Serializer serializer(ResourceLocation id) {
        return SERIALIZERS.computeIfAbsent(id, Serializer::new);
    }

    static IngredientType<?> type(ResourceLocation id) {
        IngredientType<?> t = BUILTIN.get(id);
        if (t != null) return t;
        t = (IngredientType<?>) NeoForgeRegistries.INGREDIENT_TYPES.get(id);
        if (t == null) throw new IllegalStateException("Unknown NeoForge ingredient type " + id);
        return t;
    }

    static ResourceLocation idOf(IngredientType<?> type) {
        for (var e : BUILTIN.entrySet()) if (e.getValue() == type) return e.getKey();
        ResourceLocation id = NeoForgeRegistries.INGREDIENT_TYPES.getKey(type);
        if (id == null) throw new IllegalStateException("Unregistered NeoForge ingredient type " + type);
        return id;
    }

    // ---- NeoForge's Ingredient patch ----

    public static Ingredient toVanilla(ICustomIngredient custom) {
        return new Adapter(custom);
    }

    public static ICustomIngredient getCustomIngredient(Ingredient ingredient) {
        return ingredient instanceof Adapter a ? a.custom : null;
    }

    public static boolean isCustom(Ingredient ingredient) {
        return ingredient instanceof Adapter;
    }

    public static boolean hasNoItems(Ingredient ingredient) {
        return ingredient.isEmpty();
    }

    public static Codec<List<Ingredient>> listCodecNonEmpty() {
        return IngredientCodecs.LIST_CODEC_NONEMPTY;
    }

    public static Codec<List<Ingredient>> listCodec() {
        return Ingredient.CODEC.listOf();
    }

    public static MapCodec<Ingredient> mapCodecNonEmpty() {
        return IngredientCodecs.MAP_CODEC_NONEMPTY;
    }

    private static java.lang.reflect.Field valuesField;

    /** NeoForge exposes the vanilla values array; custom ingredients have none */
    public static Ingredient.Value[] getValues(Ingredient ingredient) {
        try {
            if (valuesField == null) {
                valuesField = Ingredient.class.getDeclaredField("values");
                valuesField.setAccessible(true);
            }
            Object v = valuesField.get(ingredient);
            return v == null ? new Ingredient.Value[0] : (Ingredient.Value[]) v;
        } catch (ReflectiveOperationException e) {
            return new Ingredient.Value[0];
        }
    }

    /** Ingredient wrapping a NeoForge custom ingredient. */
    public static final class Adapter extends AbstractIngredient {
        final ICustomIngredient custom;

        public Adapter(ICustomIngredient custom) {
            super(java.util.stream.Stream.empty());
            this.custom = custom;
        }

        public ICustomIngredient custom() {
            return custom;
        }

        @Override
        public boolean test(ItemStack stack) {
            return stack != null && custom.test(stack);
        }

        @Override
        public ItemStack[] getItems() {
            return custom.getItems().toArray(ItemStack[]::new);
        }

        @Override
        public boolean isEmpty() {
            return getItems().length == 0;
        }

        @Override
        public boolean isSimple() {
            return custom.isSimple();
        }

        @Override
        public IIngredientSerializer<? extends Ingredient> serializer() {
            return CustomIngredients.serializer(idOf(custom.getType()));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Adapter a && a.custom.equals(custom);
        }

        @Override
        public int hashCode() {
            return custom.hashCode();
        }
    }

    /** Forge serializer backed by a NeoForge IngredientType's codecs. */
    public static final class Serializer implements IIngredientSerializer<Adapter> {
        private final ResourceLocation id;
        private MapCodec<Adapter> codec;

        Serializer(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public MapCodec<? extends Adapter> codec() {
            if (codec == null) {
                MapCodec<ICustomIngredient> c = (MapCodec<ICustomIngredient>) (MapCodec) type(id).codec();
                codec = c.xmap(Adapter::new, a -> a.custom);
            }
            return codec;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf, Adapter value) {
            ((net.minecraft.network.codec.StreamCodec) type(id).streamCodec()).encode(buf, value.custom);
        }

        @Override
        public Adapter read(RegistryFriendlyByteBuf buf) {
            return new Adapter((ICustomIngredient) ((net.minecraft.network.codec.StreamCodec) type(id).streamCodec()).decode(buf));
        }
    }
}
