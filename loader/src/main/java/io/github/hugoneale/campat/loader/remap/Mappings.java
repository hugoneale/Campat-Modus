package io.github.hugoneale.campat.loader.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The NeoForge -> Forge translation tables.
 *
 * <p>All owners and descriptors in this table are written in <b>NeoForge</b> names. The class
 * rename table is applied last, so redirect targets may freely use NeoForge type names in their
 * descriptors; they get remapped together with the rest of the class.</p>
 *
 * <p>Line formats (whitespace separated, {@code #} starts a comment):</p>
 * <pre>
 * C  neoClass  forgeClass                         rename a class
 * F  neoOwner  name  newName                      rename a field (and all references to it)
 * M  neoOwner  name  desc  newName                rename a method, including overrides in mod classes
 * R  neoOwner  name  desc  targetOwner  targetName  [targetDesc]
 *                                                 redirect a call site to a static helper
 * S  neoOwner  name  desc  targetOwner  targetName  [targetDesc]
 *                                                 redirect a field read (GETSTATIC/GETFIELD) to a static helper
 * N  srgName  newName                             rename an SRG member (f_123_/m_123_) wherever it appears;
 *                                                 SRG ids are unique per field / method family, so no owner
 * T  owner  field  neoDesc  forgeDesc  [adapter]  a vanilla field Forge patched to a subtype: mixin shadows and
 *                                                 accessors are retyped; the adapter (static owner.name(desc))
 *                                                 turns values written through setters/shadows into Forge's type
 * I  class / K  class                              the class became an interface / the interface became a class:
 *                                                 call opcodes are fixed up
 * A  owner  oldDesc  newDesc  adapterOwner  adapterName
 *                                                 a constructor whose parameters changed: every call (also super(...)
 *                                                 from subclasses) passes the old arguments to the static adapter,
 *                                                 which returns the new arguments as an Object[] (primitives boxed)
 * Y  type  helperOwner  helperName                casts (CHECKCAST) to the type call the static helper (Object)type instead
 * X  target  member  helperOwner  helperName       a mixin accessor / invoker of a member that is gone: calls of
 *                                                 static accessors targeting it go to the static helper instead
 * D  owner  name  desc  helperOwner  helperName
 *                                                 an abstract method the platform type gained: mod classes that extend
 *                                                 it get an implementation calling the static helper(self, args...)
 * V  owner  name  oldDesc  newName  newDesc  callMap  bridgeMap
 *                                                 a virtual method whose signature evolved; see {@link EvolutionPass}
 * </pre>
 * For {@code R} without an explicit target descriptor the helper descriptor is derived: static
 * methods keep their descriptor, instance methods take the receiver as an extra first argument and
 * constructors ({@code <init>}) return the constructed type.
 */
public final class Mappings {
    public record MemberKey(String owner, String name, String desc) {}

    public record Redirect(String targetOwner, String targetName, String targetDesc) {}

    public record DefaultImpl(String owner, String name, String desc, String helperOwner, String helperName) {}

    public record CtorAdapter(String newDesc, String adapterOwner, String adapterName) {}

    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> fieldRenames = new HashMap<>(); // owner.name -> newName
    private final Map<MemberKey, String> methodRenames = new HashMap<>();
    private final Map<MemberKey, Redirect> methodRedirects = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<MemberKey, Redirect> fieldRedirects = new HashMap<>();
    private final Map<MemberKey, CtorAdapter> ctorAdapters = new HashMap<>();
    private final List<DefaultImpl> defaultImpls = new ArrayList<>();
    private final Map<String, Redirect> accessorReplacements = new HashMap<>(); // target.member -> helper
    private final Map<String, Redirect> castRedirects = new HashMap<>();
    /** owners that have any member entry, so lookups can be skipped quickly */
    private final Set<String> memberOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, String> globalNames = new HashMap<>();
    private final Map<String, List<Evolution>> evolutions = new HashMap<>(); // by old name
    private final Map<String, Boolean> kindChanges = new HashMap<>(); // class -> now an interface?
    private final Map<String, String[]> fieldRetypes = new HashMap<>(); // owner.name -> {neoDesc, forgeDesc}

    /** A virtual method whose signature changed; maps are described in {@link EvolutionPass}. */
    public record Evolution(String owner, String name, String oldDesc, String newName, String newDesc, String callMap, String bridgeMap) {}

    public static Mappings load(Collection<InputStream> sources) throws IOException {
        Mappings m = new Mappings();
        for (InputStream in : sources) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int no = 0;
                while ((line = r.readLine()) != null) {
                    no++;
                    int hash = line.indexOf('#');
                    if (hash >= 0) line = line.substring(0, hash);
                    line = line.strip();
                    if (line.isEmpty()) continue;
                    String[] p = line.split("\\s+");
                    try {
                        m.parse(p);
                    } catch (RuntimeException e) {
                        throw new IOException("Bad mapping line " + no + ": " + line, e);
                    }
                }
            }
        }
        return m;
    }

    private void parse(String[] p) {
        switch (p[0]) {
            case "C" -> classes.put(p[1], p[2]);
            case "F" -> { fieldRenames.put(p[1] + "." + p[2], p[3]); memberOwners.add(p[1]); }
            case "M" -> { methodRenames.put(new MemberKey(p[1], p[2], p[3]), p[4]); memberOwners.add(p[1]); }
            case "R" -> {
                methodRedirects.put(new MemberKey(p[1], p[2], p[3]), new Redirect(p[4], p[5], p.length > 6 ? p[6] : null));
                memberOwners.add(p[1]);
            }
            case "S" -> {
                fieldRedirects.put(new MemberKey(p[1], p[2], p[3]), new Redirect(p[4], p[5], p.length > 6 ? p[6] : null));
                memberOwners.add(p[1]);
            }
            case "Y" -> castRedirects.put(p[1], new Redirect(p[2], p[3], "(Ljava/lang/Object;)L" + p[1] + ";"));
            case "X" -> accessorReplacements.put(p[1] + "." + p[2], new Redirect(p[3], p[4], null));
            case "D" -> defaultImpls.add(new DefaultImpl(p[1], p[2], p[3], p[4], p[5]));
            case "A" -> ctorAdapters.put(new MemberKey(p[1], "<init>", p[2]), new CtorAdapter(p[3], p[4], p[5]));
            case "N" -> globalNames.put(p[1], p[2]);
            case "T" -> fieldRetypes.put(p[1] + "." + p[2], new String[]{p[3], p[4], p.length > 5 ? p[5] : null});
            case "I" -> kindChanges.put(p[1], true);
            case "K" -> kindChanges.put(p[1], false);
            case "V" -> evolutions.computeIfAbsent(p[2], k -> new ArrayList<>()).add(new Evolution(p[1], p[2], p[3], p[4], p[5], p[6], p[7]));
            default -> throw new IllegalArgumentException("Unknown entry type " + p[0]);
        }
    }

    /** Drop class renames for classes that the runtime provides itself under their NeoForge name. */
    public void removeClassRenames(Predicate<String> provided) {
        classes.keySet().removeIf(provided);
    }

    public Map<String, String> classes() {
        return classes;
    }

    public String mapClass(String neo) {
        String m = classes.get(neo);
        if (m != null) return m;
        // inner classes of renamed outers that are not listed themselves
        int d = neo.lastIndexOf('$');
        if (d > 0) {
            String outer = mapClass(neo.substring(0, d));
            if (!outer.equals(neo.substring(0, d))) return outer + neo.substring(d);
        }
        return neo;
    }

    /** @return the new name of an SRG member, or {@code null} */
    public String globalName(String srg) {
        return globalNames.isEmpty() ? null : globalNames.get(srg);
    }

    /** @return {neoDesc, forgeDesc} for a field Forge retyped, or null */
    public String[] fieldRetype(String owner, String name) {
        return fieldRetypes.isEmpty() ? null : fieldRetypes.get(owner + "." + name);
    }

    public boolean hasFieldRetypes() {
        return !fieldRetypes.isEmpty();
    }

    /** @return TRUE if the class is an interface now but was not, FALSE for the reverse, else null */
    public Boolean kindChange(String owner) {
        return kindChanges.isEmpty() ? null : kindChanges.get(owner);
    }

    public List<Evolution> evolutions(String name) {
        return evolutions.getOrDefault(name, List.of());
    }

    public boolean hasEvolutions() {
        return !evolutions.isEmpty();
    }

    public boolean hasGlobalNames() {
        return !globalNames.isEmpty();
    }

    public boolean hasMembers(String owner) {
        return memberOwners.contains(owner);
    }

    public String fieldRename(String owner, String name) {
        return fieldRenames.get(owner + "." + name);
    }

    public String methodRename(String owner, String name, String desc) {
        return methodRenames.get(new MemberKey(owner, name, desc));
    }

    public Redirect methodRedirect(String owner, String name, String desc) {
        return methodRedirects.get(new MemberKey(owner, name, desc));
    }

    /**
     * A mod's static mixin accessor {@code accessor.method desc} reads / calls {@code target.member};
     * if that member is emulated ({@code X} line) calls of the accessor are redirected.
     */
    public void registerAccessor(String accessor, String method, String desc, String target, String member) {
        Redirect r = accessorReplacements.get(target + "." + member);
        if (r == null) return;
        methodRedirects.put(new MemberKey(accessor, method, desc), r);
        memberOwners.add(accessor);
    }

    public Redirect castRedirect(String type) {
        return castRedirects.isEmpty() ? null : castRedirects.get(type);
    }

    public boolean hasAccessorReplacements() {
        return !accessorReplacements.isEmpty();
    }

    public List<DefaultImpl> defaultImpls() {
        return defaultImpls;
    }

    public CtorAdapter ctorAdapter(String owner, String desc) {
        return ctorAdapters.isEmpty() ? null : ctorAdapters.get(new MemberKey(owner, "<init>", desc));
    }

    public Redirect fieldRedirect(String owner, String name, String desc) {
        return fieldRedirects.get(new MemberKey(owner, name, desc));
    }

    public Set<MemberKey> renamedMethods() {
        return methodRenames.keySet();
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add(classes.size() + " class renames");
        out.add(fieldRenames.size() + " field renames");
        out.add(methodRenames.size() + " method renames");
        out.add(methodRedirects.size() + " method redirects");
        out.add(fieldRedirects.size() + " field redirects");
        out.add(globalNames.size() + " SRG names");
        return out;
    }
}
