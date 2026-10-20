package io.github.hugoneale.campat.runtime.legacy.client;

import net.minecraft.client.Minecraft;

/** Client side constructor argument adapters, see {@code LegacyCtors}. */
public final class LegacyClientCtors {
    private LegacyClientCtors() {}

    /** AbstractSelectionList(mc, width, height, y0, y1, itemHeight) -> (mc, width, listHeight, y, itemHeight) */
    public static Object[] selectionList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
        return new Object[]{mc, width, y1 - y0, y0, itemHeight};
    }
}
