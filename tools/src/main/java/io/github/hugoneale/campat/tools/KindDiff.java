package io.github.hugoneale.campat.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

/**
 * Lists NeoForge classes renamed to a Forge class of the other kind (class vs interface), for
 * example NeoForge's {@code Lazy} interface and Forge's {@code Lazy} class. Call sites need
 * different opcodes, so these become I/K lines in members.txt.
 *
 * <p>usage: KindDiff neo_env.txt forge_env.txt classes.txt runtime.jar</p>
 */
public final class KindDiff {
    public static void main(String[] a) throws Exception {
        PlatformEnv neo = PlatformEnv.fromList(Path.of(a[0]));
        PlatformEnv forge = PlatformEnv.fromList(Path.of(a[1]));
        forge.load(Path.of(a[3]));
        List<String> out = new ArrayList<>();
        for (String l : Files.readAllLines(Path.of(a[2]))) {
            String[] p = l.trim().split("\\s+");
            if (p.length != 3 || !p[0].equals("C")) continue;
            PlatformEnv.ClassInfo n = neo.get(p[1]), f = forge.get(p[2]);
            if (n == null || f == null) continue;
            boolean ni = (n.access() & Opcodes.ACC_INTERFACE) != 0, fi = (f.access() & Opcodes.ACC_INTERFACE) != 0;
            if (ni != fi) out.add((fi ? "I " : "K ") + p[1]);
        }
        out.forEach(System.out::println);
    }
}
