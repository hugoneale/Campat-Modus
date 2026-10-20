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
