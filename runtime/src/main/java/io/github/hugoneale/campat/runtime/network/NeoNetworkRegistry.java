package io.github.hugoneale.campat.runtime.network;

import io.github.hugoneale.campat.runtime.CampatModus;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge style payload registry. Payload types are recognised by vanilla's custom payload codec
 * through a hook in Forge's codec lookup, so NeoForge mods can send them with
 * {@code PacketDistributor}, as raw {@code ClientboundCustomPayloadPacket}s or in bundles.
 */
public final class NeoNetworkRegistry {
    public record Registration(CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec, IPayloadHandler<?> handler,
                               List<ConnectionProtocol> protocols, Optional<PacketFlow> flow, String version, boolean optional) {}

    private static final Map<ResourceLocation, Registration> PAYLOADS = new ConcurrentHashMap<>();

    private NeoNetworkRegistry() {}

    public static <T extends CustomPacketPayload, B extends FriendlyByteBuf> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec,
            IPayloadHandler<T> handler, List<ConnectionProtocol> protocols, Optional<PacketFlow> flow, String version, boolean optional) {
        Registration r = new Registration(type, codec, handler, protocols, flow, version, optional);
        if (PAYLOADS.putIfAbsent(type.id(), r) != null) {
            throw new IllegalStateException("Payload " + type.id() + " is already registered");
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        return PAYLOADS.containsKey(id);
    }

    /** Codec for a registered payload id, used for both encoding and decoding. */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <B extends FriendlyByteBuf> StreamCodec<B, ? extends CustomPacketPayload> codec(ResourceLocation id) {
        Registration r = PAYLOADS.get(id);
        return r == null ? null : (StreamCodec<B, ? extends CustomPacketPayload>) r.codec();
    }

    /** @return true if the payload was a NeoForge payload and has been handled */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean handle(CustomPacketPayload payload, Connection connection) {
        Registration r = PAYLOADS.get(payload.type().id());
        if (r == null) return false;
        PacketListener listener = connection.getPacketListener();
        if (!(listener instanceof ICommonPacketListener common)) {
            CampatModus.LOGGER.warn("Dropping payload {}: unsupported listener {}", payload.type().id(), listener);
            return true;
        }
        PacketFlow flow = listener.flow();
        if (r.flow().isPresent() && r.flow().get() != flow) {
            CampatModus.LOGGER.warn("Dropping payload {} received in the wrong direction ({})", payload.type().id(), flow);
            return true;
        }
        if (!r.protocols().contains(listener.protocol())) {
            CampatModus.LOGGER.warn("Dropping payload {} received during {} phase", payload.type().id(), listener.protocol());
            return true;
        }
        try {
            ((IPayloadHandler) r.handler()).handle(payload, new CampatPayloadContext(common, flow));
        } catch (Throwable t) {
            CampatModus.LOGGER.error("Failed to handle payload {}", payload.type().id(), t);
            common.disconnect(Component.literal("Failed to handle payload " + payload.type().id() + ": " + t));
        }
        return true;
    }

    /** Both sides run the same conversion, so a registered payload is assumed to be known remotely. */
    public static boolean hasChannel(Connection connection, ConnectionProtocol protocol, ResourceLocation id) {
        Registration r = PAYLOADS.get(id);
        return r != null && r.protocols().contains(protocol);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handlePacket(Packet<?> packet, ICommonPacketListener listener) {
        ((Packet) packet).handle(listener);
    }
}
