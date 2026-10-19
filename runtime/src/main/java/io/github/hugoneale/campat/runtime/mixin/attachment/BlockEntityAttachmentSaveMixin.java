package io.github.hugoneale.campat.runtime.mixin.attachment;

import io.github.hugoneale.campat.runtime.attachment.AttachmentAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public abstract class BlockEntityAttachmentSaveMixin {
    @Inject(method = "saveWithoutMetadata", at = @At("RETURN"))
    private void campat$saveAttachments(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag data = AttachmentAccess.of(this).serializeAttachments(provider);
        if (data != null) cir.getReturnValue().put(AttachmentHolder.ATTACHMENTS_NBT_KEY, data);
    }

    @Inject(method = "loadWithComponents", at = @At("RETURN"))
    private void campat$loadAttachments(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (tag.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND)) {
            AttachmentAccess.of(this).deserializeAttachments(provider, tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY));
        }
    }
}
