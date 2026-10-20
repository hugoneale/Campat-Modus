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

    /** DeferredRegister.register(name, supplier) of 1.20.1 code */
    public static RegistryObject register(DeferredRegister register, String name, Supplier supplier) {
        return register.register(name, () -> adapt(supplier.get()));
    }
}
