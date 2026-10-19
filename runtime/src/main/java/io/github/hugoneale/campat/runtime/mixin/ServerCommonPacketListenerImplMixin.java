package io.github.hugoneale.campat.runtime.mixin;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** NeoForge patches server packet listeners to implement {@link ICommonPacketListener}. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin implements ICommonPacketListener {
    @Shadow @Final protected MinecraftServer server;

    @Override
    public void send(CustomPacketPayload payload) {
        ((ServerCommonPacketListenerImpl) (Object) this).send(new ClientboundCustomPayloadPacket(payload));
    }

    @Override
    public ReentrantBlockableEventLoop<?> getMainThreadEventLoop() {
        return server;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.NEOFORGE;
    }
}
