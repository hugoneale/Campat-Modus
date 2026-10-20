package io.github.hugoneale.campat.loader.convert;

import io.github.hugoneale.campat.loader.remap.ClassConverter;
import io.github.hugoneale.campat.loader.remap.ClassHierarchy;
import io.github.hugoneale.campat.loader.remap.Mappings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Converts a Forge (or NeoForge 47) mod built for Minecraft 1.20.1 into a Forge 1.21.1 mod: SRG
 * member names become 1.21.1 Mojang names, renamed classes follow, removed APIs are redirected to
 * the runtime's emulation helpers, and the data pack layout and formats are updated.
 */
public final class LegacyJarConverter {
    private final ClassConverter classConverter;
    private final LegacyResources resources;

    private final Mappings mappings;

    public LegacyJarConverter(Mappings mappings, ClassHierarchy hierarchy) {
        this.mappings = mappings;
        this.classConverter = new ClassConverter(mappings, hierarchy);
        this.resources = new LegacyResources(classConverter.remapper());
    }

    public static boolean isLegacyMod(Map<String, byte[]> entries) {
        byte[] toml = entries.get(JarConverter.FORGE_TOML);
        if (toml == null || entries.containsKey(JarConverter.NEO_TOML)) return false;
        String text = new String(toml, StandardCharsets.UTF_8);
        if (LegacyTomlConverter.isLegacy(text)) return true;
        if (!LegacyTomlConverter.accepts1201(text)) return false;
        for (var e : entries.entrySet()) {
            if (e.getKey().endsWith(".class") && LegacyTomlConverter.hasSrgNames(e.getValue())) return true;
        }
        return false;
    }

    /** static mixin accessors of members 1.21.1 no longer has get redirected to emulations (X mapping lines) */
    private void indexAccessors(Map<String, byte[]> in) {
        if (!mappings.hasAccessorReplacements()) return;
        for (var e : in.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            try {
                org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(e.getValue()).accept(cn, org.objectweb.asm.ClassReader.SKIP_CODE);
                if ((cn.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) == 0 || cn.invisibleAnnotations == null) continue;
                List<String> targets = new ArrayList<>();
                for (var an : cn.invisibleAnnotations) {
                    if (!an.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || an.values == null) continue;
                    for (int i = 0; i < an.values.size(); i += 2) {
                        Object v = an.values.get(i + 1);
                        if (v instanceof List<?> l) for (Object o : l) {
                            if (o instanceof org.objectweb.asm.Type t) targets.add(t.getInternalName());
                            else if (o instanceof String str) targets.add(str.replace('.', '/'));
                        }
                    }
                }
                if (targets.isEmpty()) continue;
                for (var mn : cn.methods) {
                    if ((mn.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0) continue;
                    List<org.objectweb.asm.tree.AnnotationNode> anns = new ArrayList<>();
                    if (mn.visibleAnnotations != null) anns.addAll(mn.visibleAnnotations);
                    if (mn.invisibleAnnotations != null) anns.addAll(mn.invisibleAnnotations);
                    for (var an : anns) {
                        boolean accessor = an.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;");
                        if (!accessor && !an.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;")) continue;
                        String member = null;
                        if (an.values != null) for (int i = 0; i < an.values.size(); i += 2) if (an.values.get(i).equals("value")) member = (String) an.values.get(i + 1);
                        if (member == null) {
                            String n = mn.name.replaceFirst("^(get|set|is|call|invoke)", "");
                            member = n.isEmpty() ? mn.name : Character.toLowerCase(n.charAt(0)) + n.substring(1);
                        }
                        for (String t : targets) {
                            mappings.registerAccessor(cn.name, mn.name, mn.desc, t, member);
                            mappings.registerAccessor(cn.name, mn.name, mn.desc, t, member.toUpperCase(java.util.Locale.ROOT));
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    public JarConverter.Result convert(String fileName, byte[] jar) throws IOException {
        Map<String, byte[]> in = JarConverter.read(jar);
        if (!isLegacyMod(in)) throw new IOException(fileName + " is not a Minecraft 1.20.1 Forge mod");
        List<String> warnings = new ArrayList<>();
        Set<String> missing = new TreeSet<>();
        List<JarConverter.Report> nested = new ArrayList<>();
        indexAccessors(in);

        LegacyTomlConverter.Result toml = new LegacyTomlConverter().convert(new String(in.get(JarConverter.FORGE_TOML), StandardCharsets.UTF_8));
        warnings.addAll(toml.warnings());
        Manifest manifest = new Manifest();
        if (in.containsKey("META-INF/MANIFEST.MF")) manifest.read(new ByteArrayInputStream(in.get("META-INF/MANIFEST.MF")));

        Map<String, byte[]> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            String name = e.getKey();
            byte[] data = e.getValue();
            if (name.equals("META-INF/MANIFEST.MF") || JarConverter.isSignature(name)) continue;
            if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                try {
                    ClassConverter.Output o = classConverter.convert(data);
                    warnings.addAll(o.problems());
                    out.put(name, o.bytes());
                } catch (RuntimeException ex) {
                    warnings.add("failed to convert " + name + ": " + ex);
                    out.put(name, data);
                }
            } else if (JarConverter.isNestedJar(name)) {
                Map<String, byte[]> n = JarConverter.read(data);
                if (isLegacyMod(n)) {
                    JarConverter.Result r = convert(name.substring(name.lastIndexOf('/') + 1), data);
                    nested.add(r.report());
                    out.put(name, r.jar());
                } else {
                    out.put(name, data);
                }
            } else if (name.equals(JarConverter.FORGE_TOML)) {
                out.put(name, toml.modsToml().getBytes(StandardCharsets.UTF_8));
            } else {
                ResourceRewriter.Rewritten r = resources.rewrite(name, data);
                if (r != null) out.putIfAbsent(r.name(), r.data());
            }
        }
        if (!out.containsKey("pack.mcmeta")) {
            String desc = String.join(", ", toml.modIds()) + " resources";
            out.put("pack.mcmeta", ("{\"pack\":{\"description\":\"" + desc.replace("\"", "'")
                + "\",\"pack_format\":34,\"supported_formats\":[15,48]}}").getBytes(StandardCharsets.UTF_8));
        }
        Attributes main = manifest.getMainAttributes();
        if (main.getValue(Attributes.Name.MANIFEST_VERSION) == null) main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.putValue("Campat-Converted-From", fileName);
        main.putValue("Campat-Source-Version", LegacyTomlConverter.FROM);
        manifest.getEntries().clear();
        return new JarConverter.Result(JarConverter.write(manifest, out), new JarConverter.Report(fileName, toml.modIds(), warnings, missing, nested));
    }
}
