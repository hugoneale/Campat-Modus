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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minecraft 1.20.1 resources -> 1.21.1: data pack layout, data JSON formats and SRG names in text files. */
final class LegacyResources {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    /** 1.21 made the data pack registry folders singular. */
    private static final Map<String, String> DIRS = Map.ofEntries(
        Map.entry("advancements", "advancement"), Map.entry("recipes", "recipe"), Map.entry("loot_tables", "loot_table"),
        Map.entry("predicates", "predicate"), Map.entry("item_modifiers", "item_modifier"), Map.entry("functions", "function"),
        Map.entry("structures", "structure"));
    private static final Map<String, String> TAG_DIRS = Map.ofEntries(
        Map.entry("items", "item"), Map.entry("blocks", "block"), Map.entry("entity_types", "entity_type"),
        Map.entry("fluids", "fluid"), Map.entry("game_events", "game_event"), Map.entry("functions", "function"));
    private static final Pattern DATA = Pattern.compile("^data/([^/]+)/([^/]+)/(.*)$");

    private final NeoRemapper remapper;

    LegacyResources(NeoRemapper remapper) {
        this.remapper = remapper;
    }

    ResourceRewriter.Rewritten rewrite(String name, byte[] data) {
        if (name.startsWith("data/")) return rewriteData(name, data);
        if (name.equals("META-INF/accesstransformer.cfg") || name.endsWith(".js") || name.startsWith("META-INF/services/")
            || (name.endsWith(".json") && !name.contains("/"))) {
            // ATs, coremods, services, mixin configs and refmaps: class and SRG names
            String text = new String(data, StandardCharsets.UTF_8);
            String out;
            if (name.endsWith(".json")) out = rewriteRootJson(text);
            else {
                StringBuilder sb = new StringBuilder();
                for (String line : text.split("\n", -1)) sb.append(remapper.mapStringReferences(line)).append('\n');
                out = sb.substring(0, sb.length() - 1);
                if (name.equals("META-INF/accesstransformer.cfg")) out = fixAccessTransformer(out);
            }
            return new ResourceRewriter.Rewritten(name, out.getBytes(StandardCharsets.UTF_8));
        }
        return new ResourceRewriter.Rewritten(name, data);
    }

