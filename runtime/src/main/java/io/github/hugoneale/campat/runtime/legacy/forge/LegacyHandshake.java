package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Forge 47's {@code HandshakeHandler}. Forge 52 negotiates channels during the configuration phase
 * and 1.20.1 login packets are not sent (see {@link LegacySimpleChannel.MessageBuilder}), so these
 * handlers are only wired up, never called.
 */
public final class LegacyHandshake {
    private LegacyHandshake() {}

    public interface HandshakeConsumer<M> {
        void accept(LegacyHandshake handler, M message, Supplier<LegacyNetworkContext> context);
    }

    public static <M> BiConsumer<M, Supplier<LegacyNetworkContext>> biConsumerFor(HandshakeConsumer<M> consumer) {
        return (m, c) -> consumer.accept(null, m, c);
    }

    public static <M> BiConsumer<M, Supplier<LegacyNetworkContext>> indexFirst(HandshakeConsumer<M> consumer) {
        return biConsumerFor(consumer);
    }

    public static boolean packetNeedsResponse(net.minecraft.network.Connection connection, int index) {
        return false;
    }
}
