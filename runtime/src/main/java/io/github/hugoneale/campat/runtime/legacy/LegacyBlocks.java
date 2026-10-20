package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;

/** Conversions for 1.20.1 block, item, effect and model methods whose signatures changed. */
public final class LegacyBlocks {
    private LegacyBlocks() {}

    /** Block.use result -> useItemOn result; PASS lets the held item act, as it did in 1.20.1 */
    public static ItemInteractionResult toItemResult(InteractionResult r) {
        if (r == null) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return switch (r) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }

    public static BlockGetter emptyGetter() {
        return EmptyBlockGetter.INSTANCE;
    }

    public static BlockPos zeroPos() {
        return BlockPos.ZERO;
    }

    public static LivingEntity noEntity() {
        return null;
    }

    public static boolean yes() {
        return true;
    }

    public static boolean no() {
        return false;
    }

    public static Item.TooltipContext tooltipContext(BlockGetter getter) {
        return getter instanceof Level level ? Item.TooltipContext.of(level) : Item.TooltipContext.EMPTY;
    }

    public static BlockGetter blockGetter(Item.TooltipContext context) {
        return context == null ? null : context.level();
    }

    // ---- models: float color components -> packed ARGB ----

    public static void renderToBuffer(net.minecraft.client.model.Model model, PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        model.renderToBuffer(pose, consumer, light, overlay, FastColor.ARGB32.colorFromFloat(a, r, g, b));
    }

    public static float red(int color) {
        return FastColor.ARGB32.red(color) / 255f;
    }

    public static float green(int color) {
        return FastColor.ARGB32.green(color) / 255f;
    }

    public static float blue(int color) {
        return FastColor.ARGB32.blue(color) / 255f;
    }

    public static float alpha(int color) {
        return FastColor.ARGB32.alpha(color) / 255f;
    }

    /** 1.20.1's {@code GravelBlock}; 1.21.1 builds gravel as a {@code ColoredFallingBlock} */
    public static class GravelBlock extends net.minecraft.world.level.block.ColoredFallingBlock {
        public GravelBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(new net.minecraft.util.ColorRGBA(-8356741), properties);
        }
    }

    /** 1.20.1's {@code SandBlock(dustColor, properties)} */
    public static class SandBlock extends net.minecraft.world.level.block.ColoredFallingBlock {
        public SandBlock(int dustColor, net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(new net.minecraft.util.ColorRGBA(dustColor), properties);
        }
    }

    /** 1.20.1's {@code StemGrownBlock}; 1.21.1 stems name their fruit by registry key */
    public abstract static class StemGrownBlock extends net.minecraft.world.level.block.Block {
        public StemGrownBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(properties);
        }

        public abstract net.minecraft.world.level.block.StemBlock getStem();

        public abstract net.minecraft.world.level.block.AttachedStemBlock getAttachedStem();
    }

    /** 1.20.1's {@code MelonBlock}; melons are plain blocks in 1.21.1 */
    public static class MelonBlock extends StemGrownBlock {
        public MelonBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public net.minecraft.world.level.block.StemBlock getStem() {
            return (net.minecraft.world.level.block.StemBlock) net.minecraft.world.level.block.Blocks.MELON_STEM;
        }

        @Override
        public net.minecraft.world.level.block.AttachedStemBlock getAttachedStem() {
            return (net.minecraft.world.level.block.AttachedStemBlock) net.minecraft.world.level.block.Blocks.ATTACHED_MELON_STEM;
        }
    }

    /** 1.20.1's {@code GlassBlock} */
    public static class GlassBlock extends net.minecraft.world.level.block.TransparentBlock {
        public GlassBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(properties);
        }
    }

    /** {@code new FallingBlock(properties)}: FallingBlock is abstract in 1.21.1 */
    public static net.minecraft.world.level.block.FallingBlock fallingBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new SimpleFallingBlock(properties);
    }

    public static class SimpleFallingBlock extends net.minecraft.world.level.block.FallingBlock {
        public static final com.mojang.serialization.MapCodec<SimpleFallingBlock> CODEC = simpleCodec(SimpleFallingBlock::new);

        public SimpleFallingBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.FallingBlock> codec() {
            return CODEC;
        }
    }

    private static net.minecraft.core.cauldron.CauldronInteraction.InteractionMap interactions(java.util.Map map) {
        return new net.minecraft.core.cauldron.CauldronInteraction.InteractionMap("campat_legacy", map);
    }

    /** AbstractCauldronBlock(properties, map) -> (properties, interaction map) */
    public static Object[] cauldronArgs(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties, java.util.Map map) {
        return new Object[]{properties, interactions(map)};
    }

    /** LayeredCauldronBlock(properties, fillPredicate, map) -> (precipitation, interaction map, properties) */
    public static Object[] layeredCauldronArgs(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties, java.util.function.Predicate fill, java.util.Map map) {
        net.minecraft.world.level.biome.Biome.Precipitation p = net.minecraft.world.level.biome.Biome.Precipitation.NONE;
        for (net.minecraft.world.level.biome.Biome.Precipitation c : new net.minecraft.world.level.biome.Biome.Precipitation[]{
            net.minecraft.world.level.biome.Biome.Precipitation.RAIN, net.minecraft.world.level.biome.Biome.Precipitation.SNOW}) {
            if (fill != null && fill.test(c)) { p = c; break; }
        }
        return new Object[]{p, interactions(map), properties};
    }

    /** FenceGateBlock(properties, open, close): 1.21.1 also wants a wood type */
    public static Object[] fenceGateArgs(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties, net.minecraft.sounds.SoundEvent open, net.minecraft.sounds.SoundEvent close) {
        return new Object[]{net.minecraft.world.level.block.state.properties.WoodType.OAK, properties, open, close};
    }

    /** WitherRoseBlock(effect, properties): 1.20.1 applied the effect for 8 seconds */
    public static Object[] witherRoseArgs(net.minecraft.world.effect.MobEffect effect, net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new Object[]{LegacyHolders.wrap(effect), 8f, properties};
    }

    /** Forge 47's StairBlock(stateSupplier, properties) */
    public static Object[] stairArgs(java.util.function.Supplier supplier, net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new Object[]{supplier.get(), properties};
    }
}
