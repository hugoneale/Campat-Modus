package io.github.hugoneale.campat.runtime.mixin.attachment;

import io.github.hugoneale.campat.runtime.attachment.AttachmentAccess;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** NeoForge makes {@code Level} a data attachment holder. */
@Mixin(net.minecraft.world.level.Level.class)
public abstract class LevelAttachmentMixin implements AttachmentAccess {
    @Unique
    private AttachmentHolder.AsField campat$attachments;

    @Override
    public AttachmentHolder.AsField campat$attachments() {
        AttachmentHolder.AsField a = campat$attachments;
        if (a == null) campat$attachments = a = new AttachmentHolder.AsField((IAttachmentHolder) (Object) this);
        return a;
    }
}
