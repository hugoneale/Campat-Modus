package io.github.hugoneale.campat.loader.convert;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a {@code META-INF/neoforge.mods.toml} into the {@code META-INF/mods.toml} format that
 * Forge 1.21.1 (FML 52) understands.
 *
 * <p>Differences handled:</p>
 * <ul>
 *   <li>{@code [[dependencies.x]] type = "required|optional|incompatible|discouraged"} becomes
 *   Forge's {@code mandatory = true|false}. Forge has no notion of incompatible mods, so those
 *   entries are dropped and reported.</li>
 *   <li>The {@code neoforge} dependency becomes a dependency on {@code forge} (any version) and every
 *   mod gets a dependency on the compatibility runtime.</li>
 *   <li>{@code [[accessTransformers]] file = ...} entries become Forge's root
 *   {@code accessTransformers = [...]} list.</li>
 *   <li>{@code [[mixins]] config = ...} entries are returned so that they can be written to the jar
 *   manifest's {@code MixinConfigs} attribute, which is how Forge discovers mixin configs.</li>
 *   <li>{@code modLoader} is forced to {@code javafml} with an unbounded loader version.</li>
 * </ul>
 */
public final class ModsTomlConverter {
    public static final String RUNTIME_MOD_ID = "campat_modus";

    public record ModEntry(String modId, String namespace) {}

    public record Result(String modsToml, List<ModEntry> mods, List<String> mixinConfigs,
                         List<String> accessTransformers, List<String> warnings,
                         List<String> enumExtensions, String originalLoader) {}

    public Result convert(String neoToml) {
        CommentedConfig cfg = new TomlParser().parse(neoToml);
        List<String> warnings = new ArrayList<>();
        List<String> mixins = new ArrayList<>();
        List<String> ats = new ArrayList<>();
        List<String> enumExtensions = new ArrayList<>();

        String originalLoader = str(cfg.get("modLoader"), "javafml");
        if (!"javafml".equals(originalLoader) && !"lowcodefml".equals(originalLoader)) {
            warnings.add("Unsupported modLoader '" + originalLoader + "', treating it as javafml");
        }
        CommentedConfig out = TomlFormat.newConfig(LinkedHashMap::new);
        // lowcodefml mods (resource only) are loaded fine by javafml as long as they get an entrypoint,
        // which the converter always generates.
        out.set("modLoader", "javafml");
        out.set("loaderVersion", "*");
        out.set("license", str(cfg.get("license"), "All Rights Reserved"));
        copy(cfg, out, "issueTrackerURL");
        copy(cfg, out, "showAsResourcePack");
        copy(cfg, out, "showAsDataPack");
        copy(cfg, out, "clientSideOnly");
        copy(cfg, out, "services");
        copy(cfg, out, "properties");
        copy(cfg, out, "logoFile");

        // [[mixins]]
        for (Config m : configList(cfg, "mixins")) {
            String c = m.get("config");
            if (c != null) mixins.add(c);
        }
        // [[accessTransformers]]
        for (Config a : configList(cfg, "accessTransformers")) {
            String f = a.get("file");
            if (f != null) ats.add(f);
        }
        if (!ats.isEmpty()) out.set("accessTransformers", new ArrayList<>(ats));

        List<ModEntry> modEntries = new ArrayList<>();
        List<Config> outMods = new ArrayList<>();
        for (Config m : configList(cfg, "mods")) {
            String modId = m.get("modId");
            if (modId == null) {
                warnings.add("Skipping [[mods]] entry without modId");
                continue;
            }
            Config om = out.createSubConfig();
            for (Map.Entry<String, Object> e : m.valueMap().entrySet()) {
                String k = e.getKey();
                switch (k) {
                    case "enumExtensions" -> enumExtensions.add(String.valueOf(e.getValue()));
                    case "featureFlags" -> warnings.add(modId + ": feature flag files are not supported on Forge and were ignored");
                    default -> om.set(k, e.getValue());
                }
            }
            outMods.add(om);
            modEntries.add(new ModEntry(modId, str(m.get("namespace"), modId)));
        }
        out.set("mods", outMods);

        // [[dependencies.<modid>]] and [modproperties.<modid>]
        Config deps = out.createSubConfig();
        UnmodifiableConfig inDeps = cfg.get("dependencies");
        for (ModEntry mod : modEntries) {
            List<Config> outList = new ArrayList<>();
            boolean hasForge = false;
            if (inDeps != null) {
                for (Config d : asConfigList(inDeps.get(mod.modId()))) {
                    Config od = convertDependency(mod.modId(), d, out, warnings);
                    if (od == null) continue;
                    if ("forge".equals(od.get("modId"))) {
                        if (hasForge) continue;
                        hasForge = true;
                    }
                    outList.add(od);
                }
            }
            Config runtimeDep = out.createSubConfig();
            runtimeDep.set("modId", RUNTIME_MOD_ID);
            runtimeDep.set("mandatory", true);
            runtimeDep.set("versionRange", "*");
            runtimeDep.set("ordering", "AFTER");
            runtimeDep.set("side", "BOTH");
            outList.add(runtimeDep);
            deps.set(List.of(mod.modId()), outList);
        }
        out.set("dependencies", deps);
        copy(cfg, out, "modproperties");

        String text = new TomlWriter().writeToString(out);
        return new Result(text, modEntries, mixins, ats, warnings, enumExtensions, originalLoader);
    }

