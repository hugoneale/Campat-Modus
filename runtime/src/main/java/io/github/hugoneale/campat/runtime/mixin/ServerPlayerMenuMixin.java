package io.github.hugoneale.campat.runtime.mixin;

import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.common.extensions.IForgeServerPlayer;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's {@code openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)} on server players,
 * implemented with Forge's menu opening (which writes a plain buffer).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMixin implements IPlayerExtension {
    @Override
    public OptionalInt openMenu(MenuProvider menuProvider, Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Consumer<FriendlyByteBuf> writer = buf -> extraDataWriter.accept(new RegistryFriendlyByteBuf(buf, self.registryAccess()));
        ((IForgeServerPlayer) self).openMenu(menuProvider, writer);
        return self.containerMenu != self.inventoryMenu ? OptionalInt.of(self.containerMenu.containerId) : OptionalInt.empty();
    }
}
