package io.github.hugoneale.campat.runtime.capability;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Two way bridge between NeoForge's capabilities and Forge's for the shared handler types (item,
 * fluid and energy handlers use the same interfaces on both). Forge mods can query NeoForge mods'
 * block entities, entities and stacks, and NeoForge mods see Forge mods' capabilities.
 */
public final class CapabilityInterop {
    /** Set while one side is being asked on behalf of the other, so the bridges never recurse. */
    private static final ThreadLocal<Boolean> BRIDGING = ThreadLocal.withInitial(() -> false);

    private CapabilityInterop() {}

    // ---- NeoForge query -> Forge capability (registered as the lowest priority provider) ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerFallbacks(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, (BlockEntityType) type, (be, side) -> forge((BlockEntity) be, ForgeCapabilities.ITEM_HANDLER, (Direction) side));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, (BlockEntityType) type, (be, side) -> forge((BlockEntity) be, ForgeCapabilities.FLUID_HANDLER, (Direction) side));
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, (BlockEntityType) type, (be, side) -> forge((BlockEntity) be, ForgeCapabilities.ENERGY, (Direction) side));
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            event.registerEntity(Capabilities.ItemHandler.ENTITY, (EntityType) type, (e, ctx) -> forge((Entity) e, ForgeCapabilities.ITEM_HANDLER, null));
            event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, (EntityType) type, (e, side) -> forge((Entity) e, ForgeCapabilities.ITEM_HANDLER, (Direction) side));
            event.registerEntity(Capabilities.FluidHandler.ENTITY, (EntityType) type, (e, side) -> forge((Entity) e, ForgeCapabilities.FLUID_HANDLER, (Direction) side));
            event.registerEntity(Capabilities.EnergyStorage.ENTITY, (EntityType) type, (e, side) -> forge((Entity) e, ForgeCapabilities.ENERGY, (Direction) side));
        }
        var items = BuiltInRegistries.ITEM.stream().toArray(net.minecraft.world.item.Item[]::new);
        event.registerItem(Capabilities.ItemHandler.ITEM, (stack, ctx) -> forge(stack, ForgeCapabilities.ITEM_HANDLER, null), items);
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> forge(stack, ForgeCapabilities.FLUID_HANDLER_ITEM, null), items);
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> forge(stack, ForgeCapabilities.ENERGY, null), items);
    }

    @Nullable
    private static <T> T forge(net.minecraftforge.common.capabilities.ICapabilityProvider provider, Capability<T> cap, @Nullable Direction side) {
        if (BRIDGING.get()) return null;
        BRIDGING.set(true);
        try {
            return provider.getCapability(cap, side).resolve().orElse(null);
        } finally {
            BRIDGING.set(false);
        }
    }

    // ---- Forge query -> NeoForge capability (called from the CapabilityProvider mixin) ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> LazyOptional<T> neo(Object provider, Capability<T> cap, @Nullable Direction side) {
        if (BRIDGING.get()) return LazyOptional.empty();
        Object result = null;
        BRIDGING.set(true);
        try {
            if (provider instanceof BlockEntity be && be.getLevel() != null) {
                BlockCapability neoCap = blockCap(cap);
                if (neoCap != null) result = neoCap.getCapability(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, side);
            } else if (provider instanceof Entity e) {
                EntityCapability neoCap = cap == ForgeCapabilities.ITEM_HANDLER ? (side == null ? Capabilities.ItemHandler.ENTITY : Capabilities.ItemHandler.ENTITY_AUTOMATION)
                    : cap == ForgeCapabilities.FLUID_HANDLER ? Capabilities.FluidHandler.ENTITY
                    : cap == ForgeCapabilities.ENERGY ? Capabilities.EnergyStorage.ENTITY : null;
                if (neoCap != null) result = neoCap.getCapability(e, neoCap.contextClass() == Void.class ? null : side);
            } else if (provider instanceof ItemStack stack) {
                ItemCapability neoCap = cap == ForgeCapabilities.ITEM_HANDLER ? Capabilities.ItemHandler.ITEM
                    : cap == ForgeCapabilities.FLUID_HANDLER_ITEM ? Capabilities.FluidHandler.ITEM
                    : cap == ForgeCapabilities.ENERGY ? Capabilities.EnergyStorage.ITEM : null;
                if (neoCap != null) result = neoCap.getCapability(stack, null);
            }
        } finally {
            BRIDGING.set(false);
        }
        if (result == null) return LazyOptional.empty();
        Object r = result;
        return LazyOptional.of(() -> (T) r);
    }

    @Nullable
    private static BlockCapability<?, Direction> blockCap(Capability<?> cap) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return Capabilities.ItemHandler.BLOCK;
        if (cap == ForgeCapabilities.FLUID_HANDLER) return Capabilities.FluidHandler.BLOCK;
        if (cap == ForgeCapabilities.ENERGY) return Capabilities.EnergyStorage.BLOCK;
        return null;
    }
}
