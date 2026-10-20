package io.github.hugoneale.campat.runtime.legacy;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 1.20.1's subclassable tree growers. 1.21.1's {@link TreeGrower} is a fixed description by
 * configured feature keys, built from what the grower answers for a fixed random source.
 */
public final class LegacyTreeGrowers {
    private LegacyTreeGrowers() {}

    /** {@code AbstractTreeGrower} */
    public abstract static class AbstractTreeGrower {
        private TreeGrower modern;

        public AbstractTreeGrower() {}

        protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers);

        protected ResourceKey<ConfiguredFeature<?, ?>> megaFeature(RandomSource random) {
            return null;
        }

        public synchronized TreeGrower asTreeGrower() {
            if (modern == null) {
                String name = "campat_" + getClass().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                modern = new TreeGrower(name, Optional.ofNullable(megaFeature(RandomSource.create(0))),
                    Optional.ofNullable(getConfiguredFeature(RandomSource.create(0), false)), Optional.ofNullable(getConfiguredFeature(RandomSource.create(0), true)));
            }
            return modern;
        }

        public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random) {
            return asTreeGrower().growTree(level, generator, pos, state, random);
        }
    }

    /** {@code AbstractMegaTreeGrower} */
    public abstract static class AbstractMegaTreeGrower extends AbstractTreeGrower {
        public AbstractMegaTreeGrower() {}

        protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random);

        @Override
        protected ResourceKey<ConfiguredFeature<?, ?>> megaFeature(RandomSource random) {
            return getConfiguredMegaFeature(random);
        }
    }

    /** SaplingBlock(grower, properties) */
    public static Object[] saplingArgs(AbstractTreeGrower grower, net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new Object[]{grower.asTreeGrower(), properties};
    }
}