    /** AT lines name classes in binary form ({@code net.minecraft.x.Y}); class renames apply there too. */
    private String fixAccessTransformer(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String[] p = line.split("#", 2)[0].strip().split("\\s+");
            if (p.length >= 2) {
                String mapped = remapper.map(p[1].replace('.', '/')).replace('/', '.');
                if (!mapped.equals(p[1])) line = line.replace(p[1], mapped);
            }
            sb.append(line).append('\n');
        }
        return sb.substring(0, sb.length() - 1);
    }

    private String rewriteRootJson(String text) {
        try {
            JsonElement el = JsonParser.parseString(text);
            if (el.isJsonObject() && el.getAsJsonObject().has("mixins") || el.isJsonObject() && el.getAsJsonObject().has("client")
                || el.isJsonObject() && el.getAsJsonObject().has("server")) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("package")) {
                    // A mixin config. Targets that changed between versions must not abort the game:
                    // failed mixins become warnings (see the conversion report).
                    o.addProperty("required", false);
                    JsonObject inj = o.has("injectors") && o.get("injectors").isJsonObject() ? o.getAsJsonObject("injectors") : new JsonObject();
                    inj.addProperty("defaultRequire", 0);
                    o.add("injectors", inj);
                    return GSON.toJson(o);
                }
            }
            return GSON.toJson(remapStrings(el));
        } catch (RuntimeException e) {
            return remapper.mapStringReferences(text);
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

    static String dataPath(String name) {
        Matcher m = DATA.matcher(name);
        if (!m.matches()) return name;
        String ns = m.group(1), dir = m.group(2), rest = m.group(3);
        if (dir.equals("tags")) {
            int s = rest.indexOf('/');
            if (s > 0 && TAG_DIRS.containsKey(rest.substring(0, s))) rest = TAG_DIRS.get(rest.substring(0, s)) + rest.substring(s);
            return "data/" + ns + "/tags/" + rest;
        }
        String d = DIRS.get(dir);
        return d == null ? name : "data/" + ns + "/" + d + "/" + rest;
    }

    private ResourceRewriter.Rewritten rewriteData(String name, byte[] data) {
        String newName = dataPath(name);
        if (!name.endsWith(".json")) return new ResourceRewriter.Rewritten(newName, data);
        try {
            JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            String kind = newName.split("/").length > 2 ? newName.split("/")[2] : "";
            JsonElement out = convert(el, kind);
            return new ResourceRewriter.Rewritten(newName, GSON.toJson(out).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return new ResourceRewriter.Rewritten(newName, data);
        }
    }

    /** 1.20.1 data JSON -> 1.21.1. Recursive; {@code kind} is the data folder (recipe, loot_table, ...). */
    static JsonElement convert(JsonElement el, String kind) {
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : el.getAsJsonArray()) out.add(convert(e, kind));
            return out;
        }
        if (!el.isJsonObject()) return el;
        JsonObject in = el.getAsJsonObject();
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            String k = e.getKey();
            JsonElement v = e.getValue();
            if (k.equals("conditions") && v.isJsonArray() && looksLikeForgeConditions(v.getAsJsonArray())) {
                // Forge 47: list that must all pass. Forge 52: one condition object.
                JsonArray arr = convert(v, kind).getAsJsonArray();
                JsonElement cond = arr.size() == 1 ? arr.get(0) : and(arr);
                out.add("forge:condition", cond);
                continue;
            }
            if (k.equals("forge:conditions") && v.isJsonArray()) {
                JsonArray arr = convert(v, kind).getAsJsonArray();
                out.add("forge:condition", arr.size() == 1 ? arr.get(0) : and(arr));
                continue;
            }
            v = convert(v, kind);
            if (kind.equals("recipe") && k.equals("result") && v.isJsonObject() && v.getAsJsonObject().has("item")) {
                JsonObject r = v.getAsJsonObject();
                JsonObject nr = new JsonObject();
                for (Map.Entry<String, JsonElement> re : r.entrySet()) nr.add(re.getKey().equals("item") ? "id" : re.getKey(), re.getValue());
                v = nr;
            }
            if (kind.equals("recipe") && k.equals("result") && v.isJsonPrimitive() && isResultId(in)) {
                JsonObject nr = new JsonObject();
                nr.add("id", v);
                if (in.has("count")) nr.add("count", in.get("count"));
                v = nr;
            }
            if (kind.equals("advancement") && k.equals("icon") && v.isJsonObject() && v.getAsJsonObject().has("item")) {
                JsonObject r = v.getAsJsonObject();
                JsonObject nr = new JsonObject();
                for (Map.Entry<String, JsonElement> re : r.entrySet()) nr.add(re.getKey().equals("item") ? "id" : re.getKey(), re.getValue());
                v = nr;
            }
            out.add(k, v);
        }
        if (kind.equals("loot_table") || kind.equals("item_modifier")) lootFixes(out);
        // forge: common tags became c: tags in Forge 1.21
        return out;
    }

    private static boolean isResultId(JsonObject recipe) {
        // stonecutting / smithing (1.20.1): "result": "id", "count": n
        String type = recipe.has("type") && recipe.get("type").isJsonPrimitive() ? recipe.get("type").getAsString() : "";
        return type.endsWith("stonecutting") || type.endsWith("smithing_transform") || type.endsWith("smithing_trim");
    }

    private static void lootFixes(JsonObject o) {
        JsonElement fn = o.get("function");
        if (fn != null && fn.isJsonPrimitive()) {
            String f = fn.getAsString();
            if (f.equals("minecraft:set_nbt") || f.equals("set_nbt")) o.addProperty("function", "minecraft:set_custom_data");
            if (f.equals("minecraft:copy_nbt") || f.equals("copy_nbt")) o.addProperty("function", "minecraft:copy_custom_data");
        }
        JsonElement type = o.get("type");
        if (type != null && type.isJsonPrimitive() && type.getAsString().endsWith("loot_table") && o.has("name") && !o.has("value")) {
            o.add("value", o.remove("name"));
        }
    }

    private static boolean looksLikeForgeConditions(JsonArray arr) {
        for (JsonElement e : arr) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("type")) return false;
            String t = e.getAsJsonObject().get("type").getAsString();
            if (!t.contains(":")) return false;
        }
        return true;
    }

    private static JsonObject and(JsonArray arr) {
        JsonObject and = new JsonObject();
        and.addProperty("type", "forge:and");
        and.add("values", arr);
        return and;
    }

    static List<String> dataDirs() {
        return List.copyOf(DIRS.keySet());
    }
}
