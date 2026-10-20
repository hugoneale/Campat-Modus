package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** 1.20.1 particle types: a Codec plus a {@code ParticleOptions.Deserializer}; 1.21.1 wants a MapCodec and a StreamCodec. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyParticles {
    private LegacyParticles() {}

    /** {@code ParticleOptions.Deserializer} */
    public interface Deserializer<T extends ParticleOptions> {
        T fromCommand(ParticleType<T> type, StringReader reader) throws CommandSyntaxException;

        T fromNetwork(ParticleType<T> type, FriendlyByteBuf buf);
    }

    private static final ThreadLocal<Deserializer<?>> PENDING = new ThreadLocal<>();
    private static final Map<ParticleType<?>, Deserializer<?>> DESERIALIZERS = new WeakHashMap<>();

    static void pending(Deserializer<?> d) {
        PENDING.set(d);
    }

    /** called at the end of every ParticleType constructor (mixin) */
    public static void constructed(ParticleType<?> type) {
        Deserializer<?> d = PENDING.get();
        if (d == null) return;
        PENDING.remove();
        synchronized (DESERIALIZERS) {
            DESERIALIZERS.put(type, d);
        }
    }

    public static MapCodec codec(ParticleType type) {
        for (Method m : type.getClass().getMethods()) {
            if ((m.getName().equals("codec") || m.getName().equals("m_7652_")) && m.getParameterCount() == 0 && Codec.class.isAssignableFrom(m.getReturnType())) {
                try {
                    return MapCodec.assumeMapUnsafe((Codec) m.invoke(type));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("1.20.1 particle type without codec: " + type);
    }

    public static StreamCodec streamCodec(ParticleType type) {
        return new StreamCodec<RegistryFriendlyByteBuf, ParticleOptions>() {
            @Override
            public ParticleOptions decode(RegistryFriendlyByteBuf buf) {
                Deserializer d;
                synchronized (DESERIALIZERS) {
                    d = DESERIALIZERS.get(type);
                }
                if (d == null) throw new IllegalStateException("no 1.20.1 deserializer for " + type);
                return d.fromNetwork(type, buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ParticleOptions options) {
                for (String name : new String[]{"writeToNetwork", "m_7711_"}) {
                    try {
                        Method m = options.getClass().getMethod(name, FriendlyByteBuf.class);
                        m.invoke(options, buf);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                throw new IllegalStateException("1.20.1 particle options cannot be written: " + options);
            }
        };
    }
}
