package io.github.hugoneale.campat.runtime.legacy;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/** Minecraft 1.20.1's {@code DyeableLeatherItem}; colors live in the DYED_COLOR component now. */
public interface LegacyDyeable {
    String TAG_COLOR = "color";
    String TAG_DISPLAY = "display";
    int DEFAULT_LEATHER_COLOR = 10511680;

    default boolean hasCustomColor(ItemStack stack) {
        return stack.has(DataComponents.DYED_COLOR);
    }

    default int getColor(ItemStack stack) {
        DyedItemColor c = stack.get(DataComponents.DYED_COLOR);
        return c == null ? DEFAULT_LEATHER_COLOR : c.rgb();
    }

    default void clearColor(ItemStack stack) {
        stack.remove(DataComponents.DYED_COLOR);
    }

    default void setColor(ItemStack stack, int color) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
    }

    static ItemStack dyeArmor(ItemStack stack, List<DyeItem> dyes) {
        return DyedItemColor.applyDyes(stack, dyes);
    }

    static DyeColor unused() {
        return null;
    }
}
