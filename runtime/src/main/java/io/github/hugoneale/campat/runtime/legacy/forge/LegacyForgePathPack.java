package io.github.hugoneale.campat.runtime.legacy.forge;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;

/** Forge 47's {@code resource.PathPackResources}, now vanilla's. */
public class LegacyForgePathPack extends PathPackResources {
    private final Path source;

    public LegacyForgePathPack(String packId, boolean isBuiltin, Path source) {
        super(new PackLocationInfo(packId, Component.literal(packId), isBuiltin ? PackSource.BUILT_IN : PackSource.DEFAULT, Optional.empty()), source);
        this.source = source;
    }

    public Path getSource() {
        return source;
    }

    protected Path resolve(String... paths) {
        Path p = source;
        for (String s : paths) p = p.resolve(s);
        return p;
    }
}
