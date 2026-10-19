package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/** Small Minecraft 1.20.1 APIs that 1.21.1 removed or changed. */
public final class LegacyMisc {
    private LegacyMisc() {}

    // ---- ResourceLocation: the public constructors became factories ----

    public static ResourceLocation resourceLocation(String id) {
        return ResourceLocation.parse(id);
    }

    public static ResourceLocation resourceLocation(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static boolean isValidResourceLocation(String id) {
        return ResourceLocation.tryParse(id) != null;
    }

    // ---- chat ----

    public static TextColor parseColor(String color) {
        return TextColor.parseColor(color).result().orElse(null);
    }

    public static MutableComponent componentFromJson(String json) {
        return Component.Serializer.fromJson(json, LegacyRegistries.provider());
    }

    public static MutableComponent componentFromJson(com.google.gson.JsonElement json) {
        return Component.Serializer.fromJson(json, LegacyRegistries.provider());
    }

    public static String componentToJson(Component component) {
        return Component.Serializer.toJson(component, LegacyRegistries.provider());
    }

    // ---- DataFixerUpper 6 -> 8 ----

    public static Object getOrThrow(DataResult<?> result, boolean allowPartial, Consumer<String> onError) {
        if (allowPartial) {
            return result.resultOrPartial(onError).orElseThrow(() -> new IllegalStateException(result.error().map(e -> e.message()).orElse("no result")));
        }
        return result.getOrThrow(msg -> {
            onError.accept(msg);
            return new IllegalStateException(msg);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Either get(DataResult<?> result) {
        if (result.isSuccess()) return Either.left(result.getOrThrow());
        return Either.right(result.error().orElseThrow());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object utilGetOrThrow(DataResult result, Function toException) {
        Function<String, RuntimeException> f = msg -> {
            Object e = toException.apply(msg);
            return e instanceof RuntimeException r ? r : new IllegalStateException(msg, (Throwable) e);
        };
        DataResult<Object> typed = result;
        return typed.getOrThrow(f);
    }

    // ---- NBT files: File -> Path ----

    public static CompoundTag nbtRead(File file) throws IOException {
        return file.exists() ? NbtIo.read(file.toPath()) : null;
    }

    public static void nbtWrite(CompoundTag tag, File file) throws IOException {
        NbtIo.write(tag, file.toPath());
    }
}
