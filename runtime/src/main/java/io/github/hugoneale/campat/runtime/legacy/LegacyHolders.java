package io.github.hugoneale.campat.runtime.legacy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

/** 1.20.1 passed registry objects around directly; 1.21.1 wants {@link Holder}s for many of them. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyHolders {
    private LegacyHolders() {}

    private static final Map<Class<?>, Registry<?>> BUILTIN = new ConcurrentHashMap<>();

    public static Holder wrap(Object value) {
        if (value == null) return null;
        if (value instanceof Holder<?> h) return h;
        Registry r = BUILTIN.get(value.getClass());
        if (r != null && r.getResourceKey(value).isPresent()) return r.wrapAsHolder(value);
        for (Registry reg : BuiltInRegistries.REGISTRY) {
            if (reg.getResourceKey(value).isPresent()) {
                BUILTIN.put(value.getClass(), reg);
                return reg.wrapAsHolder(value);
            }
        }
        // data driven registries (enchantments, biomes, damage types, ...)
        RegistryAccess access = LegacyRegistries.access();
        for (RegistryAccess.RegistryEntry e : access.registries().toList()) {
            Registry reg = e.value();
            if (reg.getResourceKey(value).isPresent()) return reg.wrapAsHolder(value);
        }
        return Holder.direct(value);
    }

    public static Object value(Holder holder) {
        return holder == null ? null : holder.value();
    }
}