    private static Config convertDependency(String owner, Config d, Config factory, List<String> warnings) {
        String depId = d.get("modId");
        if (depId == null) return null;
        String type = d.get("type");
        Object mandatoryFlag = d.get("mandatory"); // some mods still use the old key
        boolean mandatory;
        if (type == null) {
            mandatory = !(mandatoryFlag instanceof Boolean b) || b;
        } else {
            switch (type.toLowerCase()) {
                case "required" -> mandatory = true;
                case "optional" -> mandatory = false;
                case "incompatible", "discouraged" -> {
                    warnings.add(owner + ": Forge cannot express '" + type + "' dependency on '" + depId + "', ignored"
                        + (d.get("reason") != null ? " (reason: " + d.get("reason") + ")" : ""));
                    return null;
                }
                default -> {
                    warnings.add(owner + ": unknown dependency type '" + type + "' for '" + depId + "', treated as optional");
                    mandatory = false;
                }
            }
        }
        Config od = factory.createSubConfig();
        if ("neoforge".equals(depId)) {
            od.set("modId", "forge");
            od.set("mandatory", true);
            od.set("versionRange", "*");
            od.set("ordering", "NONE");
            od.set("side", "BOTH");
            return od;
        }
        od.set("modId", depId);
        od.set("mandatory", mandatory);
        String range = str(d.get("versionRange"), "*");
        od.set("versionRange", range.isBlank() ? "*" : range);
        String ordering = str(d.get("ordering"), "NONE").toUpperCase(java.util.Locale.ROOT);
        od.set("ordering", ordering.equals("BEFORE") || ordering.equals("AFTER") ? ordering : "NONE");
        String side = str(d.get("side"), "BOTH").toUpperCase(java.util.Locale.ROOT);
        od.set("side", side.equals("CLIENT") || side.equals("SERVER") ? side : "BOTH");
        Object referral = d.get("referralUrl");
        if (referral != null) od.set("referralUrl", referral);
        return od;
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static void copy(UnmodifiableConfig from, Config to, String key) {
        Object v = from.get(key);
        if (v != null) to.set(key, v);
    }

    private static List<Config> configList(UnmodifiableConfig cfg, String key) {
        return asConfigList(cfg.get(key));
    }

    @SuppressWarnings("unchecked")
    private static List<Config> asConfigList(Object o) {
        if (o instanceof List<?> l) {
            List<Config> r = new ArrayList<>();
            for (Object e : l) if (e instanceof Config c) r.add(c);
            return r;
        }
        if (o instanceof Config c) return List.of(c);
        return List.of();
    }
}
