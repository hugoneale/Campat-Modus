package io.github.hugoneale.campat.loader.remap;

import org.objectweb.asm.commons.Remapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASM remapper that applies the class rename table plus field/method renames. Member renames are
 * resolved through the class hierarchy so that overrides and calls through subclasses follow.
 */
public final class NeoRemapper extends Remapper {
    private final Mappings mappings;
    private final ClassHierarchy hierarchy;
    private final Map<String, String> methodCache = new ConcurrentHashMap<>();
    private final Map<String, String> fieldCache = new ConcurrentHashMap<>();

    public NeoRemapper(Mappings mappings, ClassHierarchy hierarchy) {
        this.mappings = mappings;
        this.hierarchy = hierarchy;
    }

    @Override
    public String map(String internalName) {
        return mappings.mapClass(internalName);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.startsWith("<")) return name;
        String srg = mappings.globalName(name);
        if (srg != null) return srg;
        if (owner.startsWith("[")) return name;
        String key = owner + "." + name + descriptor;
        return methodCache.computeIfAbsent(key, k -> {
            String[] found = new String[1];
            hierarchy.find(owner, c -> {
                if (!mappings.hasMembers(c)) return false;
                String r = mappings.methodRename(c, name, descriptor);
                if (r != null) found[0] = r;
                return r != null;
            });
            return found[0] != null ? found[0] : name;
        });
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String srg = mappings.globalName(name);
        if (srg != null) return srg;
        String key = owner + "." + name;
        return fieldCache.computeIfAbsent(key, k -> {
            String[] found = new String[1];
            hierarchy.find(owner, c -> {
                if (!mappings.hasMembers(c)) return false;
                String r = mappings.fieldRename(c, name);
                if (r != null) found[0] = r;
                return r != null;
            });
            return found[0] != null ? found[0] : name;
        });
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        // lambdas implementing a Minecraft functional interface name its SRG method
        String srg = mappings.globalName(name);
        return srg != null ? srg : name;
    }

    private static final java.util.regex.Pattern SRG = java.util.regex.Pattern.compile("\\b[fm]_\\d+_\\b");
    private static final java.util.regex.Pattern TYPE = java.util.regex.Pattern.compile("L([a-zA-Z_$][\\w$]*(?:/[\\w$]+)+);");

    /** Remaps every {@code L...;} type and bare internal/binary class names inside a string. */
    public String mapStringReferences(String s) {
        if (s == null || s.isEmpty()) return s;
        if (mappings.hasGlobalNames()) return mapLegacyString(s);
        if (!s.contains("neoforged")) return s;
        // exact class name in internal or binary form
        String internal = s.replace('.', '/');
        String mapped = mappings.classes().get(internal);
        if (mapped != null) return s.indexOf('/') >= 0 ? mapped : mapped.replace('/', '.');
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int l = s.indexOf("Lnet/neoforged/", i);
            if (l < 0) break;
            int semi = s.indexOf(';', l);
            if (semi < 0) break;
            sb.append(s, i, l + 1);
            sb.append(map(s.substring(l + 1, semi)));
            i = semi;
        }
        sb.append(s, i, s.length());
        return sb.toString();
    }

    /** Legacy (SRG) strings: reflection names, mixin targets, refmap entries, AT lines, coremods. */
    private String mapLegacyString(String s) {
        String internal = s.replace('.', '/');
        String mapped = mappings.classes().get(internal);
        if (mapped == null && internal.indexOf('$') > 0) {
            String m = mappings.mapClass(internal);
            if (!m.equals(internal)) mapped = m;
        }
        if (mapped != null && !s.contains(";")) return s.indexOf('/') >= 0 ? mapped : mapped.replace('/', '.');
        String out = s;
        if (out.indexOf(';') >= 0) out = TYPE.matcher(out).replaceAll(r -> java.util.regex.Matcher.quoteReplacement("L" + map(r.group(1)) + ";"));
        if (out.indexOf('_') >= 0) {
            out = SRG.matcher(out).replaceAll(r -> {
                String n = mappings.globalName(r.group());
                return java.util.regex.Matcher.quoteReplacement(n != null ? n : r.group());
            });
        }
        return out;
    }
}
