package io.github.hugoneale.campat.runtime.mixin;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import org.spongepowered.asm.mixin.Mixin;

/** NeoForge makes {@code ItemStack} a {@link MutableDataComponentHolder}. */
@Mixin(ItemStack.class)
public abstract class ItemStackComponentHolderMixin implements MutableDataComponentHolder {}
