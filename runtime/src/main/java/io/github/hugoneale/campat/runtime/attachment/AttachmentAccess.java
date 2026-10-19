package io.github.hugoneale.campat.runtime.attachment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Mixed into the vanilla classes NeoForge makes attachment holders (entities, block entities,
 * levels, chunks). NeoForge changes their super class to {@code AttachmentHolder}; here the storage
 * lives in a delegate and this interface forwards {@link IAttachmentHolder} to it.
 */
public interface AttachmentAccess extends IAttachmentHolder {
    AttachmentHolder.AsField campat$attachments();

    @Override
    default boolean hasAttachments() {
        return campat$attachments().hasAttachments();
    }

    @Override
    default boolean hasData(AttachmentType<?> type) {
        return campat$attachments().hasData(type);
    }

    @Override
    default <T> T getData(AttachmentType<T> type) {
        return campat$attachments().getData(type);
    }

    @Override
    @Nullable
    default <T> T getExistingDataOrNull(AttachmentType<T> type) {
        return campat$attachments().getExistingDataOrNull(type);
    }

    @Override
    @Nullable
    default <T> T setData(AttachmentType<T> type, T data) {
        return campat$attachments().setData(type, data);
    }

    @Override
    @Nullable
    default <T> T removeData(AttachmentType<T> type) {
        return campat$attachments().removeData(type);
    }

    @Override
    default void syncData(AttachmentType<?> type) {
        // TODO(campat): attachment syncing (AttachmentType.Builder#sync) is not emulated yet
    }

    /** NeoForge's {@code AttachmentHolder#serializeAttachments}, exposed on the vanilla classes. */
    @Nullable
    default CompoundTag serializeAttachments(HolderLookup.Provider provider) {
        return campat$attachments().serializeAttachments(provider);
    }

    /** NeoForge's {@code AttachmentHolder#deserializeAttachments}, exposed on the vanilla classes. */
    default void deserializeAttachments(HolderLookup.Provider provider, CompoundTag tag) {
        campat$attachments().deserializeInternal(provider, tag);
    }

    static AttachmentAccess of(Object holder) {
        return (AttachmentAccess) holder;
    }
}
