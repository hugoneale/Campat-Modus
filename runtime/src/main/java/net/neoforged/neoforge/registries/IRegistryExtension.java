package net.neoforged.neoforge.registries;

import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.callback.RegistryCallback;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge's extension of {@code Registry}. Vanilla registries do not implement it on Forge: the
 * converter redirects calls of these methods on {@code Registry} to {@code DataMaps}/{@code RegistryCallbacks}.
 */
public interface IRegistryExtension<T> {
    void addCallback(RegistryCallback<T> callback);

    default <C extends RegistryCallback<T>> void addCallback(Class<C> type, C callback) {
        addCallback(callback);
    }

    @Nullable
    <A> A getData(DataMapType<T, A> type, ResourceKey<T> key);

    <A> Map<ResourceKey<T>, A> getDataMap(DataMapType<T, A> type);

    @Nullable
    ResourceLocation getKeyOrNull(T element);
}
