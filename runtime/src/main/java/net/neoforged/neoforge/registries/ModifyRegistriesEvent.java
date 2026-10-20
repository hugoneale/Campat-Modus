package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge's mod bus event for modifying registries (adding callbacks) once they all exist. Fired
 * by the runtime at the end of Forge's {@code NewRegistryEvent} handling.
 */
public class ModifyRegistriesEvent extends Event implements IModBusEvent {
    public ModifyRegistriesEvent() {}

    public Iterable<? extends Registry<?>> getRegistries() {
        return BuiltInRegistries.REGISTRY;
    }

    @SuppressWarnings("unchecked")
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {
        Registry<T> r = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
        if (r == null) throw new IllegalArgumentException("No registry " + key.location());
        return r;
    }
}
