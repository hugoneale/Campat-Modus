package campattest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Test result markers, grepped by tools/test/e2e.sh. */
public final class T {
    private static final Logger LOG = LogUtils.getLogger();

    public static void pass(String name) {
        LOG.info("[CAMPATTEST] PASS {}", name);
    }

    public static void fail(String name, Object why) {
        LOG.error("[CAMPATTEST] FAIL {}: {}", name, why);
        if (why instanceof Throwable t) LOG.error("[CAMPATTEST] stack", t);
    }

    /** Runs a test step, turning linkage errors (missing emulated API) into failures instead of crashes. */
    public static void run(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            fail(name, t);
        }
    }

    public static void check(String name, boolean ok, Object detail) {
        if (ok) pass(name); else fail(name, detail);
    }
}
