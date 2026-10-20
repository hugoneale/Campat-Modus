package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Type;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;

/** Minecraft 1.20.1's {@code Style.Serializer}, a Gson adapter; backed by 1.21.1's codec. */
public class LegacyStyleSerializer implements JsonDeserializer<Style>, JsonSerializer<Style> {
    public LegacyStyleSerializer() {}

    private static RegistryOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, LegacyRegistries.provider());
    }

    @Override
    public Style deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        return Style.Serializer.CODEC.parse(ops(), json).result().orElse(Style.EMPTY);
    }

    @Override
    public JsonElement serialize(Style style, Type type, JsonSerializationContext context) {
        return Style.Serializer.CODEC.encodeStart(ops(), style).result().orElse(null);
    }
}
