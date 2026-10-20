package io.github.hugoneale.campat.loader.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Super type lookup over the NeoForge platform (as NeoForge patches it) plus every class of the mods
 * being converted. Needed to resolve which mapping entry a call site or an override refers to, e.g.
 * a call to {@code MyItemHandler.insertItem(...)} that is declared on a NeoForge class.
 */
public final class ClassHierarchy {
    /** class -> [super, interfaces...] */
    private final Map<String, String[]> supers = new ConcurrentHashMap<>();
    /** classes from the platform index (everything else was added from mod jars) */
    private final Set<String> platform = ConcurrentHashMap.newKeySet();

    public static ClassHierarchy load(InputStream platformIndex) throws IOException {
        ClassHierarchy h = new ClassHierarchy();
        if (platformIndex != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(platformIndex, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] p = line.split(" ");
                    String[] s = new String[p.length - 1];
                    System.arraycopy(p, 1, s, 0, s.length);
                    for (int i = 0; i < s.length; i++) if (s[i].equals("-")) s[i] = null;
                    h.supers.put(p[0], s);
                    h.platform.add(p[0]);
                }
            }
        }
        return h;
    }

    public void add(String name, String superName, List<String> interfaces) {
        String[] s = new String[interfaces.size() + 1];
        s[0] = superName;
        for (int i = 0; i < interfaces.size(); i++) s[i + 1] = interfaces.get(i);
        supers.put(name, s);
    }

    /** @return the super class, or {@code null} if unknown */
    public String superClass(String name) {
        String[] s = supers.get(name);
        return s == null ? null : s[0];
    }

    public boolean isPlatform(String name) {
        return platform.contains(name);
    }

    public boolean knows(String name) {
        return supers.containsKey(name);
    }

    /**
     * Breadth first walk starting at {@code start} itself; returns the first type for which
     * {@code test} is true, or {@code null}.
     */
    public String find(String start, Predicate<String> test) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (c == null || !seen.add(c)) continue;
            if (test.test(c)) return c;
            String[] s = supers.get(c);
            if (s != null) for (String x : s) if (x != null) queue.add(x);
        }
        return null;
    }

    public boolean isSubtypeOf(String type, String parent) {
        return find(type, parent::equals) != null;
    }
}
