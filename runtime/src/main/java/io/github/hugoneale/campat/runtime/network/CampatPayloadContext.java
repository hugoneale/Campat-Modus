package io.github.hugoneale.campat.runtime.network;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** {@link IPayloadContext} for a payload received on either side. */
public final class CampatPayloadContext implements IPayloadContext {
    private final ICommonPacketListener listener;
    private final PacketFlow flow;

    public CampatPayloadContext(ICommonPacketListener listener, PacketFlow flow) {
        this.listener = listener;
        this.flow = flow;
    }

    @Override
    public ICommonPacketListener listener() {
        return listener;
    }

    @Override
    public Player player() {
        if (listener instanceof ServerGamePacketListenerImpl game) return game.player;
        if (flow == PacketFlow.CLIENTBOUND) {
            Player p = ClientAccess.player();
            if (p != null) return p;
        }
        throw new UnsupportedOperationException("No player is available during the " + listener.protocol() + " phase");
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        return listener.getMainThreadEventLoop().submit(task);
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        return listener.getMainThreadEventLoop().submit(task);
    }

    @Override
    public PacketFlow flow() {
        return flow;
    }

    @Override
    public void handle(CustomPacketPayload payload) {
        if (!NeoNetworkRegistry.handle(payload, listener.getConnection())) {
            throw new IllegalArgumentException("Payload " + payload.type().id() + " is not a registered NeoForge payload");
        }
    }

    @Override
    public void finishCurrentTask(ConfigurationTask.Type type) {
        if (listener instanceof ServerConfigurationPacketListenerImpl config) {
            config.finishCurrentTask(type);
        } else {
            throw new UnsupportedOperationException("finishCurrentTask is only valid on the server during configuration");
        }
    }

    /** Isolated so the dedicated server never loads client classes. */
    static final class ClientAccess {
        static Player player() {
            try {
                return io.github.hugoneale.campat.runtime.client.ClientNetwork.player();
            } catch (LinkageError e) {
                return null;
            }
        }
    }
}
