/*
 * Copyright (c) Forge Development LLC and contributors
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * NeoForge's menu factory receives a {@link RegistryFriendlyByteBuf}, Forge's a plain
 * {@link FriendlyByteBuf}. This is a Forge factory, so Forge's menu opening code calls it, and
 * upgrades the buffer before handing it to the NeoForge implementation.
 */
public interface IContainerFactory<T extends AbstractContainerMenu> extends net.minecraftforge.network.IContainerFactory<T> {
    T create(int windowId, Inventory inv, RegistryFriendlyByteBuf data);

    @Override
    default T create(int windowId, Inventory inv, FriendlyByteBuf data) {
        RegistryFriendlyByteBuf buf = data == null || data instanceof RegistryFriendlyByteBuf ? (RegistryFriendlyByteBuf) data
            : new RegistryFriendlyByteBuf(data, inv.player.registryAccess());
        return create(windowId, inv, buf);
    }

    @Override
    default T create(int windowId, Inventory inv) {
        return create(windowId, inv, (RegistryFriendlyByteBuf) null);
    }
}
