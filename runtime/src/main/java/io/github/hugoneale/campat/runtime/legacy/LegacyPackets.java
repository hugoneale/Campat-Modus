package io.github.hugoneale.campat.runtime.legacy;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;

/** 1.20.1 packets wrote themselves ({@code write(FriendlyByteBuf)}); 1.21.1 packets have a STREAM_CODEC. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyPackets {
    private LegacyPackets() {}

    private static final Map<Class<?>, StreamCodec> CODECS = new ConcurrentHashMap<>();

    public static void write(Packet<?> packet, FriendlyByteBuf buf) {
        StreamCodec codec = CODECS.computeIfAbsent(packet.getClass(), c -> {
            try {
                Field f = c.getField("STREAM_CODEC");
                return (StreamCodec) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no STREAM_CODEC on " + c.getName(), e);
            }
        });
        codec.encode(LegacyBufs.registryBuf(buf), packet);
    }
}
