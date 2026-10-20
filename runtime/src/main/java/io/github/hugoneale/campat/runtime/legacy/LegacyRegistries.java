package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Minecraft 1.21 threads a {@link HolderLookup.Provider} through serialisation that 1.20.1 code never
 * had. This supplies the best one available: the running server's, else the client level's, else
 * the built-in registries (enough for items, blocks, effects, ...).
 */
public final class LegacyRegistries {
    private LegacyRegistries() {}

    private static RegistryAccess builtin;

    public static RegistryAccess access() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RegistryAccess client = ClientRegistries.access();
            if (client != null) return client;
        }
        return builtin();
    }

    public static HolderLookup.Provider provider() {
        return access();
    }

    public static RegistryAccess builtin() {
        if (builtin == null) builtin = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return builtin;
    }

    /** value of a key in a (possibly data driven) registry, or null while that registry is not loaded */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object resolveKey(net.minecraft.resources.ResourceKey key) {
        if (key == null) return null;
        try {
            return access().registry(ResourceKeyAccess.registryOf(key)).map(r -> ((net.minecraft.core.Registry) r).get(key)).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class ResourceKeyAccess {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static net.minecraft.resources.ResourceKey registryOf(net.minecraft.resources.ResourceKey key) {
            return net.minecraft.resources.ResourceKey.createRegistryKey(key.registry());
        }
    }

    // ---- built-in registries that became data driven in 1.21 (enchantments, banner patterns, paintings) ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static net.minecraft.core.Registry dynamic(net.minecraft.resources.ResourceKey key) {
        return dynamic(key, net.minecraft.core.Registry.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static net.minecraft.core.Registry dynamic(net.minecraft.resources.ResourceKey key, Class<?> type) {
        java.lang.reflect.InvocationHandler h = (proxy, method, args) -> {
            switch (method.getName()) {
                case "key": if (method.getParameterCount() == 0) return key; break;
                case "toString": if (method.getParameterCount() == 0) return "LegacyDynamicRegistry[" + key.location() + "]"; break;
                case "hashCode": if (method.getParameterCount() == 0) return key.hashCode(); break;
                case "equals": if (method.getParameterCount() == 1) return proxy == args[0]; break;
                default:
            }
            Object target = access().registryOrThrow(key);
            if (!method.getDeclaringClass().isInstance(target)) return null; // DefaultedRegistry extras
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (net.minecraft.core.Registry) java.lang.reflect.Proxy.newProxyInstance(LegacyRegistries.class.getClassLoader(),
            new Class<?>[]{type}, h);
    }

    private static final java.util.Map<Object, net.minecraft.core.DefaultedRegistry> DEFAULTED = new java.util.IdentityHashMap<>();

    /** a built-in registry that was a DefaultedRegistry in 1.20.1 (potions, ...) but is a plain one now */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized net.minecraft.core.DefaultedRegistry defaulted(Object registry) {
        if (registry instanceof net.minecraft.core.DefaultedRegistry d) return d;
        net.minecraft.core.Registry r = (net.minecraft.core.Registry) registry;
        return DEFAULTED.computeIfAbsent(r, k -> {
            java.util.function.Supplier<net.minecraft.resources.ResourceLocation> defaultKey = () -> {
                net.minecraft.resources.ResourceLocation empty = net.minecraft.resources.ResourceLocation.withDefaultNamespace("empty");
                if (r.containsKey(empty)) return empty;
                java.util.Iterator<net.minecraft.resources.ResourceLocation> it = r.keySet().iterator();
                return it.hasNext() ? it.next() : empty;
            };
            java.lang.reflect.InvocationHandler h = (proxy, method, args) -> {
                String n = method.getName();
                if (n.equals("getDefaultKey") && method.getParameterCount() == 0) return defaultKey.get();
                if (n.equals("equals") && method.getParameterCount() == 1) return proxy == args[0] || r == args[0];
                if (n.equals("hashCode") && method.getParameterCount() == 0) return r.hashCode();
                Object result;
                try {
                    result = method.invoke(r, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
                // DefaultedRegistry: lookups of unknown ids answer the default entry
                if (result == null && args != null && args.length == 1 && (n.equals("get") || n.equals("getKey"))) {
                    if (n.equals("get") && args[0] instanceof net.minecraft.resources.ResourceLocation) return r.get(defaultKey.get());
                    if (n.equals("getKey")) return defaultKey.get();
                }
                return result;
            };
            return (net.minecraft.core.DefaultedRegistry) java.lang.reflect.Proxy.newProxyInstance(LegacyRegistries.class.getClassLoader(),
                new Class<?>[]{net.minecraft.core.DefaultedRegistry.class}, h);
        });
    }

    private static net.minecraft.core.Registry enchantments, bannerPatterns;
    private static net.minecraft.core.DefaultedRegistry paintings;

    public static net.minecraft.core.Registry enchantmentRegistry() {
        if (enchantments == null) enchantments = dynamic(net.minecraft.core.registries.Registries.ENCHANTMENT);
        return enchantments;
    }

    public static net.minecraft.core.Registry bannerPatternRegistry() {
        if (bannerPatterns == null) bannerPatterns = dynamic(net.minecraft.core.registries.Registries.BANNER_PATTERN);
        return bannerPatterns;
    }

    public static net.minecraft.core.DefaultedRegistry paintingVariantRegistry() {
        if (paintings == null) paintings = (net.minecraft.core.DefaultedRegistry) dynamic(net.minecraft.core.registries.Registries.PAINTING_VARIANT, net.minecraft.core.DefaultedRegistry.class);
        return paintings;
    }
}
