package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.serialization.Lifecycle;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 1.20.1's {@code CommandBuildContext} only had {@code holderLookup(key)}; 1.21.1 made it a
 * {@code HolderLookup.Provider}. Mod implementations get {@code lookup}/{@code listRegistries} (D lines)
 * that call their own {@code holderLookup}, and calls of the old method go through {@link #holderLookup}.
 */
public final class LegacyCommandContext {
    private LegacyCommandContext() {}

    /** calls of {@code CommandBuildContext.holderLookup(key)} */
    public static HolderLookup<?> holderLookup(CommandBuildContext context, ResourceKey<? extends Registry<?>> key) {
        Method own = ownHolderLookup(context.getClass());
        if (own != null) return invoke(own, context, key);
        return context.lookupOrThrow((ResourceKey) key);
    }

    /** {@code HolderLookup.Provider.lookup} for a 1.20.1 implementation */
    public static Optional lookup(CommandBuildContext self, ResourceKey key) {
        Method own = ownHolderLookup(self.getClass());
        if (own == null) return Optional.empty();
        HolderLookup<?> l = invoke(own, self, key);
        return l == null ? Optional.empty() : Optional.of(asRegistryLookup(l, key));
    }

    /** {@code HolderLookup.Provider.listRegistries}: 1.20.1 implementations could not enumerate */
    public static Stream listRegistries(CommandBuildContext self) {
        return Stream.empty();
    }

    /** {@code HolderLookup.filterElements}, which 1.21.1 only has on {@code RegistryLookup} */
    public static HolderLookup filterElements(HolderLookup lookup, Predicate predicate) {
        if (lookup instanceof HolderLookup.RegistryLookup r) return r.filterElements(predicate);
        return new Filtered(lookup, predicate);
    }

    private static Method ownHolderLookup(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod("holderLookup", ResourceKey.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static HolderLookup<?> invoke(Method m, Object target, ResourceKey<?> key) {
        try {
            return (HolderLookup<?>) m.invoke(target, key);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static HolderLookup.RegistryLookup asRegistryLookup(HolderLookup lookup, ResourceKey key) {
        if (lookup instanceof HolderLookup.RegistryLookup r) return r;
        return new HolderLookup.RegistryLookup() {
            @Override
            public ResourceKey key() {
                return key;
            }

            @Override
            public Lifecycle registryLifecycle() {
                return Lifecycle.stable();
            }

            @Override
            public Stream listElements() {
                return lookup.listElements();
            }

            @Override
            public Stream listTags() {
                return lookup.listTags();
            }

            @Override
            public Optional get(ResourceKey k) {
                return lookup.get(k);
            }

            @Override
            public Optional get(TagKey t) {
                return lookup.get(t);
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private record Filtered(HolderLookup parent, Predicate predicate) implements HolderLookup {
        @Override
        public Stream<Holder.Reference> listElements() {
            return parent.listElements().filter(h -> predicate.test(((Holder.Reference) h).value()));
        }

        @Override
        public Stream<HolderSet.Named> listTags() {
            return parent.listTags();
        }

        @Override
        public Optional get(ResourceKey key) {
            return parent.get(key).filter(h -> predicate.test(((Holder.Reference) h).value()));
        }

        @Override
        public Optional get(TagKey tag) {
            return parent.get(tag);
        }
    }
}
