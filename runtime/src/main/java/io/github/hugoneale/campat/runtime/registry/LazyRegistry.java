package io.github.hugoneale.campat.runtime.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A {@link Registry} that forwards to the registry with the given key once Forge has created it.
 * NeoForge hands out custom registries at class initialisation time, Forge creates them later
 * during {@code NewRegistryEvent}.
 */
public final class LazyRegistry {
    private LazyRegistry() {}

    @SuppressWarnings("unchecked")
    public static <T> Registry<T> of(ResourceKey<? extends Registry<T>> key) {
        InvocationHandler h = new InvocationHandler() {
            private volatile Registry<T> delegate;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "key":
                        if (method.getParameterCount() == 0) return key;
                        break;
                    case "toString":
                        if (method.getParameterCount() == 0) return "LazyRegistry[" + key.location() + "]";
                        break;
                    case "hashCode":
                        if (method.getParameterCount() == 0) return System.identityHashCode(proxy);
                        break;
                    case "equals":
                        if (method.getParameterCount() == 1) return proxy == args[0] || resolve() == args[0];
                        break;
                    default:
                }
                if ((args == null || args.length == 0) && delegate == null && lookup() == null) {
                    // codecs requested at class initialisation, before Forge created the registry
                    Class<?> r = method.getReturnType();
                    if (r == com.mojang.serialization.Codec.class) {
                        return com.mojang.serialization.Codec.lazyInitialized(() -> (com.mojang.serialization.Codec<Object>) invokeResolved(method));
                    }
                    if (r == net.minecraft.network.codec.StreamCodec.class) {
                        return new net.minecraft.network.codec.StreamCodec<Object, Object>() {
                            @Override
                            public Object decode(Object buf) {
                                return ((net.minecraft.network.codec.StreamCodec<Object, Object>) invokeResolved(method)).decode(buf);
                            }

                            @Override
                            public void encode(Object buf, Object value) {
                                ((net.minecraft.network.codec.StreamCodec<Object, Object>) invokeResolved(method)).encode(buf, value);
                            }
                        };
                    }
                }
                try {
                    return method.invoke(resolve(), args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }

            private Object invokeResolved(Method method) {
                try {
                    return method.invoke(resolve());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }

            private Registry<T> lookup() {
                return (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
            }

            private Registry<T> resolve() {
                Registry<T> d = delegate;
                if (d == null) {
                    d = lookup();
                    if (d == null) throw new IllegalStateException("Registry " + key.location() + " has not been created yet");
                    delegate = d;
                }
                return d;
            }
        };
        return (Registry<T>) Proxy.newProxyInstance(LazyRegistry.class.getClassLoader(), new Class<?>[]{Registry.class}, h);
    }
}
