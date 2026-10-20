package io.github.hugoneale.campat.runtime.legacy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static calls of members that are public only at runtime (widened by the access transformer for
 * 1.20.1 mods, but compiled against the unwidened classes).
 */
public final class LegacyAccess {
    private LegacyAccess() {}

    private static final Map<String, MethodHandle> HANDLES = new ConcurrentHashMap<>();

    public static Object invokeStatic(String owner, String name, String desc, Object... args) {
        MethodHandle h = HANDLES.computeIfAbsent(owner + "." + name + desc, k -> {
            try {
                Class<?> c = Class.forName(owner.replace('/', '.'), true, LegacyAccess.class.getClassLoader());
                MethodType type = MethodType.fromMethodDescriptorString(desc, LegacyAccess.class.getClassLoader());
                return MethodHandles.publicLookup().findStatic(c, name, type).asSpreader(Object[].class, type.parameterCount());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot reach " + k, e);
            }
        });
        try {
            return h.invoke(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
