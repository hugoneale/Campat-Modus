package campattest;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class Network {
    record Ping(String message) implements CustomPacketPayload {
        static final Type<Ping> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "ping"));
        static final StreamCodec<ByteBuf, Ping> CODEC = ByteBufCodecs.STRING_UTF8.map(Ping::new, Ping::message);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    record Pong(String message, int number) implements CustomPacketPayload {
        static final Type<Pong> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "pong"));
        static final StreamCodec<ByteBuf, Pong> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Pong::message, ByteBufCodecs.VAR_INT, Pong::number, Pong::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void setup(IEventBus modBus) {
        modBus.addListener(Network::register);
        T.pass("network.register");
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        T.pass("network.event");
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Ping.TYPE, Ping.CODEC, Network::onPing);
        r.playToServer(Pong.TYPE, Pong.CODEC, Network::onPong);
    }

    static void greet(Player player) {
        PacketDistributor.sendToPlayer((ServerPlayer) player, new Ping("hello"));
        T.pass("network.send");
    }

    private static void onPing(Ping ping, IPayloadContext ctx) {
        T.check("network.client.receive", ping.message().equals("hello") && ctx.flow().isClientbound(), ping);
        T.check("network.client.mainthread", net.minecraft.client.Minecraft.getInstance().isSameThread(), Thread.currentThread());
        ctx.reply(new Pong("pong", 7));
    }

    private static void onPong(Pong pong, IPayloadContext ctx) {
        T.check("network.server.receive", pong.number() == 7 && ctx.player() instanceof ServerPlayer, pong);
        ctx.player().getServer().execute(() -> T.pass("network.roundtrip"));
    }
}
