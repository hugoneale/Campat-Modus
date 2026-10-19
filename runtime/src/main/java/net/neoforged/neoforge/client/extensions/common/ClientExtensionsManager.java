/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.extensions.common;

import io.github.hugoneale.campat.runtime.client.RenderPropertiesAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * NeoForge registers client extensions through an event; Forge stores them on the object
 * ({@code initializeClient}). Registrations are written into Forge's field, so Forge's lookups
 * ({@code IClientItemExtensions.of} & co.) find them.
 */
public final class ClientExtensionsManager {
    static final Map<Block, IClientBlockExtensions> BLOCK_EXTENSIONS = new IdentityHashMap<>();
    static final Map<Item, IClientItemExtensions> ITEM_EXTENSIONS = new IdentityHashMap<>();
    static final Map<MobEffect, IClientMobEffectExtensions> MOB_EFFECT_EXTENSIONS = new IdentityHashMap<>();
    static final Map<FluidType, IClientFluidTypeExtensions> FLUID_TYPE_EXTENSIONS = new IdentityHashMap<>();

    private ClientExtensionsManager() {}

    @SafeVarargs
    static <T, E> void register(E extensions, Map<T, E> target, T... objects) {
        if (objects.length == 0) {
            throw new IllegalArgumentException("At least one target must be provided");
        }
        for (T object : objects) {
            E existing = target.put(object, extensions);
            if (existing != null) {
                throw new IllegalStateException("Duplicate client extensions registration for " + object + " (old: " + existing + ", new: " + extensions + ")");
            }
            ((RenderPropertiesAccessor) object).campat$setRenderProperties(extensions);
        }
    }

    public static void init() {
        io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle.postModBus(new RegisterClientExtensionsEvent());
    }
}
