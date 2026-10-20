package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;

/**
 * Minecraft 1.20.1's {@code FriendlyByteBuf.Writer} / {@code Reader} and the collection helpers that
 * took them; 1.21.1 takes {@link StreamEncoder} / {@link StreamDecoder} instead. Mod lambdas are
 * compiled against the renamed interfaces below, which are both.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyBufs {
    private LegacyBufs() {}

    /** {@code FriendlyByteBuf.Writer} */
    @FunctionalInterface
    public interface Writer<T> extends BiConsumer<FriendlyByteBuf, T>, StreamEncoder<FriendlyByteBuf, T> {
        @Override
        default void encode(FriendlyByteBuf buf, T value) {
            accept(buf, value);
        }

        default Writer<Optional<T>> asOptional() {
            return (buf, o) -> writeOptional(buf, o, this);
        }
    }

    /** {@code FriendlyByteBuf.Reader} */
    @FunctionalInterface
    public interface Reader<T> extends Function<FriendlyByteBuf, T>, StreamDecoder<FriendlyByteBuf, T> {
        @Override
        default T decode(FriendlyByteBuf buf) {
            return apply(buf);
        }

        default Reader<Optional<T>> asOptional() {
            return buf -> readOptional(buf, this);
        }
    }

    public static <T> void writeCollection(FriendlyByteBuf buf, Collection<T> c, Writer<T> w) {
        buf.writeVarInt(c.size());
        for (T t : c) w.accept(buf, t);
    }

    public static <T, C extends Collection<T>> C readCollection(FriendlyByteBuf buf, IntFunction<C> factory, Reader<T> r) {
        int n = buf.readVarInt();
        C c = factory.apply(n);
        for (int i = 0; i < n; i++) c.add(r.apply(buf));
        return c;
    }

    public static <T> List<T> readList(FriendlyByteBuf buf, Reader<T> r) {
        return readCollection(buf, ArrayList::new, r);
    }

    public static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> map, Writer<K> kw, Writer<V> vw) {
        buf.writeVarInt(map.size());
        map.forEach((k, v) -> {
            kw.accept(buf, k);
            vw.accept(buf, v);
        });
    }

    public static <K, V, M extends Map<K, V>> M readMap(FriendlyByteBuf buf, IntFunction<M> factory, Reader<K> kr, Reader<V> vr) {
        int n = buf.readVarInt();
        M m = factory.apply(n);
        for (int i = 0; i < n; i++) {
            K k = kr.apply(buf);
            m.put(k, vr.apply(buf));
        }
        return m;
    }

    public static <K, V> Map<K, V> readMap(FriendlyByteBuf buf, Reader<K> kr, Reader<V> vr) {
        return readMap(buf, HashMap::new, kr, vr);
    }

    public static <T> void writeOptional(FriendlyByteBuf buf, Optional<T> o, Writer<T> w) {
        buf.writeBoolean(o.isPresent());
        o.ifPresent(v -> w.accept(buf, v));
    }

    public static <T> Optional<T> readOptional(FriendlyByteBuf buf, Reader<T> r) {
        return buf.readBoolean() ? Optional.of(r.apply(buf)) : Optional.empty();
    }

    public static <T> void writeNullable(FriendlyByteBuf buf, T value, Writer<T> w) {
        buf.writeBoolean(value != null);
        if (value != null) w.accept(buf, value);
    }

    public static <T> T readNullable(FriendlyByteBuf buf, Reader<T> r) {
        return buf.readBoolean() ? r.apply(buf) : null;
    }

    public static <L, R> void writeEither(FriendlyByteBuf buf, Either<L, R> e, Writer<L> lw, Writer<R> rw) {
        e.ifLeft(l -> {
            buf.writeBoolean(true);
            lw.accept(buf, l);
        }).ifRight(r -> {
            buf.writeBoolean(false);
            rw.accept(buf, r);
        });
    }

    public static <L, R> Either<L, R> readEither(FriendlyByteBuf buf, Reader<L> lr, Reader<R> rr) {
        return buf.readBoolean() ? Either.left(lr.apply(buf)) : Either.right(rr.apply(buf));
    }

    public static <T> void writeId(FriendlyByteBuf buf, IdMap<Holder<T>> map, Holder<T> holder, Writer<T> direct) {
        switch (holder.kind()) {
            case REFERENCE -> buf.writeVarInt(map.getIdOrThrow(holder) + 1);
            case DIRECT -> {
                buf.writeVarInt(0);
                direct.accept(buf, holder.value());
            }
        }
    }

    public static <T> Holder<T> readById(FriendlyByteBuf buf, IdMap<Holder<T>> map, Reader<T> direct) {
        int id = buf.readVarInt();
        return id == 0 ? Holder.direct(direct.apply(buf)) : map.byIdOrThrow(id - 1);
    }

    // ---- game profiles ----

    public static void writeGameProfile(FriendlyByteBuf buf, com.mojang.authlib.GameProfile profile) {
        net.minecraft.network.codec.ByteBufCodecs.GAME_PROFILE.encode(buf, profile);
    }

    public static com.mojang.authlib.GameProfile readGameProfile(FriendlyByteBuf buf) {
        return net.minecraft.network.codec.ByteBufCodecs.GAME_PROFILE.decode(buf);
    }

    public static void writeGameProfileProperties(FriendlyByteBuf buf, com.mojang.authlib.properties.PropertyMap properties) {
        net.minecraft.network.codec.ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buf, properties);
    }

    public static com.mojang.authlib.properties.PropertyMap readGameProfileProperties(FriendlyByteBuf buf) {
        return net.minecraft.network.codec.ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(buf);
    }

    // ---- chat components: 1.21.1 needs a registry aware buffer ----

    public static RegistryFriendlyByteBuf registryBuf(FriendlyByteBuf buf) {
        return buf instanceof RegistryFriendlyByteBuf r ? r : new RegistryFriendlyByteBuf(buf, LegacyRegistries.access());
    }

    public static FriendlyByteBuf writeComponent(FriendlyByteBuf buf, Component component) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(registryBuf(buf), component);
        return buf;
    }

    public static Component readComponent(FriendlyByteBuf buf) {
        return ComponentSerialization.TRUSTED_STREAM_CODEC.decode(registryBuf(buf));
    }
}
