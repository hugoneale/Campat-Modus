package io.github.hugoneale.campat.runtime.mixin.legacy;

import io.github.hugoneale.campat.runtime.legacy.LegacyItemStacks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Copies must not share a 1.20.1 style tag the source stack may still mutate. */
@Mixin(ItemStack.class)
public abstract class ItemStackCopyMixin {
    @Inject(method = "copy", at = @At("RETURN"))
    private void campat$copyLegacyTag(CallbackInfoReturnable<ItemStack> cir) {
        LegacyItemStacks.onCopy((ItemStack) (Object) this, cir.getReturnValue());
    }
}
