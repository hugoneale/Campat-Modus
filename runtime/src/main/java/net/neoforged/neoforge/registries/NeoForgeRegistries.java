/*
 * Copyright (c) Forge Development LLC and contributors
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import com.mojang.serialization.MapCodec;
import io.github.hugoneale.campat.runtime.registry.LazyRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.StructureModifier;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.holdersets.HolderSetType;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * NeoForge's registries. Registries Forge has as well resolve to Forge's (same contents, Forge
 * namespace keys); NeoForge-only registries are created through Forge's registry system.
 */
public class NeoForgeRegistries {
    public static final Registry<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS = LazyRegistry.of(Keys.ENTITY_DATA_SERIALIZERS);
    public static final Registry<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS = LazyRegistry.of(Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS);
    public static final Registry<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS = LazyRegistry.of(Keys.BIOME_MODIFIER_SERIALIZERS);
    public static final Registry<MapCodec<? extends StructureModifier>> STRUCTURE_MODIFIER_SERIALIZERS = LazyRegistry.of(Keys.STRUCTURE_MODIFIER_SERIALIZERS);
    public static final Registry<FluidType> FLUID_TYPES = LazyRegistry.of(Keys.FLUID_TYPES);
    public static final Registry<HolderSetType> HOLDER_SET_TYPES = LazyRegistry.of(Keys.HOLDER_SET_TYPES);
    public static final Registry<net.neoforged.neoforge.common.crafting.IngredientType<?>> INGREDIENT_TYPES = new RegistryBuilder<>(Keys.INGREDIENT_TYPES).sync(true).create();
    public static final Registry<net.neoforged.neoforge.fluids.crafting.FluidIngredientType<?>> FLUID_INGREDIENT_TYPES = new RegistryBuilder<>(Keys.FLUID_INGREDIENT_TYPES).sync(true).create();
    public static final Registry<MapCodec<? extends ICondition>> CONDITION_SERIALIZERS = LazyRegistry.of(Keys.CONDITION_CODECS);
    public static final Registry<AttachmentType<?>> ATTACHMENT_TYPES = new RegistryBuilder<>(Keys.ATTACHMENT_TYPES).create();

    /** Forces class initialisation so NeoForge-only registries are queued before Forge creates registries. */
    public static void init() {}

    public static final class Keys {
        public static final ResourceKey<Registry<EntityDataSerializer<?>>> ENTITY_DATA_SERIALIZERS = ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS;
        public static final ResourceKey<Registry<MapCodec<? extends IGlobalLootModifier>>> GLOBAL_LOOT_MODIFIER_SERIALIZERS = ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS;
        public static final ResourceKey<Registry<MapCodec<? extends BiomeModifier>>> BIOME_MODIFIER_SERIALIZERS = ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS;
        public static final ResourceKey<Registry<MapCodec<? extends StructureModifier>>> STRUCTURE_MODIFIER_SERIALIZERS = ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS;
        public static final ResourceKey<Registry<FluidType>> FLUID_TYPES = ForgeRegistries.Keys.FLUID_TYPES;
        public static final ResourceKey<Registry<HolderSetType>> HOLDER_SET_TYPES = ForgeRegistries.Keys.HOLDER_SET_TYPES;
        public static final ResourceKey<Registry<net.neoforged.neoforge.common.crafting.IngredientType<?>>> INGREDIENT_TYPES = key("ingredient_serializer");
        public static final ResourceKey<Registry<net.neoforged.neoforge.fluids.crafting.FluidIngredientType<?>>> FLUID_INGREDIENT_TYPES = key("fluid_ingredient_type");
        public static final ResourceKey<Registry<MapCodec<? extends ICondition>>> CONDITION_CODECS = ForgeRegistries.Keys.CONDITION_SERIALIZERS;
        public static final ResourceKey<Registry<AttachmentType<?>>> ATTACHMENT_TYPES = key("attachment_types");
        public static final ResourceKey<Registry<BiomeModifier>> BIOME_MODIFIERS = ForgeRegistries.Keys.BIOME_MODIFIERS;
        public static final ResourceKey<Registry<StructureModifier>> STRUCTURE_MODIFIERS = ForgeRegistries.Keys.STRUCTURE_MODIFIERS;

        private static <T> ResourceKey<Registry<T>> key(String name) {
            return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", name));
        }
    }
}
