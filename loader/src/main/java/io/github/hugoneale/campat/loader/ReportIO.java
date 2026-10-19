package io.github.hugoneale.campat.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.hugoneale.campat.loader.convert.JarConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Stores conversion reports next to cached jars so warnings are shown on every launch. */
final class ReportIO {
    private static final Gson GSON = new Gson();

    private ReportIO() {}

    static String write(JarConverter.Report r) {
        return GSON.toJson(toJson(r));
    }

    static JarConverter.Report read(String s) {
        return fromJson(JsonParser.parseString(s).getAsJsonObject());
    }

    private static JsonObject toJson(JarConverter.Report r) {
        JsonObject o = new JsonObject();
        o.addProperty("file", r.fileName());
        o.add("mods", GSON.toJsonTree(r.modIds()));
        o.add("warnings", GSON.toJsonTree(r.warnings()));
        o.add("missing", GSON.toJsonTree(r.missingNeoClasses()));
        JsonArray nested = new JsonArray();
        for (JarConverter.Report n : r.nested()) nested.add(toJson(n));
        o.add("nested", nested);
        return o;
    }

    private static JarConverter.Report fromJson(JsonObject o) {
        List<JarConverter.Report> nested = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("nested")) nested.add(fromJson(e.getAsJsonObject()));
        return new JarConverter.Report(o.get("file").getAsString(), strings(o.getAsJsonArray("mods")),
            strings(o.getAsJsonArray("warnings")), new TreeSet<>(strings(o.getAsJsonArray("missing"))), nested);
    }

    private static List<String> strings(JsonArray a) {
        List<String> l = new ArrayList<>();
        for (JsonElement e : a) l.add(e.getAsString());
        return l;
    }
}
