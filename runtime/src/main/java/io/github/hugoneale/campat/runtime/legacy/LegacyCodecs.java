package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.20.1 registries of serializers held {@link Codec}s (biome modifiers, global loot modifiers, ...);
 * 1.21.1 ones hold {@link MapCodec}s. Conversions are cached so a registered value and the codec a
 * modifier returns from {@code codec()} stay the same instance (dispatch codecs look them up).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyCodecs {
    private LegacyCodecs() {}

    private static final Map<Codec<?>, MapCodec<?>> MAP_CODECS = new WeakHashMap<>();

    public static synchronized MapCodec mapCodec(Codec codec) {
        if (codec == null) return null;
        if (codec instanceof MapCodec.MapCodecCodec<?> mc) return mc.codec();
        return MAP_CODECS.computeIfAbsent(codec, c -> MapCodec.assumeMapUnsafe(c));
    }

    // ---- DFU 6 -> 8: dispatch functions answered Codecs, now MapCodecs (same erasure) ----

    private static java.util.function.Function toMap(java.util.function.Function f) {
        return k -> {
            Object c = f.apply(k);
            return c instanceof MapCodec ? c : mapCodec((Codec) c);
        };
    }

    public static Codec dispatch(Codec self, java.util.function.Function type, java.util.function.Function codec) {
        return self.dispatch(type, toMap(codec));
    }

    public static Codec dispatch(Codec self, String key, java.util.function.Function type, java.util.function.Function codec) {
        return self.dispatch(key, type, toMap(codec));
    }

    public static Codec dispatchStable(Codec self, java.util.function.Function type, java.util.function.Function codec) {
        return self.dispatchStable(type, toMap(codec));
    }

    public static MapCodec dispatchMap(Codec self, java.util.function.Function type, java.util.function.Function codec) {
        return self.dispatchMap(type, toMap(codec));
    }

    public static MapCodec dispatchMap(Codec self, String key, java.util.function.Function type, java.util.function.Function codec) {
        return self.dispatchMap(key, type, toMap(codec));
    }

    public static Codec partialDispatch(Codec self, String key, java.util.function.Function type, java.util.function.Function codec) {
        java.util.function.Function f = k -> ((com.mojang.serialization.DataResult) codec.apply(k)).map(c -> c instanceof MapCodec ? c : mapCodec((Codec) c));
        return self.partialDispatch(key, type, f);
    }

    /** ExtraCodecs.validate(codec, check) */
    public static Codec validate(Codec codec, java.util.function.Function check) {
        return codec.validate(check);
    }

    /** 1.20.1's KeyDispatchDataCodec.codec() was a Codec */
    public static Codec dispatchDataCodec(net.minecraft.util.KeyDispatchDataCodec codec) {
        return codec.codec().codec();
    }

    /** ExtraCodecs.COMPONENT */
    public static Codec component() {
        return net.minecraft.network.chat.ComponentSerialization.CODEC;
    }

    /** ExtraCodecs.validate(mapCodec, check) */
    public static MapCodec validate(MapCodec codec, java.util.function.Function check) {
        return codec.validate(check);
    }

    /** ExtraCodecs.lazyInitializedCodec(supplier) */
    public static Codec lazyInitializedCodec(Supplier supplier) {
        return Codec.lazyInitialized(supplier);
    }

    /** constructors that took a Codec and take a MapCodec now */
    public static Object[] typeArgs(Codec codec) {
        return new Object[]{mapCodec(codec)};
    }

    public static net.minecraft.util.KeyDispatchDataCodec keyDispatch(Codec codec) {
        return net.minecraft.util.KeyDispatchDataCodec.of(mapCodec(codec));
    }

    /** casts to Codec in 1.20.1 code: registered serializers are MapCodecs now */
    public static Codec asCodec(Object value) {
        if (value instanceof MapCodec<?> m) return m.codec();
        return (Codec) value;
    }

    public static Codec codec(MapCodec mapCodec) {
        return mapCodec == null ? null : mapCodec.codec();
    }

    /** a registered value: Codecs become MapCodecs */
    static Object adapt(Object value) {
        return value instanceof Codec<?> c && !(value instanceof MapCodec<?>) ? mapCodec(c) : value;
    }

    /** RegisterEvent.register(key, consumer) of 1.20.1 code: the helper adapts registered Codecs */
    public static void register(net.minecraftforge.registries.RegisterEvent event, net.minecraft.resources.ResourceKey key, java.util.function.Consumer consumer) {
        event.register(key, (java.util.function.Consumer<net.minecraftforge.registries.RegisterEvent.RegisterHelper<Object>>) helper -> consumer.accept(new net.minecraftforge.registries.RegisterEvent.RegisterHelper<Object>() {
            @Override
            public void register(net.minecraft.resources.ResourceKey<Object> k, Object value) {
                helper.register(k, named(adapt(value), k.location()));
            }

            @Override
            public void register(net.minecraft.resources.ResourceLocation id, Object value) {
                helper.register(id, named(adapt(value), id));
            }
        }));
    }

    /** RegisterEvent.register(key, id, supplier) of 1.20.1 code */
    public static void register(net.minecraftforge.registries.RegisterEvent event, net.minecraft.resources.ResourceKey key, net.minecraft.resources.ResourceLocation id, Supplier supplier) {
        event.register(key, id, () -> named(adapt(supplier.get()), id));
    }

    /** DeferredRegister.register(name, supplier) of 1.20.1 code */
    public static RegistryObject register(DeferredRegister register, String name, Supplier supplier) {
        return register.register(name, () -> named(adapt(supplier.get()), register.createTagKey(name).location()));
    }

    /** values that took their identity from the registry name in 1.20.1 (a painting's texture) */
    private static Object named(Object value, net.minecraft.resources.ResourceLocation id) {
        if (value instanceof net.minecraft.world.entity.decoration.PaintingVariant p && p.assetId().equals(LegacyCtors.UNNAMED_PAINTING))
            return new net.minecraft.world.entity.decoration.PaintingVariant(p.width(), p.height(), id);
        return value;
    }
}
