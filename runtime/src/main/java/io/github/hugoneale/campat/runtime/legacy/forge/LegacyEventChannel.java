package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.NetworkDirection;
import org.apache.commons.lang3.tuple.Pair;

/** Forge 47's {@code event.EventNetworkChannel} over Forge 52's {@link EventNetworkChannel}. */
@SuppressWarnings({"unchecked", "rawtypes"})
public class LegacyEventChannel {
    private static final Map<ResourceLocation, LegacyEventChannel> CHANNELS = new ConcurrentHashMap<>();
    private final ResourceLocation name;
    private final EventNetworkChannel channel;

    LegacyEventChannel(ResourceLocation name, Supplier<String> version) {
        this.name = name;
        int v = version == null ? 0 : version.get().hashCode();
        this.channel = ChannelBuilder.named(name).networkProtocolVersion(v).optional().eventNetworkChannel();
        CHANNELS.put(name, this);
    }

    public static LegacyEventChannel newEventChannel(ResourceLocation name, Supplier<String> version, Predicate<String> client, Predicate<String> server) {
        return new LegacyEventChannel(name, version);
    }

    public <T extends LegacyNetworkEvent> void addListener(Consumer<T> listener) {
        channel.addListener(e -> listener.accept((T) LegacyNetworkEvent.of((net.minecraftforge.event.network.CustomPayloadEvent) e)));
    }

    public void registerObject(Object object) {
        throw new UnsupportedOperationException("1.20.1 event channel annotation listeners are not supported");
    }

    public EventNetworkChannel unwrap() {
        return channel;
    }

    /** NetworkDirection.buildPacket(Pair(payload, index), channel name) */
    public static LegacyCustomPacket buildPacket(NetworkDirection direction, Pair<FriendlyByteBuf, Integer> data, ResourceLocation name) {
        LegacyEventChannel ch = CHANNELS.get(name);
        if (ch == null) throw new IllegalStateException("no 1.20.1 event channel named " + name);
        Packet<?> packet = direction.buildPacket((net.minecraftforge.network.Channel) ch.channel, data.getLeft());
        return new LegacyCustomPacket(packet, name, data.getLeft(), data.getRight(), direction);
    }

    /** Forge 47's {@code ICustomPacket} */
    public static final class LegacyCustomPacket {
        private final Packet<?> packet;
        private final ResourceLocation name;
        private final FriendlyByteBuf data;
        private final int index;
        private final NetworkDirection direction;

        LegacyCustomPacket(Packet<?> packet, ResourceLocation name, FriendlyByteBuf data, int index, NetworkDirection direction) {
            this.packet = packet;
            this.name = name;
            this.data = data;
            this.index = index;
            this.direction = direction;
        }

        public Packet<?> getThis() {
            return packet;
        }

        public ResourceLocation getName() {
            return name;
        }

        public FriendlyByteBuf getInternalData() {
            return data;
        }

        public int getIndex() {
            return index;
        }

        public NetworkDirection getDirection() {
            return direction;
        }
    }
}
