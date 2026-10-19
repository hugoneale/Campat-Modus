package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.serialization.Dynamic;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ItemLike;

/**
 * Minecraft 1.20.1's NBT based ItemStack API on 1.21.1's data components. The 1.20.1 "tag" is the
 * {@link DataComponents#CUSTOM_DATA} component. A stack owns the tag it handed out (mutations are
 * visible, like before); a stack that shares its component with a copy gets its own tag on first
 * access, so mutations never leak between stacks.
 */
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public final class LegacyItemStacks {
    private LegacyItemStacks() {}

    /** Data version of Minecraft 1.20.1, for upgrading 1.20.1 item NBT. */
    public static final int DATA_VERSION_1_20_1 = 3465;

    private static final Map<CustomData, WeakReference<ItemStack>> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean owns(ItemStack stack, CustomData data) {
        WeakReference<ItemStack> r = OWNERS.get(data);
        return r != null && r.get() == stack;
    }

    /** the stack's own mutable tag (creating one when {@code create}) */
    private static CompoundTag tag(ItemStack stack, boolean create) {
        if (stack.isEmpty()) return create ? new CompoundTag() : null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            if (!create) return null;
            data = CustomData.of(new CompoundTag());
        } else if (owns(stack, data)) {
            return data.getUnsafe();
        } else {
            data = CustomData.of(data.getUnsafe()); // copies
        }
        stack.set(DataComponents.CUSTOM_DATA, data);
        OWNERS.put(data, new WeakReference<>(stack));
        return data.getUnsafe();
    }

    /** ItemStack.copy() mixin hook: the copy must not share a tag the source may still mutate */
    public static void onCopy(ItemStack source, ItemStack copy) {
        CustomData data = source.get(DataComponents.CUSTOM_DATA);
        if (data != null && owns(source, data)) copy.set(DataComponents.CUSTOM_DATA, CustomData.of(data.getUnsafe()));
    }

    public static CompoundTag getTag(ItemStack stack) {
        return tag(stack, false);
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        return tag(stack, true);
    }

    public static boolean hasTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        // keep the caller's instance: 1.20.1 code mutates the tag after setting it
        CustomData data = io.github.hugoneale.campat.runtime.mixin.accessor.CustomDataAccessor.campat$wrap(tag);
        stack.set(DataComponents.CUSTOM_DATA, data);
        OWNERS.put(data, new WeakReference<>(stack));
    }

    public static CompoundTag getTagElement(ItemStack stack, String key) {
        CompoundTag t = tag(stack, false);
        return t != null && t.contains(key, Tag.TAG_COMPOUND) ? t.getCompound(key) : null;
    }

    public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
        CompoundTag t = tag(stack, true);
        if (t.contains(key, Tag.TAG_COMPOUND)) return t.getCompound(key);
        CompoundTag c = new CompoundTag();
        t.put(key, c);
        return c;
    }

    public static void addTagElement(ItemStack stack, String key, Tag value) {
        tag(stack, true).put(key, value);
    }

    public static void removeTagKey(ItemStack stack, String key) {
        CompoundTag t = tag(stack, false);
        if (t != null) {
            t.remove(key);
            if (t.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        }
    }

    // ---- names, tooltips, damage ----

    public static boolean hasCustomHoverName(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    public static ItemStack setHoverName(ItemStack stack, Component name) {
        if (name == null) stack.remove(DataComponents.CUSTOM_NAME);
        else stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    public static void resetHoverName(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_NAME);
    }

    public static List getTooltipLines(ItemStack stack, Player player, TooltipFlag flag) {
        return stack.getTooltipLines(player == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(player.level()), player, flag);
    }

    public static void hurtAndBreak(ItemStack stack, int amount, LivingEntity entity, Consumer onBroken) {
        if (entity != null && entity.level() instanceof ServerLevel level) {
            stack.hurtAndBreak(amount, level, entity instanceof net.minecraft.server.level.ServerPlayer p ? p : null, item -> onBroken.accept(entity));
        }
    }

    public static void enchant(ItemStack stack, Enchantment enchantment, int level) {
        stack.enchant(LegacyHolders.wrap(enchantment), level);
    }

    public static int getBaseRepairCost(ItemStack stack) {
        return stack.getOrDefault(DataComponents.REPAIR_COST, 0);
    }

    public static void setRepairCost(ItemStack stack, int cost) {
        stack.set(DataComponents.REPAIR_COST, cost);
    }

    public static boolean isSameItemSameTags(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    public static boolean tagMatches(ItemStack a, ItemStack b) {
        return a.getComponents().equals(b.getComponents());
    }

    public static ItemStack create(ItemLike item, int count, CompoundTag tag) {
        ItemStack stack = new ItemStack(item, count);
        if (tag != null) setTag(stack, tag);
        return stack;
    }

    // ---- NBT ----

    /** 1.20.1 stack NBT ({@code id, Count, tag}) through Mojang's data fixers; 1.21 NBT as is */
    public static ItemStack of(CompoundTag nbt) {
        if (nbt == null) return ItemStack.EMPTY;
        CompoundTag current = nbt;
        if (nbt.contains("Count") || nbt.contains("tag")) {
            Dynamic<Tag> fixed = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, nbt),
                DATA_VERSION_1_20_1, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            if (fixed.getValue() instanceof CompoundTag c) current = c;
        }
        return ItemStack.parseOptional(LegacyRegistries.provider(), current);
    }

    public static CompoundTag save(ItemStack stack, CompoundTag into) {
        if (stack.isEmpty()) {
            into.putString("id", "minecraft:air");
            return into;
        }
        return (CompoundTag) stack.save(LegacyRegistries.provider(), into);
    }

    public static ListTag enchantmentTags(ItemStack stack) {
        ListTag list = new ListTag();
        stack.getEnchantments().entrySet().forEach(e -> {
            CompoundTag t = new CompoundTag();
            e.getKey().unwrapKey().ifPresent(k -> t.putString("id", k.location().toString()));
            t.putShort("lvl", (short) e.getIntValue());
            list.add(t);
        });
        return list;
    }

    // ---- containers and buffers ----

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items) {
        return ContainerHelper.saveAllItems(tag, items, LegacyRegistries.provider());
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, boolean alwaysPut) {
        return ContainerHelper.saveAllItems(tag, items, alwaysPut, LegacyRegistries.provider());
    }

    public static void loadAllItems(CompoundTag tag, NonNullList<ItemStack> items) {
        ContainerHelper.loadAllItems(tag, items, LegacyRegistries.provider());
    }

    private static RegistryFriendlyByteBuf registryBuf(FriendlyByteBuf buf) {
        return buf instanceof RegistryFriendlyByteBuf r ? r : new RegistryFriendlyByteBuf(buf, LegacyRegistries.access());
    }

    public static ItemStack readItem(FriendlyByteBuf buf) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(registryBuf(buf));
    }

    public static FriendlyByteBuf writeItem(FriendlyByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(registryBuf(buf), stack);
        return buf;
    }

    public static FriendlyByteBuf writeNbt(FriendlyByteBuf buf, CompoundTag tag) {
        return buf.writeNbt(tag);
    }

    public static CompoundTag readAnySizeNbt(FriendlyByteBuf buf) {
        Tag t = buf.readNbt(NbtAccounter.unlimitedHeap());
        return t instanceof CompoundTag c ? c : null;
    }
}
