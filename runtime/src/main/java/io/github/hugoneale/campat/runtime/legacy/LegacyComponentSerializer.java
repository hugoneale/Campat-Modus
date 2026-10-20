package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;

/** Minecraft 1.20.1's {@code Component.Serializer}, a Gson adapter mods could extend; backed by 1.21.1's codec. */
public class LegacyComponentSerializer implements JsonDeserializer<MutableComponent>, JsonSerializer<Component> {
    public LegacyComponentSerializer() {}

    private static RegistryOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, LegacyRegistries.provider());
    }

    @Override
    public MutableComponent deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        return ComponentSerialization.CODEC.parse(ops(), json).getOrThrow(JsonParseException::new).copy();
    }

    @Override
    public JsonElement serialize(Component component, Type type, JsonSerializationContext context) {
        return ComponentSerialization.CODEC.encodeStart(ops(), component).getOrThrow(JsonParseException::new);
    }

    public static String toJson(Component component) {
        return LegacyMisc.componentToJson(component);
    }

    public static String toStableJson(Component component) {
        return LegacyMisc.componentToJson(component);
    }

    public static JsonElement toJsonTree(Component component) {
        return ComponentSerialization.CODEC.encodeStart(ops(), component).getOrThrow(JsonParseException::new);
    }

    public static MutableComponent fromJson(String json) {
        return LegacyMisc.componentFromJson(json);
    }

    public static MutableComponent fromJson(JsonElement json) {
        return LegacyMisc.componentFromJson(json);
    }

    public static MutableComponent fromJsonLenient(String json) {
        return Component.Serializer.fromJsonLenient(json, LegacyRegistries.provider());
    }
}
