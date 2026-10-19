package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Argument conversions and defaults used by the converter's evolved method bridges (V mappings). */
public final class Evo {
    private Evo() {}

    public static Item.TooltipContext tooltipContext(Level level) {
        return level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(level);
    }

    public static Level level(Item.TooltipContext context) {
        return context == null ? null : context.level();
    }

    public static InteractionHand mainHand() {
        return InteractionHand.MAIN_HAND;
    }
}
