package io.github.hugoneale.campat.runtime.legacy;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.NonNullList;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Argument adapters for constructors whose parameters changed between 1.20.1 and 1.21.1 ({@code A}
 * mapping lines). They also serve {@code super(...)} calls of mod subclasses: each takes the old
 * arguments and returns the new ones.
 */
public final class LegacyCtors {
    private LegacyCtors() {}

    /** a painting whose texture is named after its registry id, filled in on registration */
    public static final ResourceLocation UNNAMED_PAINTING = ResourceLocation.fromNamespaceAndPath("campat", "unnamed_painting");

    /** PaintingVariant(width, height): 1.20.1 found the texture by the registry name */
    public static Object[] paintingVariant(int width, int height) {
        return new Object[]{width, height, UNNAMED_PAINTING};
    }

    /** TorchBlock / WallTorchBlock(properties, particle) -> (particle type, properties) */
    public static Object[] torchBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties, net.minecraft.core.particles.ParticleOptions particle) {
        return new Object[]{particle instanceof net.minecraft.core.particles.SimpleParticleType t ? t : net.minecraft.core.particles.ParticleTypes.FLAME, properties};
    }

    /** CustomRecipe(id, category) -> (category) */
    public static Object[] customRecipe(ResourceLocation id, CraftingBookCategory category) {
        return new Object[]{category};
    }

    /** ShapedRecipe(id, group, category, w, h, ingredients, result[, showNotification]) */
    public static Object[] shapedRecipe(ResourceLocation id, String group, CraftingBookCategory category, int w, int h, NonNullList<Ingredient> ingredients, ItemStack result) {
        return new Object[]{group, category, new ShapedRecipePattern(w, h, ingredients, Optional.empty()), result};
    }

    public static Object[] shapedRecipe(ResourceLocation id, String group, CraftingBookCategory category, int w, int h, NonNullList<Ingredient> ingredients, ItemStack result, boolean notify) {
        return new Object[]{group, category, new ShapedRecipePattern(w, h, ingredients, Optional.empty()), result, notify};
    }

    /** ShapelessRecipe(id, group, category, result, ingredients) */
    public static Object[] shapelessRecipe(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        return new Object[]{group, category, result, ingredients};
    }

    /** ParticleType(overrideLimiter, deserializer) -> (overrideLimiter); the deserializer is kept for the stream codec */
    public static Object[] particleType(boolean overrideLimiter, LegacyParticles.Deserializer<?> deserializer) {
        LegacyParticles.pending(deserializer);
        return new Object[]{overrideLimiter};
    }

    /** RecipeProvider(output) -> (output, registries) */
    public static Object[] recipeProvider(PackOutput output) {
        return new Object[]{output, CompletableFuture.completedFuture(LegacyRegistries.provider())};
    }
}
