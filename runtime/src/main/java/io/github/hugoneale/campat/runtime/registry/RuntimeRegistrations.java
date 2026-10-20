package io.github.hugoneale.campat.runtime.registry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Objects NeoForge registers that Forge does not have, registered under the neoforge namespace. */
public final class RuntimeRegistrations {
    private RuntimeRegistrations() {}

    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.Keys.ATTRIBUTES, "neoforge");

    static {
        ATTRIBUTES.register("creative_flight", () -> new RangedAttribute("neoforge.creative_flight", 0, 0, 1).setSyncable(true));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final net.neoforged.neoforge.registries.DeferredRegister FLUID_INGREDIENT_TYPES =
        net.neoforged.neoforge.registries.DeferredRegister.create((net.minecraft.resources.ResourceKey) net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_INGREDIENT_TYPES, "neoforge");
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "neoforge");

    static {
        LOOT_MODIFIERS.register("add_table", () -> net.neoforged.neoforge.common.loot.AddTableLootModifier.CODEC);
        registerFluid("single", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient.CODEC));
        registerFluid("tag", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.TagFluidIngredient.CODEC));
        registerFluid("empty", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.EmptyFluidIngredient.CODEC));
        registerFluid("compound", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient.CODEC));
        registerFluid("components", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient.CODEC));
        registerFluid("difference", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.DifferenceFluidIngredient.CODEC));
        registerFluid("intersection", () -> new net.neoforged.neoforge.fluids.crafting.FluidIngredientType<>(net.neoforged.neoforge.fluids.crafting.IntersectionFluidIngredient.CODEC));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerFluid(String name, java.util.function.Supplier type) {
        FLUID_INGREDIENT_TYPES.register(name, type);
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        FLUID_INGREDIENT_TYPES.register(modBus);
    }
}
