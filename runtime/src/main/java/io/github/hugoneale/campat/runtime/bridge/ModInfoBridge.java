package io.github.hugoneale.campat.runtime.bridge;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NeoForge's mod properties are plain in-memory configs; Forge 52 parses mods.toml into concurrent
 * StampedConfigs, which do not support {@code valueMap()} (yumi and others walk them with it).
 */
public final class ModInfoBridge {
    private ModInfoBridge() {}

    public static Map<String, Object> getModProperties(net.minecraftforge.forgespi.language.IModInfo info) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : info.getModProperties().entrySet()) out.put(e.getKey(), plain(e.getValue()));
        return out;
    }

    private static Object plain(Object value) {
        if (value instanceof UnmodifiableConfig c) {
            Config out = Config.inMemory();
            for (UnmodifiableConfig.Entry e : c.entrySet()) out.add(Collections.singletonList(e.getKey()), plain(e.getRawValue()));
            return out;
        }
        if (value instanceof List<?> l) return l.stream().map(ModInfoBridge::plain).toList();
        return value;
    }
}
