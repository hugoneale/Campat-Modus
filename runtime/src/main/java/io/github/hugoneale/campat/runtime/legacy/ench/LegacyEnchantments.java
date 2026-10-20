package io.github.hugoneale.campat.runtime.legacy.ench;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

/** A registry for 1.20.1 mod enchantments, standing in for Forge 47's {@code ForgeRegistries.ENCHANTMENTS}. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyEnchantments {
    private LegacyEnchantments() {}

    public static final ResourceKey<Registry<LegacyEnchantment>> KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("campat", "legacy_enchantments"));
    private static Supplier<IForgeRegistry<LegacyEnchantment>> registry;
    private static IForgeRegistry<?> placeholder;

    public static void onNewRegistry(NewRegistryEvent event) {
        registry = event.create(RegistryBuilder.<LegacyEnchantment>of(KEY.location()).disableSaving().disableSync());
    }

    public static ResourceKey key() {
        return KEY;
    }

    /** ForgeRegistries.ENCHANTMENTS of 1.20.1 code */
    public static synchronized IForgeRegistry forgeRegistry() {
        if (placeholder == null) {
            placeholder = (IForgeRegistry<?>) Proxy.newProxyInstance(LegacyEnchantments.class.getClassLoader(), new Class<?>[]{IForgeRegistry.class}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getRegistryKey": return KEY;
                    case "getRegistryName": return KEY.location();
                    case "hashCode": return 31;
                    case "equals": return proxy == args[0];
                    case "toString": return "LegacyEnchantments";
                    default:
                }
                IForgeRegistry<?> real = registry == null ? null : registry.get();
                if (real == null) {
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    if (r == java.util.Collection.class || r == java.util.Set.class) return java.util.Set.of();
                    return null;
                }
                return m.invoke(real, args);
            });
        }
        return (IForgeRegistry) placeholder;
    }

    /** DeferredRegister.create(key, modid): the vanilla enchantment registry is data driven now */
    public static DeferredRegister create(ResourceKey key, String modid) {
        return DeferredRegister.create(key == Registries.ENCHANTMENT || Registries.ENCHANTMENT.equals(key) ? (ResourceKey) KEY : key, modid);
    }

    /** casts to Enchantment in 1.20.1 code */
    public static Enchantment cast(Object value) {
        if (value instanceof LegacyEnchantment l) return l.asVanilla();
        return (Enchantment) value;
    }

    public static LegacyMobType mobType(LivingEntity entity) {
        return LegacyMobType.of(entity);
    }
}
