package io.github.hugoneale.campat.runtime.capability;

import net.minecraft.world.item.ItemStack;

/** Methods NeoForge patches into {@code ItemContainerContents}; mixed in. */
public interface ItemContainerContentsSlots {
    int getSlots();

    ItemStack getStackInSlot(int slot);

    static ItemContainerContentsSlots of(Object contents) {
        return (ItemContainerContentsSlots) contents;
    }
}
