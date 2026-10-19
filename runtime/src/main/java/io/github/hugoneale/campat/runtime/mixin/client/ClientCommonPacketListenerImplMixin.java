package io.github.hugoneale.campat.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** NeoForge patches client packet listeners to implement {@link ICommonPacketListener}. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin implements ICommonPacketListener {
    @Shadow @Final protected Minecraft minecraft;
    @Shadow @Final protected Connection connection;

    @Override
    public void send(CustomPacketPayload payload) {
        ((ClientCommonPacketListenerImpl) (Object) this).send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public void disconnect(Component reason) {
        connection.disconnect(reason);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public ReentrantBlockableEventLoop<?> getMainThreadEventLoop() {
        return minecraft;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.NEOFORGE;
    }
}
