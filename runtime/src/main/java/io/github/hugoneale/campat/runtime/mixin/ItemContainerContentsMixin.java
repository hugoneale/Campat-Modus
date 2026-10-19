package io.github.hugoneale.campat.runtime.mixin;

import io.github.hugoneale.campat.runtime.capability.ItemContainerContentsSlots;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** NeoForge adds slot access to {@code ItemContainerContents}. */
@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin implements ItemContainerContentsSlots {
    @Shadow @Final private NonNullList<ItemStack> items;

    @Override
    public int getSlots() {
        return items.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return items.get(slot).copy();
    }
}
