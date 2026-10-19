/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/** NeoForge's GUI layer list. On Forge the layers end up in Forge's layered draw. */
public class GuiLayerManager {
    public static final float Z_SEPARATION = LayeredDraw.Z_SEPARATION;

    public record NamedLayer(ResourceLocation name, LayeredDraw.Layer layer) {}
}
