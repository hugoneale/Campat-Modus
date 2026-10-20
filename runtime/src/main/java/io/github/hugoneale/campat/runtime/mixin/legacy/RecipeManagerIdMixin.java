package io.github.hugoneale.campat.runtime.mixin.legacy;

import com.google.gson.JsonObject;
import io.github.hugoneale.campat.runtime.legacy.LegacyRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.20.1 recipe serializers receive the recipe id; publish it while a recipe is parsed. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerIdMixin {
    @Inject(method = "fromJson", at = @At("HEAD"))
    private static void campat$enter(ResourceLocation id, JsonObject json, HolderLookup.Provider registries, CallbackInfoReturnable<RecipeHolder<?>> cir) {
        LegacyRecipes.setCurrentId(id);
    }

    @Inject(method = "fromJson", at = @At("RETURN"))
    private static void campat$exit(ResourceLocation id, JsonObject json, HolderLookup.Provider registries, CallbackInfoReturnable<RecipeHolder<?>> cir) {
        LegacyRecipes.setCurrentId(null);
    }
}
