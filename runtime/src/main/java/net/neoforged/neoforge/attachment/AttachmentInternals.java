/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.attachment;

import io.github.hugoneale.campat.runtime.attachment.AttachmentAccess;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AttachmentInternals {
    private static void copyAttachments(HolderLookup.Provider provider, AttachmentHolder from, AttachmentHolder to, Predicate<AttachmentType<?>> filter) {
        if (from.attachments == null) {
            return;
        }
        for (var entry : from.attachments.entrySet()) {
            AttachmentType<?> type = entry.getKey();
            if (type.serializer == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            var copyHandler = (IAttachmentCopyHandler<Object>) type.copyHandler;
            if (filter.test(type)) {
                Object copy = copyHandler.copy(entry.getValue(), to.getExposedHolder(), provider);
                if (copy != null) {
                    to.getAttachmentMap().put(type, copy);
                }
            }
        }
    }

    public static void copyChunkAttachmentsOnPromotion(HolderLookup.Provider provider, AttachmentHolder.AsField from, AttachmentHolder.AsField to) {
        copyAttachments(provider, from, to, type -> true);
    }

    public static void copyEntityAttachments(Entity from, Entity to, boolean isDeath) {
        copyAttachments(from.registryAccess(), AttachmentAccess.of(from).campat$attachments(), AttachmentAccess.of(to).campat$attachments(),
            isDeath ? type -> type.copyOnDeath : type -> true);
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> copyEntityAttachments(event.getOriginal(), event.getEntity(), event.isWasDeath()));
        MinecraftForge.EVENT_BUS.addListener((LivingConversionEvent.Post event) -> copyEntityAttachments(event.getEntity(), event.getOutcome(), true));
    }

    private AttachmentInternals() {}
}
