package io.github.hugoneale.campat.tools;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Ports NeoForge's vanilla "extension" interfaces (IItemExtension & co.) to the runtime.
 *
 * <p>NeoForge patches vanilla classes to implement these interfaces, whose default methods are the
 * methods NeoForge adds to vanilla. The runtime ships the interfaces under their NeoForge names and
 * mixes them into the same vanilla classes. A default method whose signature (after renaming) is
 * already provided as a default by one of Forge's own extension interfaces on that class would
 * clash, so it is turned into an abstract declaration: calls through the NeoForge interface then
 * resolve to Forge's implementation, and mod overrides keep working for both.</p>
 *
 * <p>usage: ExtGen neoSrcDir neo_env.txt forge_env.txt classes.txt runtimeDir mixinConfig iface...</p>
 * The transplanted sources must already exist (tools/transplant.py); this rewrites them in place and
 * writes the mixins.
 */
public final class ExtGen {
    public static void main(String[] a) throws Exception {
        Path neoSrc = Path.of(a[0]);
        Map<String, ClassNode> neo = index(Path.of(a[1]));
        Map<String, ClassNode> forge = index(Path.of(a[2]));
        Map<String, String> renames = new HashMap<>();
        for (String l : Files.readAllLines(Path.of(a[3]))) {
            String[] p = l.split(" ");
            if (p.length == 3 && p[0].equals("C")) renames.put(p[1], p[2]);
        }
        Path runtime = Path.of(a[4]);
        Path mixinConfig = Path.of(a[5]);
        List<String> common = new ArrayList<>(), client = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        for (int i = 6; i < a.length; i++) {
            String iface = a[i].replace('.', '/');
            ClassNode e = neo.get(iface);
            if (e == null) { System.err.println("unknown interface " + iface); continue; }
            // vanilla classes NeoForge patches to implement the interface
            List<String> targets = new ArrayList<>();
            for (ClassNode cn : neo.values()) {
                if ((cn.name.startsWith("net/minecraft/") || cn.name.startsWith("com/mojang/")) && cn.interfaces.contains(iface)) targets.add(cn.name);
            }
            targets.sort(null);
            // default methods of Forge interfaces visible on any target
            Set<String> forgeDefaults = new HashSet<>();
            for (String t : targets) collectDefaults(forge, t, forgeDefaults, new HashSet<>(), false);
            Set<String> clash = new HashSet<>();
            for (MethodNode mn : e.methods) {
                if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0 || mn.name.startsWith("<")) continue;
                if ((mn.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
                String mapped = mn.name + mapDesc(mn.desc, renames, iface);
                if (forgeDefaults.contains(mapped)) clash.add(mn.name + mn.desc);
            }
            Path src = runtime.resolve(iface + ".java");
            if (!Files.exists(src)) { System.err.println("not transplanted: " + src); continue; }
            String text = Files.readString(src);
            String edited = abstractify(text, iface, e, clash);
            Files.writeString(src, edited);
            boolean isClient = iface.contains("/client/");
            for (String t : targets) {
                String simple = t.substring(t.lastIndexOf('/') + 1).replace('$', '_');
                String eSimple = iface.substring(iface.lastIndexOf('/') + 1);
                String mixinName = "ext." + (isClient ? "client." : "") + eSimple + "_" + simple + "Mixin";
                Path mixin = runtime.resolve("io/github/hugoneale/campat/runtime/mixin/" + mixinName.replace('.', '/') + ".java");
                Files.createDirectories(mixin.getParent());
                String pkg = "io.github.hugoneale.campat.runtime.mixin.ext" + (isClient ? ".client" : "");
                String targetRef = t.replace('/', '.').replace('$', '.');
                boolean targetIsInterface = forge.get(t) != null && (forge.get(t).access & Opcodes.ACC_INTERFACE) != 0;
                boolean privateTarget = forge.get(t) != null && (forge.get(t).access & Opcodes.ACC_PUBLIC) == 0;
                String annotation = privateTarget ? "@Mixin(targets = \"" + t.replace('/', '.') + "\", remap = false)" : "@Mixin(value = " + targetRef + ".class, remap = false)";
                Files.writeString(mixin, """
                    package %s;

                    import org.spongepowered.asm.mixin.Mixin;

                    /** Generated by :tools ExtGen - NeoForge patches %s to implement %s. */
                    %s
                    public %s %s %s %s {}
                    """.formatted(pkg, simple, eSimple, annotation, targetIsInterface ? "interface" : "abstract class",
                        mixinName.substring(mixinName.lastIndexOf('.') + 1), targetIsInterface ? "extends" : "implements", iface.replace('/', '.')));
                (isClient ? client : common).add(mixinName);
            }
            report.append(iface).append(": targets=").append(targets).append(" clashing defaults made abstract=").append(clash.size()).append('\n');
        }
        updateMixinConfig(mixinConfig, common, client);
        System.out.print(report);
    }

    private static void collectDefaults(Map<String, ClassNode> env, String c, Set<String> out, Set<String> seen, boolean asInterface) {
        if (c == null || !seen.add(c)) return;
        ClassNode cn = env.get(c);
        if (cn == null) return;
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0 && !mn.name.startsWith("<")) out.add(mn.name + mn.desc);
            }
        }
        collectDefaults(env, cn.superName, out, seen, false);
        for (String i : cn.interfaces) collectDefaults(env, i, out, seen, true);
    }

    private static final Pattern OBJ = Pattern.compile("L([^;]+);");

    private static String mapDesc(String desc, Map<String, String> renames, String keep) {
        Matcher m = OBJ.matcher(desc);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String n = m.group(1);
            String r = n.equals(keep) ? n : renames.getOrDefault(n, n);
            m.appendReplacement(sb, Matcher.quoteReplacement("L" + r + ";"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Removes the body (and default modifier) of clashing default methods, located with javac's parser. */
    private static String abstractify(String text, String iface, ClassNode cls, Set<String> clash) throws IOException {
        if (clash.isEmpty()) return text;
        // erased parameter lists by name, in class file order, for matching against source declarations
        Map<String, List<String>> descsByName = new LinkedHashMap<>();
        for (MethodNode mn : cls.methods) descsByName.computeIfAbsent(mn.name, k -> new ArrayList<>()).add(mn.desc);

        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        JavaFileObject fo = new SimpleJavaFileObject(URI.create("string:///" + iface + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return text; }
        };
        JavacTask task = (JavacTask) jc.getTask(null, null, d -> {}, List.of("-proc:none"), null, List.of(fo));
        CompilationUnitTree unit = task.parse().iterator().next();
        SourcePositions pos = Trees.instance(task).getSourcePositions();
        List<long[]> edits = new ArrayList<>(); // start of method, end of method, start of body
        for (Tree t : unit.getTypeDecls()) {
            if (!(t instanceof ClassTree ct)) continue;
            Map<String, Integer> seenByName = new HashMap<>();
            for (Tree m : ct.getMembers()) {
                if (!(m instanceof MethodTree mt) || mt.getBody() == null) continue;
                String name = mt.getName().toString();
                int idx = seenByName.merge(name, 1, Integer::sum) - 1;
                List<String> descs = descsByName.getOrDefault(name, List.of());
                // pick the class file overload with the same arity, in declaration order
                String desc = null;
                int arityIdx = 0;
                for (String d : descs) {
                    if (Type.getArgumentTypes(d).length == mt.getParameters().size()) {
                        if (arityMatchIndex(ct, mt, name) == arityIdx) { desc = d; break; }
                        arityIdx++;
                    }
                }
                if (desc == null || !clash.contains(name + desc)) continue;
                if (!mt.getModifiers().getFlags().contains(Modifier.DEFAULT)) continue;
                edits.add(new long[]{pos.getStartPosition(unit, mt.getModifiers()), pos.getStartPosition(unit, mt.getBody()), pos.getEndPosition(unit, mt.getBody())});
            }
        }
        edits.sort((x, y) -> Long.compare(y[0], x[0]));
        StringBuilder sb = new StringBuilder(text);
        for (long[] e : edits) {
            sb.replace((int) e[1], (int) e[2], ";");
            String head = sb.substring((int) e[0], (int) e[1]);
            String newHead = head.replaceFirst("\\bdefault\\s+", "");
            sb.replace((int) e[0], (int) e[1], newHead);
        }
        return sb.toString();
    }

    /** index of {@code mt} among same-named, same-arity methods in declaration order */
    private static int arityMatchIndex(ClassTree ct, MethodTree mt, String name) {
        int i = 0;
        for (Tree m : ct.getMembers()) {
            if (m instanceof MethodTree o && o.getName().contentEquals(name) && o.getParameters().size() == mt.getParameters().size()) {
                if (o == mt) return i;
                i++;
            }
        }
        return -1;
    }

    private static void updateMixinConfig(Path cfg, List<String> common, List<String> client) throws IOException {
        String s = Files.readString(cfg);
        s = merge(s, "\"mixins\": [", common);
        s = merge(s, "\"client\": [", client);
        Files.writeString(cfg, s);
    }

    private static String merge(String json, String key, List<String> add) {
        int start = json.indexOf(key);
        int end = json.indexOf(']', start);
        String body = json.substring(start + key.length(), end);
        Set<String> entries = new java.util.TreeSet<>();
        for (String e : body.split(",")) if (!e.isBlank()) entries.add(e.strip().replace("\"", ""));
        entries.addAll(add);
        StringBuilder sb = new StringBuilder();
        for (String e : entries) sb.append(sb.length() == 0 ? "\n    \"" : ",\n    \"").append(e).append('"');
        return json.substring(0, start + key.length()) + sb + "\n  " + json.substring(end);
    }

    private static Map<String, ClassNode> index(Path envList) throws IOException {
        Map<String, ClassNode> out = new HashMap<>();
        for (String l : Files.readAllLines(envList)) {
            if (l.isBlank()) continue;
            try (ZipFile z = new ZipFile(l.strip())) {
                var en = z.entries();
                while (en.hasMoreElements()) {
                    var e = en.nextElement();
                    String n = e.getName();
                    if (!n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                    String cn = n.substring(0, n.length() - 6);
                    if (out.containsKey(cn)) continue;
                    if (!(cn.startsWith("net/") || cn.startsWith("com/mojang/"))) continue;
                    try (InputStream in = z.getInputStream(e)) {
                        ClassNode node = new ClassNode();
                        new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_CODE);
                        out.put(cn, node);
                    } catch (RuntimeException ignored) {}
                }
            }
        }
        return out;
    }
}
