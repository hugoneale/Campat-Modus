package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Forge 47's {@code simple.SimpleChannel} over Forge 52's {@link SimpleChannel}. */
@SuppressWarnings({"unchecked", "rawtypes"})
public class LegacySimpleChannel {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("campat");
    private final SimpleChannel channel;
    /** Forge 47 let a class be registered twice (the later index won); Forge 52 refuses */
    private final java.util.Set<Class<?>> registered = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public LegacySimpleChannel(ResourceLocation name, Supplier<String> version) {
        int v = version == null ? 0 : version.get().hashCode() & 0x7fffffff;
        this.channel = ChannelBuilder.named(name).networkProtocolVersion(v).optional().simpleChannel();
    }

    public SimpleChannel unwrap() {
        return channel;
    }

    public <M> LegacyMessageHandler<M> registerMessage(int index, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
                                    BiConsumer<M, Supplier<LegacyNetworkContext>> handler) {
        return registerMessage(index, type, encoder, decoder, handler, Optional.empty());
    }

    public <M> LegacyMessageHandler<M> registerMessage(int index, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
                                    BiConsumer<M, Supplier<LegacyNetworkContext>> handler, Optional<NetworkDirection> direction) {
        if (!registered.add(type)) {
            LOGGER.warn("{} registered twice on channel {}; keeping the first registration", type.getName(), channel.getName());
            return new LegacyMessageHandler<>(type, index);
        }
        SimpleChannel.MessageBuilder b = direction.isPresent() ? channel.messageBuilder(type, index, (NetworkDirection) direction.get()) : channel.messageBuilder(type, index);
        b.encoder((BiConsumer) encoder).decoder((Function) decoder)
            .consumerNetworkThread((BiConsumer<M, net.minecraftforge.event.network.CustomPayloadEvent.Context>) (msg, ctx) -> {
                LegacyNetworkContext legacy = new LegacyNetworkContext(ctx);
                handler.accept(msg, () -> legacy);
            })
            .add();
        return new LegacyMessageHandler<>(type, index);
    }

    public <M> MessageBuilder<M> messageBuilder(Class<M> type, int id) {
        return new MessageBuilder<>(this, type, id, null);
    }

    public <M> MessageBuilder<M> messageBuilder(Class<M> type, int id, NetworkDirection direction) {
        return new MessageBuilder<>(this, type, id, direction);
    }

    public <M> void sendToServer(M message) {
        channel.send(message, PacketDistributor.SERVER.noArg());
    }

    public <M> void send(PacketDistributor.PacketTarget target, M message) {
        channel.send(message, target);
    }

    public <M> void sendTo(M message, Connection connection, NetworkDirection direction) {
        channel.send(message, connection);
    }

    public <M> void reply(M message, LegacyNetworkContext context) {
        channel.reply(message, context.unwrap());
    }

    public boolean isRemotePresent(Connection connection) {
        return channel.isRemotePresent(connection);
    }

    /** Forge 47's {@code SimpleChannel.MessageBuilder} */
    public static class MessageBuilder<M> {
        private final LegacySimpleChannel channel;
        private final Class<M> type;
        private final int id;
        private final NetworkDirection direction;
        private BiConsumer<M, FriendlyByteBuf> encoder = (m, b) -> {};
        private Function<FriendlyByteBuf, M> decoder;
        private BiConsumer<M, Supplier<LegacyNetworkContext>> consumer;
        private boolean mainThread;

        MessageBuilder(LegacySimpleChannel channel, Class<M> type, int id, NetworkDirection direction) {
            this.channel = channel;
            this.type = type;
            this.id = id;
            this.direction = direction;
        }

        public MessageBuilder<M> encoder(BiConsumer<M, FriendlyByteBuf> encoder) {
            this.encoder = encoder;
            return this;
        }

        public MessageBuilder<M> decoder(Function<FriendlyByteBuf, M> decoder) {
            this.decoder = decoder;
            return this;
        }

        public MessageBuilder<M> consumer(BiConsumer<M, Supplier<LegacyNetworkContext>> consumer) {
            this.consumer = consumer;
            return this;
        }

        public MessageBuilder<M> consumerNetworkThread(BiConsumer<M, Supplier<LegacyNetworkContext>> consumer) {
            this.consumer = consumer;
            return this;
        }

        public MessageBuilder<M> consumerMainThread(BiConsumer<M, Supplier<LegacyNetworkContext>> consumer) {
            this.consumer = consumer;
            this.mainThread = true;
            return this;
        }

        // Forge 47 login-phase messages: Forge 52 negotiates channels during configuration, so the
        // login index and login packet lists are accepted and not sent
        public MessageBuilder<M> loginIndex(Function<M, Integer> getter, BiConsumer<M, Integer> setter) {
            return this;
        }

        public MessageBuilder<M> markAsLoginPacket() {
            return this;
        }

        public MessageBuilder<M> noResponse() {
            return this;
        }

        public MessageBuilder<M> buildLoginPacketList(Function<Boolean, java.util.List> packets) {
            return this;
        }

        public void add() {
            BiConsumer<M, Supplier<LegacyNetworkContext>> handler = consumer == null ? (m, c) -> {} : consumer;
            BiConsumer<M, Supplier<LegacyNetworkContext>> h = mainThread
                ? (m, c) -> { c.get().enqueueWork(() -> handler.accept(m, c)); c.get().setPacketHandled(true); }
                : handler;
            channel.registerMessage(id, type, encoder, decoder, h, Optional.ofNullable(direction));
        }
    }
}
