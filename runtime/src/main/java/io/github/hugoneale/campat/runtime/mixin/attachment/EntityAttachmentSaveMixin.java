package io.github.hugoneale.campat.runtime.mixin.attachment;

import io.github.hugoneale.campat.runtime.attachment.AttachmentAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Persists entity attachments under NeoForge's NBT key so worlds stay compatible with NeoForge. */
@Mixin(Entity.class)
public abstract class EntityAttachmentSaveMixin {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void campat$saveAttachments(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        Entity self = (Entity) (Object) this;
        CompoundTag data = AttachmentAccess.of(self).serializeAttachments(self.registryAccess());
        if (data != null) cir.getReturnValue().put(AttachmentHolder.ATTACHMENTS_NBT_KEY, data);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void campat$loadAttachments(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND)) {
            Entity self = (Entity) (Object) this;
            AttachmentAccess.of(self).deserializeAttachments(self.registryAccess(), tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY));
        }
    }
}
