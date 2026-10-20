package net.neoforged.neoforge.fluids;

import com.mojang.serialization.Codec;
import io.github.hugoneale.campat.runtime.bridge.FluidStackBridge;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/** NeoForge's immutable fluid stack wrapper (for data components), over Forge's FluidStack. */
public class SimpleFluidContent implements DataComponentHolder {
    public static final SimpleFluidContent EMPTY = new SimpleFluidContent(FluidStack.EMPTY);
    public static final Codec<SimpleFluidContent> CODEC = FluidStackBridge.OPTIONAL_CODEC.xmap(SimpleFluidContent::new, c -> c.fluidStack);
    public static final StreamCodec<RegistryFriendlyByteBuf, SimpleFluidContent> STREAM_CODEC =
        FluidStackBridge.OPTIONAL_STREAM_CODEC.map(SimpleFluidContent::new, c -> c.fluidStack);

    private final FluidStack fluidStack;

    private SimpleFluidContent(FluidStack stack) {
        this.fluidStack = stack;
    }

    public static SimpleFluidContent copyOf(FluidStack stack) {
        return stack.isEmpty() ? EMPTY : new SimpleFluidContent(stack.copy());
    }

    public FluidStack copy() {
        return fluidStack.copy();
    }

    public boolean isEmpty() {
        return fluidStack.isEmpty();
    }

    public Fluid getFluid() {
        return fluidStack.getFluid();
    }

    public Holder<Fluid> getFluidHolder() {
        return FluidStackBridge.getFluidHolder(fluidStack);
    }

    public boolean is(TagKey<Fluid> tag) {
        return FluidStackBridge.is(fluidStack, tag);
    }

    public boolean is(Fluid fluid) {
        return FluidStackBridge.is(fluidStack, fluid);
    }

    public boolean is(Predicate<Holder<Fluid>> predicate) {
        return predicate.test(getFluidHolder());
    }

    public boolean is(Holder<Fluid> holder) {
        return FluidStackBridge.is(fluidStack, holder);
    }

    public boolean is(HolderSet<Fluid> set) {
        return set.contains(getFluidHolder());
    }

    public int getAmount() {
        return fluidStack.getAmount();
    }

    public FluidType getFluidType() {
        return fluidStack.getFluid().getFluidType();
    }

    public boolean is(FluidType type) {
        return getFluidType() == type;
    }

    public boolean matches(FluidStack other) {
        return FluidStackBridge.matches(fluidStack, other);
    }

    public boolean isSameFluid(FluidStack other) {
        return FluidStackBridge.isSameFluid(fluidStack, other);
    }

    public boolean isSameFluidSameComponents(FluidStack other) {
        return FluidStackBridge.isSameFluidSameComponents(fluidStack, other);
    }

    public boolean isSameFluidSameComponents(SimpleFluidContent other) {
        return isSameFluidSameComponents(other.fluidStack);
    }

    @Override
    public DataComponentMap getComponents() {
        return FluidStackBridge.getComponents(fluidStack);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SimpleFluidContent c && FluidStackBridge.matches(fluidStack, c.fluidStack);
    }

    @Override
    public int hashCode() {
        return FluidStackBridge.hashFluidAndComponents(fluidStack) * 31 + fluidStack.getAmount();
    }
}
