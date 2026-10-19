package campattest;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge custom ingredients: a registered type used from code and from a data pack recipe. */
final class Ingredients {
    record TwoDiamonds() implements ICustomIngredient {
        static final MapCodec<TwoDiamonds> CODEC = MapCodec.unit(TwoDiamonds::new);

        @Override
        public boolean test(ItemStack stack) {
            return stack.is(Items.DIAMOND) && stack.getCount() >= 2;
        }

        @Override
        public Stream<ItemStack> getItems() {
            return Stream.of(new ItemStack(Items.DIAMOND, 2));
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public IngredientType<?> getType() {
            return TYPE.get();
        }
    }

    static DeferredHolder<IngredientType<?>, IngredientType<TwoDiamonds>> TYPE;

    static void setup(IEventBus modBus) {
        DeferredRegister<IngredientType<?>> types = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, CampatTest.MODID);
        TYPE = types.register("two_diamonds", () -> new IngredientType<>(TwoDiamonds.CODEC));
        types.register(modBus);
    }

    static void verify(MinecraftServer server) {
        T.run("ingredient.code", () -> {
            Ingredient custom = new TwoDiamonds().toVanilla();
            Ingredient compound = CompoundIngredient.of(Ingredient.of(Items.STICK), Ingredient.of(Items.APPLE));
            T.check("ingredient.code", custom.test(new ItemStack(Items.DIAMOND, 2)) && !custom.test(new ItemStack(Items.DIAMOND))
                && custom.getCustomIngredient() instanceof TwoDiamonds && compound.test(new ItemStack(Items.APPLE))
                && !compound.test(new ItemStack(Items.DIRT)), custom);
        });
        T.run("ingredient.datapack", () -> {
            var custom = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "custom_ingredient"));
            var compound = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "compound_ingredient"));
            boolean ok = custom.isPresent() && compound.isPresent();
            if (ok) {
                Recipe<?> c = custom.get().value(), d = compound.get().value();
                ok = c.getIngredients().get(0).test(new ItemStack(Items.DIAMOND, 3))
                    && !c.getIngredients().get(0).test(new ItemStack(Items.DIAMOND, 1))
                    && d.getIngredients().get(0).test(new ItemStack(Items.APPLE));
            }
            T.check("ingredient.datapack", ok, custom.isPresent() + " " + compound.isPresent());
        });
    }
}
