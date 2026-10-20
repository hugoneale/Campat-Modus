package io.github.hugoneale.campat.runtime.mixin.legacy;

import io.github.hugoneale.campat.runtime.legacy.forge.LegacyNetworkHooks;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.1 entities build their spawn packet without the ServerEntity: it is handed over here */
@Mixin(ServerEntity.class)
public abstract class ServerEntityPairingMixin {
    @Inject(method = "sendPairingData", at = @At("HEAD"))
    private void campat$pairingStart(ServerPlayer player, java.util.function.Consumer<?> consumer, CallbackInfo ci) {
        LegacyNetworkHooks.pairing((ServerEntity) (Object) this);
    }

    @Inject(method = "sendPairingData", at = @At("RETURN"))
    private void campat$pairingEnd(ServerPlayer player, java.util.function.Consumer<?> consumer, CallbackInfo ci) {
        LegacyNetworkHooks.pairing(null);
    }
}
