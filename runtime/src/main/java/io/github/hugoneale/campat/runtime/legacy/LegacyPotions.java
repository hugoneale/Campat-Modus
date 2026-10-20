package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.world.item.alchemy.Potion;

/** Minecraft 1.20.1's {@code Potions.EMPTY}: there is no empty potion in 1.21.1 (absence is an empty Optional). */
public final class LegacyPotions {
    private LegacyPotions() {}

    private static Potion empty;

    public static synchronized Potion empty() {
        if (empty == null) empty = new Potion("empty");
        return empty;
    }
}
