package io.github.hugoneale.campat.runtime.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * NeoForge's extra Ingredient codecs. Kept apart from {@link CustomIngredients}: NeoForge's ingredient
 * classes use these in their static initialisers, and CustomIngredients builds types from theirs.
 */
public final class IngredientCodecs {
    private IngredientCodecs() {}

    /** NeoForge's Ingredient.LIST_CODEC_NONEMPTY */
    public static final Codec<List<Ingredient>> LIST_CODEC_NONEMPTY = Ingredient.CODEC_NONEMPTY.listOf()
        .validate(l -> l.isEmpty() ? DataResult.error(() -> "Ingredient list cannot be empty") : DataResult.success(l));

    /** NeoForge's Ingredient.MAP_CODEC_NONEMPTY: the ingredient inlined into a map */
    public static final MapCodec<Ingredient> MAP_CODEC_NONEMPTY = Ingredient.VANILLA_MAP_CODEC;
}
