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
}
