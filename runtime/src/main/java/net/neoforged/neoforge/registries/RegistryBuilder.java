/*
 * Copyright (c) Forge Development LLC and contributors
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import io.github.hugoneale.campat.runtime.CampatModus;
import io.github.hugoneale.campat.runtime.registry.LazyRegistry;
import io.github.hugoneale.campat.runtime.registry.NeoRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge's registry builder. On Forge the registry is created through Forge's
 * {@code NewRegistryEvent}; {@link #create()} returns a view that resolves to it.
 */
public class RegistryBuilder<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    @Nullable
    private ResourceLocation defaultKey;
    private int maxId = -1;
    private boolean sync = false;

    public RegistryBuilder(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    public RegistryBuilder<T> defaultKey(ResourceLocation key) {
        this.defaultKey = key;
        return this;
    }

    public RegistryBuilder<T> defaultKey(ResourceKey<T> key) {
        this.defaultKey = key.location();
        return this;
    }

    public RegistryBuilder<T> withIntrusiveHolders() {
        return this;
    }

    public RegistryBuilder<T> callback(Object callback) {
        CampatModus.LOGGER.debug("Registry callbacks are not supported on {}", registryKey.location());
        return this;
    }

    public RegistryBuilder<T> onAdd(Object callback) {
        return callback(callback);
    }

    public RegistryBuilder<T> onBake(Object callback) {
        return callback(callback);
    }

    public RegistryBuilder<T> onClear(Object callback) {
        return callback(callback);
    }

    public RegistryBuilder<T> maxId(int maxId) {
        this.maxId = maxId;
        return this;
    }

    public RegistryBuilder<T> sync(boolean sync) {
        this.sync = sync;
        return this;
    }

    public RegistryBuilder<T> disableRegistrationCheck() {
        return this;
    }

    public Registry<T> create() {
        NeoRegistries.add(new NeoRegistries.Pending(registryKey, sync, maxId, defaultKey));
        return LazyRegistry.of(registryKey);
    }
}
