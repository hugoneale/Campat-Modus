package io.github.hugoneale.campat.runtime.bridge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ModConfig;

/** NeoForge config behaviour that Forge lacks. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConfigBridge {
    private ConfigBridge() {}

    /** NeoForge's {@code ModConfig.Type.STARTUP} does not exist on Forge; it is mapped to COMMON. */
    public static ModConfig.Type startupType() {
        return ModConfig.Type.COMMON;
    }

    static void afterRegister(ModContainer container, ModConfig.Type type) {
        // Forge loads COMMON and CLIENT configs right after mod construction, which is what most
        // NeoForge mods expect. STARTUP configs (mapped to COMMON) are read later than on NeoForge.
    }

    // ---- NeoForge list overloads with a new-element supplier (only used by NeoForge's config UI) ----

    public static ForgeConfigSpec.ConfigValue defineList(ForgeConfigSpec.Builder b, String path, java.util.List def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineList(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineList(ForgeConfigSpec.Builder b, String path, java.util.function.Supplier def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineList(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineList(ForgeConfigSpec.Builder b, java.util.List path, java.util.List def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineList(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineList(ForgeConfigSpec.Builder b, java.util.List path, java.util.function.Supplier def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineList(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineListAllowEmpty(ForgeConfigSpec.Builder b, String path, java.util.List def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineListAllowEmpty(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineListAllowEmpty(ForgeConfigSpec.Builder b, String path, java.util.function.Supplier def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineListAllowEmpty(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineListAllowEmpty(ForgeConfigSpec.Builder b, java.util.List path, java.util.List def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineListAllowEmpty(path, def, validator);
    }

    public static ForgeConfigSpec.ConfigValue defineListAllowEmpty(ForgeConfigSpec.Builder b, java.util.List path, java.util.function.Supplier def, java.util.function.Supplier newElement, java.util.function.Predicate validator) {
        return b.defineListAllowEmpty(path, def, validator);
    }

    // ---- NeoForge's config values implement the primitive supplier interfaces ----

    public static boolean getAsBoolean(ForgeConfigSpec.BooleanValue v) {
        return v.get();
    }

    public static boolean isTrue(ForgeConfigSpec.BooleanValue v) {
        return v.get();
    }

    public static boolean isFalse(ForgeConfigSpec.BooleanValue v) {
        return !v.get();
    }

    public static int getAsInt(ForgeConfigSpec.IntValue v) {
        return v.get();
    }

    public static long getAsLong(ForgeConfigSpec.LongValue v) {
        return v.get();
    }

    public static double getAsDouble(ForgeConfigSpec.DoubleValue v) {
        return v.get();
    }
}
