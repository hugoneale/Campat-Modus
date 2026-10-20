package io.github.hugoneale.campat.runtime.legacy;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;

/** 1.20.1 entity data serializers wrote / read themselves; 1.21.1 ones expose a StreamCodec. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyDataSerializers {
    private LegacyDataSerializers() {}

    /** EntityDataSerializer.codec() for 1.20.1 implementations (write / read only) */
    public static StreamCodec codec(EntityDataSerializer serializer) {
        Method write = find(serializer.getClass(), "write", 2), read = find(serializer.getClass(), "read", 1);
        return new StreamCodec<RegistryFriendlyByteBuf, Object>() {
            @Override
            public Object decode(RegistryFriendlyByteBuf buf) {
                try {
                    return read.invoke(serializer, buf);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Object value) {
                try {
                    write.invoke(serializer, buf, value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private static Method find(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) {
            if ((m.getName().equals(name) || m.getName().equals(name.equals("write") ? "m_6856_" : "m_6709_")) && m.getParameterCount() == params
                && m.getParameterTypes()[0] == FriendlyByteBuf.class) return m;
        }
        throw new IllegalStateException("1.20.1 entity data serializer without " + name + ": " + c.getName());
    }

    public static void write(EntityDataSerializer serializer, FriendlyByteBuf buf, Object value) {
        ((StreamCodec) serializer.codec()).encode(LegacyBufs.registryBuf(buf), value);
    }

    public static Object read(EntityDataSerializer serializer, FriendlyByteBuf buf) {
        return ((StreamCodec) serializer.codec()).decode(LegacyBufs.registryBuf(buf));
    }

    public static EntityDataSerializer simple(LegacyBufs.Writer writer, LegacyBufs.Reader reader) {
        return EntityDataSerializer.forValueType(StreamCodec.of((buf, v) -> writer.accept((FriendlyByteBuf) buf, v), buf -> reader.apply((FriendlyByteBuf) buf)));
    }

    public static EntityDataSerializer optional(LegacyBufs.Writer writer, LegacyBufs.Reader reader) {
        return EntityDataSerializer.forValueType(StreamCodec.of(
            (buf, v) -> LegacyBufs.writeOptional((FriendlyByteBuf) buf, (Optional) v, writer),
            buf -> LegacyBufs.readOptional((FriendlyByteBuf) buf, reader)));
    }

    public static EntityDataSerializer simpleEnum(Class enumClass) {
        LegacyBufs.Writer<Object> w = (buf, v) -> buf.writeEnum((Enum) v);
        LegacyBufs.Reader<Object> r = buf -> buf.readEnum(enumClass);
        return simple(w, r);
    }

    public static EntityDataSerializer simpleId(IdMap map) {
        LegacyBufs.Writer<Object> w = (buf, v) -> buf.writeVarInt(map.getIdOrThrow(v));
        LegacyBufs.Reader<Object> r = buf -> map.byIdOrThrow(buf.readVarInt());
        return simple(w, r);
    }
}
