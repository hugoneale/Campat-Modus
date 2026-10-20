package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;

/** Forge 47's {@code NetworkHooks}. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyNetworkHooks {
    private LegacyNetworkHooks() {}

    public static void openScreen(ServerPlayer player, MenuProvider provider) {
        player.openMenu(provider);
    }

    public static void openScreen(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(provider, buf -> buf.writeBlockPos(pos));
    }

    public static void openScreen(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraData) {
        player.openMenu(provider, buf -> extraData.accept(buf));
    }

    public static Packet getEntitySpawningPacket(Entity entity) {
        return new ClientboundAddEntityPacket(entity, 0, entity.blockPosition());
    }
}
