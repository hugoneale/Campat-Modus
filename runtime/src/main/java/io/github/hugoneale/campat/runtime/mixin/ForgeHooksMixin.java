package io.github.hugoneale.campat.runtime.mixin;

import io.github.hugoneale.campat.runtime.network.NeoNetworkRegistry;
import net.minecraft.network.Connection;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Teaches vanilla's custom payload codec (as patched by Forge) about NeoForge payload types. */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class ForgeHooksMixin {
    @Inject(method = "getCustomPayloadCodec", at = @At("HEAD"), cancellable = true)
    private static void campat$neoPayloadCodec(ResourceLocation id, int max, CallbackInfoReturnable<StreamCodec<?, ? extends CustomPacketPayload>> cir) {
        StreamCodec<?, ? extends CustomPacketPayload> codec = NeoNetworkRegistry.codec(id);
        if (codec != null) cir.setReturnValue(codec);
    }

    @Inject(method = "onCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;Lnet/minecraft/network/Connection;)Z", at = @At("HEAD"), cancellable = true)
    private static void campat$neoPayloadHandler(CustomPacketPayload payload, Connection connection, CallbackInfoReturnable<Boolean> cir) {
        if (NeoNetworkRegistry.handle(payload, connection)) cir.setReturnValue(true);
    }
}
