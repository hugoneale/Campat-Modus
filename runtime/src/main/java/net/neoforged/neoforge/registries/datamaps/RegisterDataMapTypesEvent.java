/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries.datamaps;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.registries.DataPackRegistriesHooks;
import org.jetbrains.annotations.ApiStatus;

/**
 * Event fired on the mod event bus, in order to register {@link DataMapType data map types}.
 */
public class RegisterDataMapTypesEvent extends Event implements IModBusEvent {
    /** campat: Forge's event bus needs a public no-arg constructor to build listener lists */
    public RegisterDataMapTypesEvent() {
        this((Map) null);
    }

    private final Map<ResourceKey<Registry<?>>, Map<ResourceLocation, DataMapType<?, ?>>> attachments;

    @ApiStatus.Internal
    public RegisterDataMapTypesEvent(Map<ResourceKey<Registry<?>>, Map<ResourceLocation, DataMapType<?, ?>>> attachments) {
        this.attachments = attachments;
    }

    /**
     * Register a registry data map.
     *
     * @param type the data map type to register
     * @param <T>  the type of the data map
     * @param <R>  the type of the registry
     * @throws IllegalArgumentException      if a type with the same ID has already been registered for that registry
     * @throws UnsupportedOperationException if the registry is a non-synced datapack registry and the data map is synced
     */
    public <T, R> void register(DataMapType<R, T> type) {
        final var registry = type.registryKey();

        final var map = attachments.computeIfAbsent((ResourceKey) registry, k -> new HashMap<>());
        if (map.containsKey(type.id())) {
            throw new IllegalArgumentException("Tried to register data map type with ID " + type.id() + " to registry " + registry.location() + " twice");
        }
        map.put(type.id(), type);
    }
}
