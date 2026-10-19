package net.neoforged.neoforge.common;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.holdersets.HolderSetType;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * NeoForge's {@code NeoForgeMod} constants on Forge: lazy holders of the objects Forge registers
 * itself (under the forge namespace), or of the runtime's own registrations where Forge has none.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class NeoForgeMod {
    private NeoForgeMod() {}

    public static final String VERSION_CHECK_CAT = ForgeMod.VERSION_CHECK_CAT;

    private static <T> DeferredHolder<T, T> forge(ResourceKey<? extends Registry<T>> registry, String path) {
        return DeferredHolder.create((ResourceKey) registry, ResourceLocation.fromNamespaceAndPath("forge", path));
    }

    private static <T> DeferredHolder<T, T> neo(ResourceKey<? extends Registry<T>> registry, String path) {
        return DeferredHolder.create((ResourceKey) registry, ResourceLocation.fromNamespaceAndPath("neoforge", path));
    }

    public static final Holder<Attribute> SWIM_SPEED = forge(Registries.ATTRIBUTE, "swim_speed");
    public static final Holder<Attribute> NAMETAG_DISTANCE = forge(Registries.ATTRIBUTE, "nametag_distance");
    /** Registered by the runtime; Forge has no creative flight attribute */
    public static final Holder<Attribute> CREATIVE_FLIGHT = neo(Registries.ATTRIBUTE, "creative_flight");

    public static final DeferredHolder ADD_TABLE_LOOT_MODIFIER_TYPE = forge(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "add_table");
    public static final DeferredHolder NONE_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "none");
    public static final DeferredHolder ADD_FEATURES_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "add_features");
    public static final DeferredHolder REMOVE_FEATURES_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "remove_features");
    public static final DeferredHolder ADD_SPAWNS_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "add_spawns");
    public static final DeferredHolder REMOVE_SPAWNS_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "remove_spawns");
    public static final DeferredHolder ADD_CARVERS_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "add_carvers");
    public static final DeferredHolder REMOVE_CARVERS_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "remove_carvers");
    public static final DeferredHolder ADD_SPAWN_COSTS_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "add_spawn_costs");
    public static final DeferredHolder REMOVE_SPAWN_COSTS_BIOME_MODIFIER_TYPE = forge(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "remove_spawn_costs");
    public static final DeferredHolder NONE_STRUCTURE_MODIFIER_TYPE = forge(ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, "none");
    public static final DeferredHolder ADD_SPAWNS_STRUCTURE_MODIFIER_TYPE = forge(ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, "add_spawns");
    public static final DeferredHolder REMOVE_SPAWNS_STRUCTURE_MODIFIER_TYPE = forge(ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, "remove_spawns");
    public static final DeferredHolder CLEAR_SPAWNS_STRUCTURE_MODIFIER_TYPE = forge(ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, "clear_spawns");

    public static final Holder<HolderSetType> ANY_HOLDER_SET = forge(ForgeRegistries.Keys.HOLDER_SET_TYPES, "any");
    public static final Holder<HolderSetType> AND_HOLDER_SET = forge(ForgeRegistries.Keys.HOLDER_SET_TYPES, "and");
    public static final Holder<HolderSetType> OR_HOLDER_SET = forge(ForgeRegistries.Keys.HOLDER_SET_TYPES, "or");
    public static final Holder<HolderSetType> NOT_HOLDER_SET = forge(ForgeRegistries.Keys.HOLDER_SET_TYPES, "not");

    public static final DeferredHolder COMPOUND_INGREDIENT_TYPE = io.github.hugoneale.campat.runtime.crafting.CustomIngredients.COMPOUND_HOLDER;
    public static final DeferredHolder DATA_COMPONENT_INGREDIENT_TYPE = io.github.hugoneale.campat.runtime.crafting.CustomIngredients.DATA_COMPONENT_HOLDER;
    public static final DeferredHolder DIFFERENCE_INGREDIENT_TYPE = io.github.hugoneale.campat.runtime.crafting.CustomIngredients.DIFFERENCE_HOLDER;
    public static final DeferredHolder INTERSECTION_INGREDIENT_TYPE = io.github.hugoneale.campat.runtime.crafting.CustomIngredients.INTERSECTION_HOLDER;
    public static final DeferredHolder BLOCK_TAG_INGREDIENT = io.github.hugoneale.campat.runtime.crafting.CustomIngredients.BLOCK_TAG_HOLDER;

    public static final DeferredHolder AND_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "and");
    public static final DeferredHolder FALSE_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "false");
    public static final DeferredHolder ITEM_EXISTS_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "item_exists");
    public static final DeferredHolder MOD_LOADED_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "mod_loaded");
    public static final DeferredHolder NOT_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "not");
    public static final DeferredHolder OR_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "or");
    public static final DeferredHolder TAG_EMPTY_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "tag_empty");
    public static final DeferredHolder TRUE_CONDITION = forge(ForgeRegistries.Keys.CONDITION_SERIALIZERS, "true");

    public static final Holder<FluidType> EMPTY_TYPE = forge(ForgeRegistries.Keys.FLUID_TYPES, "empty");
    public static final Holder<FluidType> WATER_TYPE = forge(ForgeRegistries.Keys.FLUID_TYPES, "water");
    public static final Holder<FluidType> LAVA_TYPE = forge(ForgeRegistries.Keys.FLUID_TYPES, "lava");

    public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_EMPTY_MILK = DeferredHolder.create(Registries.SOUND_EVENT, ResourceLocation.withDefaultNamespace("item.bucket.empty_milk"));
    public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_FILL_MILK = DeferredHolder.create(Registries.SOUND_EVENT, ResourceLocation.withDefaultNamespace("item.bucket.fill_milk"));
    public static final DeferredHolder<FluidType, FluidType> MILK_TYPE = DeferredHolder.create(ForgeRegistries.Keys.FLUID_TYPES, ResourceLocation.withDefaultNamespace("milk"));
    public static final DeferredHolder<Fluid, Fluid> MILK = DeferredHolder.create(Registries.FLUID, ResourceLocation.withDefaultNamespace("milk"));
    public static final DeferredHolder<Fluid, Fluid> FLOWING_MILK = DeferredHolder.create(Registries.FLUID, ResourceLocation.withDefaultNamespace("flowing_milk"));

    public static final ResourceKey<DamageType> POISON_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("neoforge", "poison"));
    public static final PermissionNode<Boolean> USE_SELECTORS_PERMISSION = ForgeMod.USE_SELECTORS_PERMISSION;

    private static boolean mergedTooltips, properFilenames;

    public static void enableMilkFluid() {
        ForgeMod.enableMilkFluid();
    }

    public static void enableMergedAttributeTooltips() {
        mergedTooltips = true;
    }

    public static void enableProperFilenameValidation() {
        properFilenames = true;
    }

    public static boolean getProperFilenameValidation() {
        return properFilenames;
    }

    public static boolean shouldMergeAttributeTooltips() {
        return mergedTooltips;
    }

    public static boolean isPRBuild() {
        return false;
    }
}
