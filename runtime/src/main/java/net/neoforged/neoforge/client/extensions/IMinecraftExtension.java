/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.extensions;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Extension interface for {@link Minecraft}.
 */
public interface IMinecraftExtension {
    private Minecraft self() {
        return (Minecraft) (Object) this;
    }

    /**
     * Pushes a screen as a new GUI layer.
     *
     * @param screen the new GUI layer
     */
    void pushGuiLayer(Screen screen) ;

    /**
     * Pops a GUI layer from the screen.
     */
    void popGuiLayer() ;

    /**
     * Retrieves the {@link Locale} set by the player.
     * Useful for creating string and number formatters.
     */
    Locale getLocale() ;
}
