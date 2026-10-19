/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModContainer;

/**
 * Register an instance to {@code ModContainer#registerExtensionPoint} to supply a config screen for
 * the mods list. Bridged to Forge's {@code ConfigScreenHandler.ConfigScreenFactory}.
 */
@SuppressWarnings("rawtypes")
public interface IConfigScreenFactory extends IExtensionPoint {
    /** @deprecated use {@link #createScreen(ModContainer, Screen)} */
    @Deprecated(forRemoval = true)
    default Screen createScreen(Minecraft minecraft, Screen modListScreen) {
        throw new RuntimeException("You must implement createScreen(ModContainer, Screen)");
    }

    default Screen createScreen(ModContainer container, Screen modListScreen) {
        return createScreen(net.minecraft.client.Minecraft.getInstance(), modListScreen);
    }
}
