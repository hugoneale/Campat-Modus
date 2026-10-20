package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.NetworkDirection;

/** Forge 47's {@code NetworkEvent.Context} over Forge 52's {@link CustomPayloadEvent.Context}. */
public class LegacyNetworkContext {
    private final CustomPayloadEvent.Context context;

    public LegacyNetworkContext(CustomPayloadEvent.Context context) {
        this.context = context;
    }

    public CompletableFuture<Void> enqueueWork(Runnable work) {
        return context.enqueueWork(work);
    }

    public ServerPlayer getSender() {
        return context.getSender();
    }

    public void setPacketHandled(boolean handled) {
        context.setPacketHandled(handled);
    }

    public boolean getPacketHandled() {
        return context.getPacketHandled();
    }

    public Connection getNetworkManager() {
        return context.getConnection();
    }

    public NetworkDirection getDirection() {
        return context.isClientSide() ? NetworkDirection.PLAY_TO_CLIENT : NetworkDirection.PLAY_TO_SERVER;
    }

    public CustomPayloadEvent.Context unwrap() {
        return context;
    }
}
