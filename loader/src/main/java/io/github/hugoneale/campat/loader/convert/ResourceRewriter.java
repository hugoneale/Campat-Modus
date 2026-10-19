package io.github.hugoneale.campat.loader.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.hugoneale.campat.loader.remap.NeoRemapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites non-class resources: service files, mixin refmaps and data pack JSON whose format differs
 * between NeoForge and Forge.
 */
public final class ResourceRewriter {
    public record Rewritten(String name, byte[] data) {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern NEO_DATA_DIR = Pattern.compile("^data/([^/]+)/neoforge/(biome_modifier|structure_modifier)/(.*)$");
    /** Registry ids that exist with the same name and format under the forge namespace. */
    private static final Set<String> FORGE_EQUIVALENT_TYPES = Set.of(
        // conditions
        "and", "or", "not", "true", "false", "mod_loaded", "item_exists", "tag_empty",
        // biome/structure modifiers
        "none", "add_features", "remove_features", "add_spawns", "remove_spawns", "add_spawn_costs", "remove_spawn_costs",
        "add_carvers", "remove_carvers",
        // holder sets
        "any",
        // loot conditions / modifiers
        "loot_table_id", "can_tool_perform_action", "can_item_perform_ability"
    );

    private final NeoRemapper remapper;

    public ResourceRewriter(NeoRemapper remapper) {
        this.remapper = remapper;
    }

    /** @return the (possibly renamed / rewritten) resource, or {@code null} to drop it */
    public Rewritten rewrite(String name, byte[] data, List<String> mixinConfigs) {
        if (name.startsWith("META-INF/services/")) {
            String service = name.substring("META-INF/services/".length());
            String text = new String(data, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (String line : text.split("\n")) sb.append(remapper.mapStringReferences(line.strip())).append('\n');
            return new Rewritten("META-INF/services/" + remapper.mapStringReferences(service), sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (name.endsWith(".json") && !name.contains("/")) {
            // mixin configs and refmaps live at the jar root
            String text = new String(data, StandardCharsets.UTF_8);
            if (text.contains("net/neoforged") || text.contains("net.neoforged")) {
                return new Rewritten(name, remapJsonStrings(text).getBytes(StandardCharsets.UTF_8));
            }
            return new Rewritten(name, data);
        }
        if (name.startsWith("data/")) {
            String newName = name;
            Matcher m = NEO_DATA_DIR.matcher(name);
            if (m.matches()) newName = "data/" + m.group(1) + "/forge/" + m.group(2) + "/" + m.group(3);
            if (name.equals("data/neoforge/loot_modifiers/global_loot_modifiers.json")) newName = "data/forge/loot_modifiers/global_loot_modifiers.json";
            if (name.endsWith(".json")) {
                String text = new String(data, StandardCharsets.UTF_8);
                if (text.contains("neoforge:")) {
                    try {
                        JsonElement el = JsonParser.parseString(text);
                        JsonElement converted = convertData(el);
                        return new Rewritten(newName, GSON.toJson(converted).getBytes(StandardCharsets.UTF_8));
                    } catch (RuntimeException e) {
                        // not strict JSON (comments etc.); leave alone
                    }
                }
            }
            return new Rewritten(newName, data);
        }
        return new Rewritten(name, data);
    }

    private String remapJsonStrings(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            return GSON.toJson(remapStrings(el));
        } catch (RuntimeException e) {
            return json;
        }
    }

    private JsonElement remapStrings(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) return new JsonPrimitive(remapper.mapStringReferences(el.getAsString()));
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : el.getAsJsonArray()) out.add(remapStrings(e));
            return out;
        }
        if (el.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet())
                out.add(remapper.mapStringReferences(e.getKey()), remapStrings(e.getValue()));
            return out;
        }
        return el;
    }

    /** NeoForge data format -> Forge data format. */
    static JsonElement convertData(JsonElement el) {
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : el.getAsJsonArray()) out.add(convertData(e));
            return out;
        }
        if (!el.isJsonObject()) return el;
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            String k = e.getKey();
            JsonElement v = convertData(e.getValue());
            if (k.equals("neoforge:conditions")) {
                // NeoForge: list of conditions that must all pass. Forge: a single condition object.
                JsonElement cond = v;
                if (v.isJsonArray()) {
                    JsonArray arr = v.getAsJsonArray();
                    if (arr.size() == 1) cond = arr.get(0);
                    else {
                        JsonObject and = new JsonObject();
                        and.addProperty("type", "forge:and");
                        and.add("values", arr);
                        cond = and;
                    }
                }
                out.add("forge:condition", cond);
                continue;
            }
            if ((k.equals("type") || k.equals("condition")) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                String s = v.getAsString();
                if (s.startsWith("neoforge:") && FORGE_EQUIVALENT_TYPES.contains(s.substring(9))) v = new JsonPrimitive("forge:" + s.substring(9));
            }
            out.add(k, v);
        }
        return out;
    }

    /** Removes dropped nested jars from {@code META-INF/jarjar/metadata.json}. */
    public byte[] filterJarJarMetadata(byte[] data, List<String> droppedPrefixes) {
        try {
            JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray jars = root.getAsJsonArray("jars");
            if (jars == null) return data;
            JsonArray kept = new JsonArray();
            for (JsonElement j : jars) {
                String path = j.getAsJsonObject().has("path") ? j.getAsJsonObject().get("path").getAsString() : "";
                String simple = path.substring(path.lastIndexOf('/') + 1);
                if (droppedPrefixes.stream().noneMatch(simple::startsWith)) kept.add(j);
            }
            root.add("jars", kept);
            return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return data;
        }
    }

    static List<String> list(String... s) {
        return new ArrayList<>(List.of(s));
    }
}
