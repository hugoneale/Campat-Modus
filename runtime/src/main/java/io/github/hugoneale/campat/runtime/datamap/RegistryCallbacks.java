package io.github.hugoneale.campat.runtime.datamap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.callback.AddCallback;
import net.neoforged.neoforge.registries.callback.BakeCallback;
import net.neoforged.neoforge.registries.callback.RegistryCallback;

/**
 * NeoForge's {@code Registry.addCallback}: add callbacks run for entries registered during Forge's
 * {@link RegisterEvent} of that registry (after everyone registered), bake callbacks once
 * registration is over.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class RegistryCallbacks {
    private RegistryCallbacks() {}

    private static final Map<ResourceKey<?>, List<RegistryCallback<?>>> CALLBACKS = new HashMap<>();
    private static final Map<ResourceKey<?>, Set<Object>> SEEN = new HashMap<>();

    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.LOWEST, RegistryCallbacks::onRegister);
    }

    public static synchronized void addCallback(Registry registry, RegistryCallback callback) {
        CALLBACKS.computeIfAbsent(registry.key(), k -> new ArrayList<>()).add(callback);
    }

    public static void addCallback(Registry registry, Class type, RegistryCallback callback) {
        addCallback(registry, callback);
    }

    private static synchronized void onRegister(RegisterEvent event) {
        List<RegistryCallback<?>> list = CALLBACKS.get(event.getRegistryKey());
        Registry registry = event.getVanillaRegistry();
        if (list == null || registry == null) return;
        Set<Object> seen = SEEN.computeIfAbsent(event.getRegistryKey(), k -> new HashSet<>());
        for (Object key : registry.registryKeySet()) {
            if (!seen.add(key)) continue;
            Object value = registry.get((ResourceKey) key);
            for (RegistryCallback<?> cb : list) {
                if (cb instanceof AddCallback add) add.onAdd(registry, registry.getId(value), (ResourceKey) key, value);
            }
        }
    }

    /** after registration (common setup) */
    public static synchronized void bake() {
        for (var e : CALLBACKS.entrySet()) {
            Registry registry = (Registry) net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get((ResourceKey) e.getKey());
            if (registry == null) continue;
            for (RegistryCallback<?> cb : e.getValue()) if (cb instanceof BakeCallback bake) bake.onBake(registry);
        }
    }
}
