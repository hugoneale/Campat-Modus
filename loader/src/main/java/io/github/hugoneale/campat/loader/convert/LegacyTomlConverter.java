package io.github.hugoneale.campat.loader.convert;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rewrites the {@code mods.toml} of a Forge (or NeoForge 47) mod for Minecraft 1.20.1 so that Forge
 * 1.21.1 accepts it: version ranges of Minecraft, Forge and the loader are opened up, NeoForge
 * dependencies become Forge ones and every mod depends on the compatibility runtime.
 */
public final class LegacyTomlConverter {
    public record Result(String modsToml, List<String> modIds, List<String> warnings) {}

    /** Minecraft version the legacy layer translates from. */
    public static final String FROM = "1.20.1";
    public static final String TO = "1.21.1";

    /** Range check only: does the mods.toml accept Minecraft 1.20.1? */
    public static boolean accepts1201(String toml) {
        try {
            CommentedConfig cfg = new TomlParser().parse(toml);
            UnmodifiableConfig deps = cfg.get("dependencies");
            if (deps == null) return true;
            for (Object list : deps.valueMap().values()) {
                for (Config d : asList(list)) {
                    Object id = d.get("modId"), range = d.get("versionRange");
                    if ("minecraft".equals(id) && range != null) return VersionRanges.contains(range.toString(), FROM);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** SRG member names (f_123_ / m_123_) only appear in mods compiled for Forge 1.20.x and older. */
    public static boolean hasSrgNames(byte[] classFile) {
        String s = new String(classFile, java.nio.charset.StandardCharsets.ISO_8859_1);
        int i = 0;
        while ((i = s.indexOf('_', i)) >= 0) {
            if (i > 0 && (s.charAt(i - 1) == 'm' || s.charAt(i - 1) == 'f') && (i < 2 || !Character.isLetterOrDigit(s.charAt(i - 2)))) {
                int j = i + 1;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                if (j - i > 3 && j < s.length() && s.charAt(j) == '_') return true;
            }
            i++;
        }
        return false;
    }

    /** @return whether a mods.toml declares a Minecraft 1.20.1 mod (and not a 1.21.1 one) */
    public static boolean isLegacy(String toml) {
        CommentedConfig cfg;
        try {
            cfg = new TomlParser().parse(toml);
        } catch (RuntimeException e) {
            return false;
        }
        String mc = null, forge = null;
        UnmodifiableConfig deps = cfg.get("dependencies");
        if (deps != null) {
            for (Object list : deps.valueMap().values()) {
                for (Config d : asList(list)) {
                    String id = d.get("modId");
                    Object range = d.get("versionRange");
                    if (range == null) continue;
                    if ("minecraft".equals(id)) mc = range.toString();
                    else if ("forge".equals(id) || "neoforge".equals(id)) forge = range.toString();
                }
            }
        }
        if (mc != null && !mc.isBlank() && !mc.equals("*")) return VersionRanges.contains(mc, FROM) && !VersionRanges.contains(mc, TO);
        String loader = cfg.get("loaderVersion") instanceof Object o ? o.toString() : null;
        if (forge != null && !forge.isBlank() && !forge.equals("*")) return VersionRanges.contains(forge, "47.2.0") && !VersionRanges.contains(forge, "52.1.0");
        return loader != null && VersionRanges.contains(loader, "47") && !VersionRanges.contains(loader, "52");
    }

    public Result convert(String toml) {
        CommentedConfig cfg = new TomlParser().parse(toml);
        List<String> warnings = new ArrayList<>();
        List<String> modIds = new ArrayList<>();
        cfg.set("loaderVersion", "*");
        Object loader = cfg.get("modLoader");
        if (!"javafml".equals(String.valueOf(loader)) && !"lowcodefml".equals(String.valueOf(loader))) {
            warnings.add("unsupported modLoader " + cfg.get("modLoader") + ", treated as javafml");
            cfg.set("modLoader", "javafml");
        }
        for (Config m : asList(cfg.get("mods"))) {
            Object id = m.get("modId");
            if (id != null) modIds.add(id.toString());
        }
        Config deps = cfg.get("dependencies");
        if (deps == null) {
            deps = cfg.createSubConfig();
            cfg.set("dependencies", deps);
        }
        for (String id : modIds) {
            List<Config> list = new ArrayList<>(asList(deps.get(List.of(id))));
            List<Config> out = new ArrayList<>();
            boolean hasForge = false;
            for (Config d : list) {
                Object depId = d.get("modId");
                String dep = String.valueOf(depId);
                switch (dep.toLowerCase(Locale.ROOT)) {
                    case "minecraft" -> d.set("versionRange", "*");
                    case "forge", "neoforge" -> {
                        if (hasForge) continue;
                        hasForge = true;
                        d.set("modId", "forge");
                        d.set("versionRange", "*");
                    }
                    default -> { }
                }
                Object type = d.get("type"); // NeoForge 47 style is still mandatory in 1.20.1, but be lenient
                if (type != null && d.get("mandatory") == null) {
                    d.set("mandatory", "required".equalsIgnoreCase(type.toString()));
                    d.remove("type");
                }
                if (d.get("mandatory") == null) d.set("mandatory", true);
                out.add(d);
            }
            Config runtime = cfg.createSubConfig();
            runtime.set("modId", ModsTomlConverter.RUNTIME_MOD_ID);
            runtime.set("mandatory", true);
            runtime.set("versionRange", "*");
            runtime.set("ordering", "AFTER");
            runtime.set("side", "BOTH");
            out.add(runtime);
            deps.set(List.of(id), out);
        }
        return new Result(new TomlWriter().writeToString(cfg), modIds, warnings);
    }

    private static List<Config> asList(Object o) {
        if (o instanceof List<?> l) {
            List<Config> r = new ArrayList<>();
            for (Object e : l) if (e instanceof Config c) r.add(c);
            return r;
        }
        if (o instanceof Config c) return List.of(c);
        return List.of();
    }
}
