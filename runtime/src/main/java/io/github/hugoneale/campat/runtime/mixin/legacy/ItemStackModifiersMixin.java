package io.github.hugoneale.campat.runtime.mixin.legacy;

import io.github.hugoneale.campat.runtime.legacy.forge.LegacyItemAttributeModifierEvent;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires Forge 47's ItemAttributeModifierEvent for 1.20.1 mods. */
@Mixin(ItemStack.class)
public abstract class ItemStackModifiersMixin {
    @Unique
    private static final ThreadLocal<Boolean> campat$inside = ThreadLocal.withInitial(() -> false);

    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void campat$legacyModifierEvent(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer, CallbackInfo ci) {
        if (campat$inside.get()) return;
        ItemStack self = (ItemStack) (Object) this;
        campat$inside.set(true);
        try {
            LegacyItemAttributeModifierEvent.wrap(self, slot, null, c -> self.forEachModifier(slot, c), consumer);
        } finally {
            campat$inside.set(false);
        }
        ci.cancel();
    }
}
