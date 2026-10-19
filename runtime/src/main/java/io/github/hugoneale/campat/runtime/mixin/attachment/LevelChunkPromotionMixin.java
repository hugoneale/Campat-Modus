package io.github.hugoneale.campat.runtime.mixin.attachment;

import io.github.hugoneale.campat.runtime.attachment.AttachmentAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk attachments loaded onto the proto chunk move to the level chunk, as on NeoForge. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPromotionMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void campat$copyAttachments(ServerLevel level, ProtoChunk proto, LevelChunk.PostLoadProcessor post, CallbackInfo ci) {
        AttachmentInternals.copyChunkAttachmentsOnPromotion(level.registryAccess(), AttachmentAccess.of(proto).campat$attachments(), AttachmentAccess.of(this).campat$attachments());
    }
}
