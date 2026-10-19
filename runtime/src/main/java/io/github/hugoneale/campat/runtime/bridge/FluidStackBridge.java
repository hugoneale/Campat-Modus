package io.github.hugoneale.campat.runtime.bridge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge's {@code FluidStack} carries data components like an {@code ItemStack}; Forge 1.21.1's
 * keeps an NBT tag. NeoForge-only members are redirected here and the components are stored as a
 * serialized {@link DataComponentPatch} inside the Forge stack's tag, so they survive copies,
 * saving and syncing through Forge's own code.
 */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class FluidStackBridge {
    /** Key inside the Forge fluid stack tag that holds the NeoForge component patch. */
    public static final String COMPONENTS_KEY = "neoforge:components";

    private FluidStackBridge() {}

    // ---- construction ----

    public static FluidStack createFluidStack(Holder<Fluid> fluid, int amount) {
        return new FluidStack(fluid.value(), amount);
    }

    public static FluidStack createFluidStack(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
        FluidStack stack = new FluidStack(fluid.value(), amount);
        writePatch(stack, patch);
        return stack;
    }

    public static FluidStack createFluidStack(Fluid fluid, int amount, DataComponentPatch patch) {
        FluidStack stack = new FluidStack(fluid, amount);
        writePatch(stack, patch);
        return stack;
    }

    // ---- simple members ----

    public static Component getHoverName(FluidStack self) {
        return self.getDisplayName();
    }

    public static String getDescriptionId(FluidStack self) {
        return self.getTranslationKey();
    }

    public static FluidStack copyWithAmount(FluidStack self, int amount) {
        if (self.isEmpty()) return FluidStack.EMPTY;
        return new FluidStack(self, amount);
    }

    public static FluidStack copyAndClear(FluidStack self) {
        if (self.isEmpty()) return FluidStack.EMPTY;
        FluidStack copy = self.copy();
        self.setAmount(0);
        return copy;
    }

    public static Holder<Fluid> getFluidHolder(FluidStack self) {
        return self.getFluid().builtInRegistryHolder();
    }

    public static FluidType getFluidType(FluidStack self) {
        return self.getFluid().getFluidType();
    }

    public static boolean is(FluidStack self, TagKey<Fluid> tag) {
        return self.getFluid().builtInRegistryHolder().is(tag);
    }

    public static boolean is(FluidStack self, Fluid fluid) {
        return self.getFluid() == fluid;
    }

    public static boolean is(FluidStack self, FluidType type) {
        return self.getFluid().getFluidType() == type;
    }

    public static boolean is(FluidStack self, Holder<Fluid> fluid) {
        return self.getFluid() == fluid.value();
    }

    public static void limitSize(FluidStack self, int max) {
        if (!self.isEmpty() && self.getAmount() > max) self.setAmount(max);
    }

    // ---- static comparisons ----

    public static boolean isSameFluid(FluidStack a, FluidStack b) {
        return a.getFluid() == b.getFluid();
    }

    public static boolean isSameFluidSameComponents(FluidStack a, FluidStack b) {
        if (a.getFluid() != b.getFluid()) return false;
        if (a.isEmpty() && b.isEmpty()) return true;
        return Objects.equals(readPatch(a), readPatch(b));
    }

    public static boolean matches(FluidStack a, FluidStack b) {
        if (a == b) return true;
        return a.getAmount() == b.getAmount() && isSameFluidSameComponents(a, b);
    }

    public static int hashFluidAndComponents(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return 31 * stack.getFluid().hashCode() + readPatch(stack).hashCode();
    }

    // ---- components ----

    public static DataComponentPatch getComponentsPatch(FluidStack self) {
        return readPatch(self);
    }

    public static PatchedDataComponentMap getComponents(FluidStack self) {
        return PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, readPatch(self));
    }

    public static Object get(FluidStack self, DataComponentType type) {
        return getComponents(self).get(type);
    }

    public static Object get(FluidStack self, java.util.function.Supplier<? extends DataComponentType> type) {
        return get(self, type.get());
    }

    public static Object getOrDefault(FluidStack self, DataComponentType type, Object fallback) {
        Object v = get(self, type);
        return v != null ? v : fallback;
    }

    public static boolean has(FluidStack self, DataComponentType type) {
        return getComponents(self).has(type);
    }

    public static Object set(FluidStack self, DataComponentType type, @Nullable Object value) {
        PatchedDataComponentMap map = getComponents(self);
        Object old = map.set(type, value);
        writePatch(self, map.asPatch());
        return old;
    }

    public static Object remove(FluidStack self, DataComponentType type) {
        PatchedDataComponentMap map = getComponents(self);
        Object old = map.remove(type);
        writePatch(self, map.asPatch());
        return old;
    }

    public static Object update(FluidStack self, DataComponentType type, Object fallback, UnaryOperator updater) {
        return set(self, type, updater.apply(getOrDefault(self, type, fallback)));
    }

    public static Object update(FluidStack self, DataComponentType type, Object fallback, Object arg, BiFunction updater) {
        return set(self, type, updater.apply(getOrDefault(self, type, fallback), arg));
    }

    public static void applyComponents(FluidStack self, DataComponentPatch patch) {
        PatchedDataComponentMap map = getComponents(self);
        map.applyPatch(patch);
        writePatch(self, map.asPatch());
    }

    public static void applyComponents(FluidStack self, DataComponentMap components) {
        PatchedDataComponentMap map = getComponents(self);
        map.setAll(components);
        writePatch(self, map.asPatch());
    }

    private static DataComponentPatch readPatch(FluidStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(COMPONENTS_KEY)) return DataComponentPatch.EMPTY;
        return DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag.get(COMPONENTS_KEY)).result().orElse(DataComponentPatch.EMPTY);
    }

    private static void writePatch(FluidStack stack, DataComponentPatch patch) {
        if (patch.isEmpty()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(COMPONENTS_KEY);
                if (tag.isEmpty()) stack.setTag(null);
            }
            return;
        }
        DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE, patch).result()
            .ifPresent(t -> stack.getOrCreateTag().put(COMPONENTS_KEY, t));
    }

    // ---- codecs and serialization ----

    private static final Codec<Holder<Fluid>> FLUID_HOLDER = BuiltInRegistries.FLUID.holderByNameCodec()
        .validate(h -> h.is(Fluids.EMPTY.builtInRegistryHolder()) ? DataResult.error(() -> "Fluid must not be minecraft:empty") : DataResult.success(h));

    public static final Codec<FluidStack> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
        FLUID_HOLDER.fieldOf("id").forGetter(FluidStackBridge::getFluidHolder),
        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount),
        DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidStackBridge::readPatch)
    ).apply(i, FluidStackBridge::createFluidStack)));

    public static final Codec<FluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
        .xmap(o -> o.orElse(FluidStack.EMPTY), s -> s.isEmpty() ? Optional.empty() : Optional.of(s));

    public static final Codec<Holder<Fluid>> FLUID_NON_EMPTY_CODEC = FLUID_HOLDER;

    public static Codec<FluidStack> fixedAmountCodec(int amount) {
        return Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
            FLUID_HOLDER.fieldOf("id").forGetter(FluidStackBridge::getFluidHolder),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidStackBridge::readPatch)
        ).apply(i, (f, p) -> createFluidStack(f, amount, p))));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> OPTIONAL_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FluidStack decode(RegistryFriendlyByteBuf buf) {
            int amount = buf.readVarInt();
            if (amount <= 0) return FluidStack.EMPTY;
            Holder<Fluid> fluid = ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.FLUID).decode(buf);
            DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buf);
            return createFluidStack(fluid, amount, patch);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FluidStack stack) {
            if (stack.isEmpty()) {
                buf.writeVarInt(0);
                return;
            }
            buf.writeVarInt(stack.getAmount());
            ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.FLUID).encode(buf, getFluidHolder(stack));
            DataComponentPatch.STREAM_CODEC.encode(buf, readPatch(stack));
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FluidStack decode(RegistryFriendlyByteBuf buf) {
            FluidStack s = OPTIONAL_STREAM_CODEC.decode(buf);
            if (s.isEmpty()) throw new io.netty.handler.codec.DecoderException("Empty FluidStack not allowed");
            return s;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FluidStack stack) {
            if (stack.isEmpty()) throw new io.netty.handler.codec.EncoderException("Empty FluidStack not allowed");
            OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    };

    public static Codec<FluidStack> OPTIONAL_CODEC() {
        return OPTIONAL_CODEC;
    }

    public static Codec<FluidStack> CODEC() {
        return CODEC;
    }

    public static Codec<Holder<Fluid>> FLUID_NON_EMPTY_CODEC() {
        return FLUID_NON_EMPTY_CODEC;
    }

    public static StreamCodec<RegistryFriendlyByteBuf, FluidStack> STREAM_CODEC() {
        return STREAM_CODEC;
    }

    public static StreamCodec<RegistryFriendlyByteBuf, FluidStack> OPTIONAL_STREAM_CODEC() {
        return OPTIONAL_STREAM_CODEC;
    }

    public static Tag save(FluidStack self, HolderLookup.Provider provider) {
        if (self.isEmpty()) throw new IllegalStateException("Cannot encode empty FluidStack");
        return CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), self).getOrThrow();
    }

    public static Tag save(FluidStack self, HolderLookup.Provider provider, Tag prefix) {
        if (self.isEmpty()) throw new IllegalStateException("Cannot encode empty FluidStack");
        return CODEC.encode(self, provider.createSerializationContext(NbtOps.INSTANCE), prefix).getOrThrow();
    }

    public static Tag saveOptional(FluidStack self, HolderLookup.Provider provider) {
        return self.isEmpty() ? new CompoundTag() : save(self, provider);
    }

    public static Optional<FluidStack> parse(HolderLookup.Provider provider, Tag tag) {
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        return CODEC.parse(ops, tag).resultOrPartial(e -> io.github.hugoneale.campat.runtime.CampatModus.LOGGER.error("Tried to load invalid fluid: '{}'", e));
    }

    public static FluidStack parseOptional(HolderLookup.Provider provider, CompoundTag tag) {
        return tag.isEmpty() ? FluidStack.EMPTY : parse(provider, tag).orElse(FluidStack.EMPTY);
    }
}
