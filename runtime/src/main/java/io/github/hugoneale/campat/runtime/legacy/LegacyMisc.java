package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/** Small Minecraft 1.20.1 APIs that 1.21.1 removed or changed. */
public final class LegacyMisc {
    private LegacyMisc() {}

    // ---- ResourceLocation: the public constructors became factories ----

    public static ResourceLocation resourceLocation(String id) {
        return ResourceLocation.parse(id);
    }

    public static ResourceLocation resourceLocation(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static boolean isValidResourceLocation(String id) {
        return ResourceLocation.tryParse(id) != null;
    }

    // ---- chat ----

    public static TextColor parseColor(String color) {
        return TextColor.parseColor(color).result().orElse(null);
    }

    public static MutableComponent componentFromJson(String json) {
        return Component.Serializer.fromJson(json, LegacyRegistries.provider());
    }

    public static MutableComponent componentFromJson(com.google.gson.JsonElement json) {
        return Component.Serializer.fromJson(json, LegacyRegistries.provider());
    }

    public static String componentToJson(Component component) {
        return Component.Serializer.toJson(component, LegacyRegistries.provider());
    }

    // ---- DataFixerUpper 6 -> 8 ----

    public static Object getOrThrow(DataResult<?> result, boolean allowPartial, Consumer<String> onError) {
        if (allowPartial) {
            return result.resultOrPartial(onError).orElseThrow(() -> new IllegalStateException(result.error().map(e -> e.message()).orElse("no result")));
        }
        return result.getOrThrow(msg -> {
            onError.accept(msg);
            return new IllegalStateException(msg);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Either get(DataResult<?> result) {
        if (result.isSuccess()) return Either.left(result.getOrThrow());
        return Either.right(result.error().orElseThrow());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object utilGetOrThrow(DataResult result, Function toException) {
        Function<String, RuntimeException> f = msg -> {
            Object e = toException.apply(msg);
            return e instanceof RuntimeException r ? r : new IllegalStateException(msg, (Throwable) e);
        };
        DataResult<Object> typed = result;
        return typed.getOrThrow(f);
    }

    // ---- NBT files: File -> Path ----

    public static CompoundTag nbtRead(File file) throws IOException {
        return file.exists() ? NbtIo.read(file.toPath()) : null;
    }

    public static void nbtWrite(CompoundTag tag, File file) throws IOException {
        NbtIo.write(tag, file.toPath());
    }

    // ---- common 1.20.1 members (see tools/legacy/rules.txt) ----

    public static int performPrefixedCommand(net.minecraft.commands.Commands commands, net.minecraft.commands.CommandSourceStack source, String command) {
        commands.performPrefixedCommand(source, command);
        return 1;
    }

    public static Object wrapperData(net.minecraft.util.random.WeightedEntry.Wrapper wrapper) {
        return wrapper.data();
    }

    public static float saturationModifier(net.minecraft.world.food.FoodProperties food) {
        return food.nutrition() == 0 ? 0 : food.saturation() / (food.nutrition() * 2f);
    }

    public static boolean isEdible(net.minecraft.world.item.ItemStack stack) {
        return stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
    }

    public static net.minecraft.world.entity.EquipmentSlot.Type armorSlotType() {
        return net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    public static java.text.DecimalFormat attributeModifierFormat() {
        return net.minecraft.world.item.component.ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT;
    }

    public static String filterText(String text) {
        return net.minecraft.util.StringUtil.filterText(text);
    }

    public static net.minecraft.nbt.NbtAccounter nbtAccounter(long quota) {
        return net.minecraft.nbt.NbtAccounter.create(quota);
    }

    public static Object readById(net.minecraft.network.FriendlyByteBuf buf, net.minecraft.core.IdMap map) {
        return buf.readById(map::byIdOrThrow);
    }

    public static void writeId(net.minecraft.network.FriendlyByteBuf buf, net.minecraft.core.IdMap map, Object value) {
        buf.writeById(map::getIdOrThrow, value);
    }

    public static net.minecraft.nbt.CompoundTag readCompressed(java.io.InputStream in) throws java.io.IOException {
        return net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    }

    public static net.minecraft.nbt.CompoundTag readCompressed(java.io.File file) throws java.io.IOException {
        return net.minecraft.nbt.NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    }

    public static void writeCompressed(net.minecraft.nbt.CompoundTag tag, java.io.File file) throws java.io.IOException {
        net.minecraft.nbt.NbtIo.writeCompressed(tag, file.toPath());
    }

    public static net.minecraft.network.chat.Component formatDuration(net.minecraft.world.effect.MobEffectInstance effect, float multiplier) {
        return net.minecraft.world.effect.MobEffectUtil.formatDuration(effect, multiplier, 20f);
    }

    /** ItemStack.getAttributeModifiers(slot): the stack's modifiers (component or item defaults) for a slot */
    public static com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
            attributeModifiers(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
        com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> out =
            com.google.common.collect.LinkedHashMultimap.create();
        stack.forEachModifier(slot, (attribute, modifier) -> out.put(attribute.value(), modifier));
        return out;
    }

    /** EnchantmentHelper.getEnchantments(stack) */
    public static java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchantments(net.minecraft.world.item.ItemStack stack) {
        java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> out = new java.util.LinkedHashMap<>();
        for (var e : net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) out.put(e.getKey().value(), e.getIntValue());
        return out;
    }

    private static net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> vanillaAttribute(String path) {
        return net.minecraftforge.registries.RegistryObject.create(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path), net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES);
    }

    /** ForgeMod.BLOCK_REACH: vanilla's block interaction range since 1.20.5 */
    public static net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> blockReach() {
        return vanillaAttribute("player.block_interaction_range");
    }

    /** ForgeMod.ENTITY_REACH */
    public static net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> entityReach() {
        return vanillaAttribute("player.entity_interaction_range");
    }

    public static net.minecraft.world.item.Item.Properties defaultDurability(net.minecraft.world.item.Item.Properties properties, int durability) {
        return properties.durability(durability);
    }

    /** Forge 47's Item.Properties.setNoRepair: 1.21.1 decides repairs per item, nothing to set */
    public static net.minecraft.world.item.Item.Properties setNoRepair(net.minecraft.world.item.Item.Properties properties) {
        return properties;
    }
}
