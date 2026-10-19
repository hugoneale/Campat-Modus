package io.github.hugoneale.campat.runtime.registry;

import io.github.hugoneale.campat.runtime.CampatModus;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.NewRegistryEvent;

import java.util.ArrayList;
import java.util.List;

/** Creates the registries NeoForge mods build with NeoForge's {@code RegistryBuilder}. */
public final class NeoRegistries {
    public record Pending(ResourceKey<? extends Registry<?>> key, boolean sync, int maxId, ResourceLocation defaultKey) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static boolean created;

    private NeoRegistries() {}

    public static synchronized void add(Pending p) {
        if (created) {
            CampatModus.LOGGER.error("Registry {} was built after registries were created and will not exist", p.key().location());
            return;
        }
        if (PENDING.stream().noneMatch(e -> e.key().equals(p.key()))) PENDING.add(p);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized void onNewRegistry(NewRegistryEvent event) {
        // NeoForge's own registries must exist before mods register into them
        net.neoforged.neoforge.registries.NeoForgeRegistries.init();
        for (Pending p : PENDING) {
            if (BuiltInRegistries.REGISTRY.containsKey(p.key().location())) continue;
            var b = net.minecraftforge.registries.RegistryBuilder.of(p.key().location()).hasTags();
            if (p.maxId() > 0) b.setMaxID(p.maxId());
            if (p.defaultKey() != null) b.setDefaultKey(p.defaultKey());
            if (!p.sync()) b.disableSync();
            event.create((net.minecraftforge.registries.RegistryBuilder) b);
        }
        created = true;
    }

    /** NeoForge's {@code NewRegistryEvent#register(Registry)}: the registry was already queued by its builder. */
    public static void register(NewRegistryEvent event, Registry<?> registry) {
    }
}
