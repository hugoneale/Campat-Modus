package io.github.hugoneale.campat.tools;

import io.github.hugoneale.campat.loader.convert.JarConverter;
import io.github.hugoneale.campat.loader.remap.ClassHierarchy;
import io.github.hugoneale.campat.loader.remap.Mappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Offline conversion of a folder of NeoForge mods, the same way the locator does it at launch,
 * writing converted jars to an output folder and structurally verifying every converted class.
 *
 * <p>usage: ConvertAll runtime.jar campatResourceDir modsDir outDir</p>
 */
public final class ConvertAll {
    public static void main(String[] args) throws Exception {
        Path runtimeJar = Path.of(args[0]);
        Path res = Path.of(args[1]);
        Path mods = Path.of(args[2]);
        Path out = Path.of(args[3]);
        Files.createDirectories(out);

        Set<String> runtimeClasses = new HashSet<>();
        for (String n : JarConverter.read(Files.readAllBytes(runtimeJar)).keySet())
            if (n.endsWith(".class")) runtimeClasses.add(n.substring(0, n.length() - 6));
        Mappings mappings;
        try (InputStream a = Files.newInputStream(res.resolve("mappings/classes.txt"));
             InputStream b = Files.newInputStream(res.resolve("mappings/members.txt"))) {
            mappings = Mappings.load(List.of(a, b));
        }
        mappings.removeClassRenames(runtimeClasses::contains);
        ClassHierarchy hierarchy;
        try (InputStream h = Files.newInputStream(res.resolve("hierarchy.txt"))) {
            hierarchy = ClassHierarchy.load(h);
        }
        List<Path> jars;
        try (Stream<Path> s = Files.list(mods)) {
            jars = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
        List<Path> neo = new ArrayList<>();
        for (Path j : jars) {
            Map<String, byte[]> e = JarConverter.read(Files.readAllBytes(j));
            if (JarConverter.isNeoForgeMod(e)) {
                neo.add(j);
                JarConverter.index(e, hierarchy);
            }
        }
        JarConverter conv = new JarConverter(mappings, hierarchy, runtimeClasses::contains);
        int ok = 0, failed = 0, badClasses = 0;
        long t0 = System.currentTimeMillis();
        StringBuilder report = new StringBuilder();
        for (Path j : neo) {
            try {
                JarConverter.Result r = conv.convert(j.getFileName().toString(), Files.readAllBytes(j));
                Files.write(out.resolve(j.getFileName()), r.jar());
                int bad = verify(r.jar(), j.getFileName().toString(), report);
                badClasses += bad;
                report.append(j.getFileName()).append(" mods=").append(r.report().modIds())
                    .append(" warnings=").append(r.report().warnings().size())
                    .append(" missing=").append(r.report().missingNeoClasses().size()).append('\n');
                for (String w : r.report().warnings()) report.append("   w ").append(w).append('\n');
                ok++;
            } catch (Exception e) {
                failed++;
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                report.append("FAILED ").append(j.getFileName()).append('\n').append(sw).append('\n');
            }
        }
        Files.writeString(out.resolve("convert-report.txt"), report);
        System.out.printf("converted %d, failed %d, structurally invalid classes %d, %d ms%n", ok, failed, badClasses, System.currentTimeMillis() - t0);
    }

    private static int verify(byte[] jar, String name, StringBuilder report) throws Exception {
        int bad = 0;
        for (var e : JarConverter.read(jar).entrySet()) {
            if (!e.getKey().endsWith(".class") || e.getKey().startsWith("META-INF/")) continue;
            StringWriter sw = new StringWriter();
            try {
                new ClassReader(e.getValue()).accept(new CheckClassAdapter(org.objectweb.asm.Opcodes.ASM9, new org.objectweb.asm.tree.ClassNode(), true) {}, 0);
            } catch (Exception ex) {
                sw.append(ex.toString());
            }
            if (sw.getBuffer().length() > 0) {
                bad++;
                if (bad <= 3) report.append("   INVALID ").append(name).append('!').append(e.getKey()).append(": ")
                    .append(sw.toString(), 0, Math.min(600, sw.getBuffer().length())).append('\n');
            }
        }
        return bad;
    }
}
