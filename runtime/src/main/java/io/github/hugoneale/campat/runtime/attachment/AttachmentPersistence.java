package io.github.hugoneale.campat.runtime.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import net.neoforged.neoforge.attachment.LevelAttachmentsSavedData;

/** Level and chunk attachment persistence, plus copying on respawn and conversion. */
public final class AttachmentPersistence {
    private AttachmentPersistence() {}

    public static void register() {
        AttachmentInternals.register();
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel level) LevelAttachmentsSavedData.init(level);
        });
        bus.addListener((ChunkDataEvent.Save e) -> {
            CompoundTag data = AttachmentAccess.of(e.getChunk()).serializeAttachments(e.getLevel().registryAccess());
            if (data != null) e.getData().put(AttachmentHolder.ATTACHMENTS_NBT_KEY, data);
        });
        bus.addListener((ChunkDataEvent.Load e) -> {
            if (e.getData().contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND) && e.getLevel() != null) {
                AttachmentAccess.of(e.getChunk()).deserializeAttachments(e.getLevel().registryAccess(), e.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY));
            }
        });
    }
}
