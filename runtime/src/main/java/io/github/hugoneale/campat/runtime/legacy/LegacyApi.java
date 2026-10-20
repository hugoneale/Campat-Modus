package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Minecraft 1.20.1 APIs that 1.21.1 removed or reshaped, implemented on the new ones. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyApi {
    private LegacyApi() {}

    // ---- Ingredient: network / JSON went to codecs ----

    public static Ingredient ingredientFromNetwork(FriendlyByteBuf buf) {
        return Ingredient.CONTENTS_STREAM_CODEC.decode(LegacyBufs.registryBuf(buf));
    }

    public static void ingredientToNetwork(Ingredient ingredient, FriendlyByteBuf buf) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(LegacyBufs.registryBuf(buf), ingredient);
    }

    public static Ingredient ingredientFromJson(JsonElement json) {
        return Ingredient.CODEC.parse(LegacyRegistries.provider().createSerializationContext(JsonOps.INSTANCE), json)
            .getOrThrow(msg -> new com.google.gson.JsonSyntaxException(msg));
    }

    public static Ingredient ingredientFromJson(JsonElement json, boolean allowEmpty) {
        return ingredientFromJson(json);
    }

    public static JsonElement ingredientToJson(Ingredient ingredient) {
        return Ingredient.CODEC.encodeStart(LegacyRegistries.provider().createSerializationContext(JsonOps.INSTANCE), ingredient).getOrThrow();
    }

    public static net.minecraft.world.item.ItemStack itemStackFromJson(com.google.gson.JsonObject json) {
        return net.minecraft.world.item.ItemStack.CODEC.parse(LegacyRegistries.provider().createSerializationContext(JsonOps.INSTANCE), json)
            .getOrThrow(msg -> new com.google.gson.JsonSyntaxException(msg));
    }

    // ---- saved data: load function + supplier became a Factory ----

    public static SavedData computeIfAbsent(DimensionDataStorage storage, Function<CompoundTag, ? extends SavedData> load, Supplier<? extends SavedData> create, String name) {
        return storage.computeIfAbsent(new SavedData.Factory<>((Supplier) create, (tag, provider) -> load.apply(tag), null), name);
    }

    public static SavedData get(DimensionDataStorage storage, Function<CompoundTag, ? extends SavedData> load, String name) {
        return storage.get(new SavedData.Factory<>(() -> null, (tag, provider) -> load.apply(tag), null), name);
    }

    // ---- NBT ----

    public static BlockPos readBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    public static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    public static GameProfile readGameProfile(CompoundTag tag) {
        String name = tag.contains("Name", Tag.TAG_STRING) ? tag.getString("Name") : null;
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : net.minecraft.Util.NIL_UUID;
        try {
            GameProfile profile = new GameProfile(id, name == null ? "" : name);
            if (tag.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag props = tag.getCompound("Properties");
                for (String key : props.getAllKeys()) {
                    ListTag list = props.getList(key, Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag p = list.getCompound(i);
                        String value = p.getString("Value");
                        profile.getProperties().put(key, p.contains("Signature", Tag.TAG_STRING) ? new Property(key, value, p.getString("Signature")) : new Property(key, value));
                    }
                }
            }
            return profile;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static CompoundTag writeGameProfile(CompoundTag tag, GameProfile profile) {
        if (!profile.getName().isEmpty()) tag.putString("Name", profile.getName());
        if (profile.getId() != null) tag.putUUID("Id", profile.getId());
        if (!profile.getProperties().isEmpty()) {
            CompoundTag props = new CompoundTag();
            for (String key : profile.getProperties().keySet()) {
                ListTag list = new ListTag();
                for (Property p : profile.getProperties().get(key)) {
                    CompoundTag t = new CompoundTag();
                    t.putString("Value", p.value());
                    if (p.signature() != null) t.putString("Signature", p.signature());
                    list.add(t);
                }
                props.put(key, list);
            }
            tag.put("Properties", props);
        }
        return tag;
    }

    // ---- entities ----

    public static void broadcastBreakEvent(net.minecraft.world.entity.LivingEntity player, InteractionHand hand) {
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        player.onEquippedItemBroken(player.getItemInHand(hand).getItem(), slot);
    }

    public static void broadcastBreakEvent(net.minecraft.world.entity.LivingEntity entity, EquipmentSlot slot) {
        entity.onEquippedItemBroken(entity.getItemBySlot(slot).getItem(), slot);
    }

    // ---- attribute modifiers: UUID + name became a ResourceLocation id ----

    public static AttributeModifier attributeModifier(UUID id, String name, double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(modifierId(id, name), amount, op);
    }

    public static AttributeModifier attributeModifier(String name, double amount, AttributeModifier.Operation op) {
        return attributeModifier(UUID.randomUUID(), name, amount, op);
    }

    public static AttributeModifier attributeModifier(UUID id, Supplier<String> name, double amount, AttributeModifier.Operation op) {
        return attributeModifier(id, name.get(), amount, op);
    }

    public static ResourceLocation modifierId(UUID id, String name) {
        return ResourceLocation.fromNamespaceAndPath("campat_legacy", id.toString());
    }

    public static UUID modifierUuid(AttributeModifier modifier) {
        ResourceLocation id = modifier.id();
        if (id.getNamespace().equals("campat_legacy")) {
            try {
                return UUID.fromString(id.getPath());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String modifierName(AttributeModifier modifier) {
        return modifier.id().toString();
    }

    // ---- food ----

    public static FoodProperties getFoodProperties(Item item) {
        return item.components().get(DataComponents.FOOD);
    }

    public static boolean isEdible(Item item) {
        return item.components().has(DataComponents.FOOD);
    }

    public static List<Pair<MobEffectInstance, Float>> foodEffects(FoodProperties food) {
        List<Pair<MobEffectInstance, Float>> out = new ArrayList<>();
        for (FoodProperties.PossibleEffect e : food.effects()) out.add(Pair.of(e.effect(), e.probability()));
        return out;
    }

    public static boolean isMeat(FoodProperties food) {
        return false;
    }

    // ---- misc ----

    public static float[] getTextureDiffuseColor(DyeColor color) {
        int c = color.getTextureDiffuseColor();
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    public static boolean isAllowedChatCharacter(char c) {
        return StringUtil.isAllowedChatCharacter(c);
    }

    public static AABB aabb(BlockPos a, BlockPos b) {
        return new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(b));
    }

    public static int zeroI() {
        return 0;
    }

    public static float zeroF() {
        return 0f;
    }

    public static double zeroD() {
        return 0d;
    }

    public static void noop(Object self) {}

    public static void noop(Object self, Object arg) {}
}
