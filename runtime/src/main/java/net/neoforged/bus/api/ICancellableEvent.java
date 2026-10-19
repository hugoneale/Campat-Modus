/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.bus.api;

import net.minecraftforge.eventbus.api.Event;

/**
 * NeoForge marks cancellable events with this interface, Forge with {@code @Cancelable}. The
 * converter adds {@code @Cancelable} to mod events implementing this interface; Forge's
 * {@link Event} provides the actual state.
 */
public interface ICancellableEvent {
    default void setCanceled(boolean canceled) {
        ((Event) this).setCanceled(canceled);
    }

    default boolean isCanceled() {
        return ((Event) this).isCanceled();
    }
}
