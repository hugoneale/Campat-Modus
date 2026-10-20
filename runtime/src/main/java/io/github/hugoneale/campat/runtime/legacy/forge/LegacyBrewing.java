package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;

/**
 * Forge 47's static {@code BrewingRecipeRegistry}. Forge 52 collects brewing recipes with
 * {@link BrewingRecipeRegisterEvent} whenever potion brewing is (re)built; recipes added here are
 * added to every such event.
 */
public final class LegacyBrewing {
    private LegacyBrewing() {}

    private static final List<IBrewingRecipe> RECIPES = new ArrayList<>();
    private static boolean listening;

    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        MinecraftForge.EVENT_BUS.addListener((BrewingRecipeRegisterEvent e) -> {
            synchronized (RECIPES) {
                for (IBrewingRecipe r : RECIPES) e.addRecipe(r);
            }
        });
    }

    public static boolean addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
        return addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public static boolean addRecipe(IBrewingRecipe recipe) {
        listen();
        synchronized (RECIPES) {
            RECIPES.add(recipe);
        }
        return true;
    }

    public static List<IBrewingRecipe> getRecipes() {
        synchronized (RECIPES) {
            return List.copyOf(RECIPES);
        }
    }

    public static ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || input.getCount() != 1 || ingredient.isEmpty()) return ItemStack.EMPTY;
        for (IBrewingRecipe r : getRecipes()) {
            ItemStack out = r.getOutput(input, ingredient);
            if (!out.isEmpty()) return out;
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasOutput(ItemStack input, ItemStack ingredient) {
        return !getOutput(input, ingredient).isEmpty();
    }

    public static boolean isValidIngredient(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (IBrewingRecipe r : getRecipes()) if (r.isIngredient(stack)) return true;
        return false;
    }

    public static boolean isValidInput(ItemStack stack) {
        if (stack.getCount() != 1) return false;
        for (IBrewingRecipe r : getRecipes()) if (r.isInput(stack)) return true;
        return false;
    }

    public static boolean canBrew(NonNullList<ItemStack> inputs, ItemStack ingredient, int[] inputIndexes) {
        if (ingredient.isEmpty()) return false;
        for (int i : inputIndexes) if (hasOutput(inputs.get(i), ingredient)) return true;
        return false;
    }

    public static void brewPotions(NonNullList<ItemStack> inputs, ItemStack ingredient, int[] inputIndexes) {
        for (int i : inputIndexes) {
            ItemStack out = getOutput(inputs.get(i), ingredient);
            if (!out.isEmpty()) inputs.set(i, out);
        }
    }
}
