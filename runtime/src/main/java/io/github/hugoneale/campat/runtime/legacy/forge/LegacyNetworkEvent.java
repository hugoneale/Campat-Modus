package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge 47's {@code NetworkEvent} and its payload subclasses, delivered to listeners of a
 * {@link LegacyEventChannel}. Listeners commonly check the exact subclass.
 */
public class LegacyNetworkEvent extends Event {
    private final FriendlyByteBuf payload;
    private final Supplier<LegacyNetworkContext> source;
    private final int loginIndex;

    public LegacyNetworkEvent() {
        this(null, null, -1);
    }

    LegacyNetworkEvent(FriendlyByteBuf payload, Supplier<LegacyNetworkContext> source, int loginIndex) {
        this.payload = payload;
        this.source = source;
        this.loginIndex = loginIndex;
    }

    public FriendlyByteBuf getPayload() {
        return payload;
    }

    public Supplier<LegacyNetworkContext> getSource() {
        return source;
    }

    public int getLoginIndex() {
        return loginIndex;
    }

    static LegacyNetworkEvent of(CustomPayloadEvent event) {
        LegacyNetworkContext ctx = new LegacyNetworkContext(event.getSource());
        Supplier<LegacyNetworkContext> source = () -> ctx;
        return event.getSource().isServerSide()
            ? new ClientCustomPayloadEvent(event.getPayload(), source, event.getLoginIndex())
            : new ServerCustomPayloadEvent(event.getPayload(), source, event.getLoginIndex());
    }

    /** a payload sent by the client (received on the server) */
    public static class ClientCustomPayloadEvent extends LegacyNetworkEvent {
        public ClientCustomPayloadEvent() {}

        ClientCustomPayloadEvent(FriendlyByteBuf payload, Supplier<LegacyNetworkContext> source, int loginIndex) {
            super(payload, source, loginIndex);
        }
    }

    /** a payload sent by the server (received on the client) */
    public static class ServerCustomPayloadEvent extends LegacyNetworkEvent {
        public ServerCustomPayloadEvent() {}

        ServerCustomPayloadEvent(FriendlyByteBuf payload, Supplier<LegacyNetworkContext> source, int loginIndex) {
            super(payload, source, loginIndex);
        }
    }

    public static class ClientCustomPayloadLoginEvent extends ClientCustomPayloadEvent {
        public ClientCustomPayloadLoginEvent() {}
    }

    public static class ServerCustomPayloadLoginEvent extends ServerCustomPayloadEvent {
        public ServerCustomPayloadLoginEvent() {}
    }

    public static class GatherLoginPayloadsEvent extends Event {
        public GatherLoginPayloadsEvent() {}
    }

    public static class LoginPayloadEvent extends LegacyNetworkEvent {
        public LoginPayloadEvent() {}
    }

    public static class ChannelRegistrationChangeEvent extends LegacyNetworkEvent {
        public ChannelRegistrationChangeEvent() {}
    }
}
