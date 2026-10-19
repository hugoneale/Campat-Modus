package io.github.hugoneale.campat.tools;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla (net/minecraft, com/mojang) members that NeoForge and Forge patched differently: same
 * field name with another type. Printed as {@code T owner name neoDesc forgeDesc} lines.
 *
 * <p>usage: MemberDiff neo_env.txt forge_env.txt</p>
 */
public final class MemberDiff {
    public static void main(String[] a) throws Exception {
        PlatformEnv neo = PlatformEnv.fromList(Path.of(a[0]));
        PlatformEnv forge = PlatformEnv.fromList(Path.of(a[1]));
        int n = 0;
        for (var e : neo.classes.entrySet()) {
            String c = e.getKey();
            if (!c.startsWith("net/minecraft/") && !c.startsWith("com/mojang/")) continue;
            PlatformEnv.ClassInfo f = forge.get(c);
            if (f == null) continue;
            Map<String, String> ff = new HashMap<>();
            for (String fd : f.fields()) ff.put(fd.substring(0, fd.indexOf(':')), fd.substring(fd.indexOf(':') + 1));
            for (String nd : e.getValue().fields()) {
                String name = nd.substring(0, nd.indexOf(':')), desc = nd.substring(nd.indexOf(':') + 1);
                String fdesc = ff.get(name);
                if (fdesc != null && !fdesc.equals(desc)) {
                    System.out.println("T " + c + " " + name + " " + desc + " " + fdesc);
                    n++;
                }
            }
        }
        System.err.println(n + " field type differences");
    }
}
