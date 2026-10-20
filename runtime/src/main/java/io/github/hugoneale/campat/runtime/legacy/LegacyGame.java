package io.github.hugoneale.campat.runtime.legacy;

import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.server.ServerLifecycleHooks;

/** More Minecraft 1.20.1 APIs: potions on NBT, the loot data manager, resource packs, recipe lookups. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyGame {
    private LegacyGame() {}

    // ---- PotionUtils -> PotionContents component ----

    private static PotionContents contents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    public static Potion getPotion(ItemStack stack) {
        return contents(stack).potion().map(Holder::value).orElse(LegacyPotions.empty());
    }

    public static Potion getPotion(CompoundTag tag) {
        if (tag == null || !tag.contains("Potion")) return LegacyPotions.empty();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Potion"));
        Potion p = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.POTION.get(id);
        return p == null ? LegacyPotions.empty() : p;
    }

    public static ItemStack setPotion(ItemStack stack, Potion potion) {
        if (potion == null || potion == LegacyPotions.empty()) stack.remove(DataComponents.POTION_CONTENTS);
        else stack.set(DataComponents.POTION_CONTENTS, contents(stack).withPotion(LegacyHolders.wrap(potion)));
        return stack;
    }

    public static List<MobEffectInstance> getMobEffects(ItemStack stack) {
        List<MobEffectInstance> out = new ArrayList<>();
        contents(stack).getAllEffects().forEach(out::add);
        return out;
    }

    public static List<MobEffectInstance> getCustomEffects(ItemStack stack) {
        return new ArrayList<>(contents(stack).customEffects());
    }

    public static ItemStack setCustomEffects(ItemStack stack, Collection<MobEffectInstance> effects) {
        PotionContents c = contents(stack);
        List<MobEffectInstance> all = new ArrayList<>(c.customEffects());
        all.addAll(effects);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(c.potion(), c.customColor(), all));
        return stack;
    }

    public static List<MobEffectInstance> getAllEffects(Potion potion, Collection<MobEffectInstance> custom) {
        List<MobEffectInstance> out = new ArrayList<>(potion == null ? List.of() : potion.getEffects());
        out.addAll(custom);
        return out;
    }

    public static int getColor(ItemStack stack) {
        return contents(stack).getColor();
    }

    public static int getColor(Potion potion) {
        return PotionContents.getColor(potion.getEffects());
    }

    public static int getColor(Collection<MobEffectInstance> effects) {
        return PotionContents.getColor(effects);
    }

    // ---- loot: LootDataManager -> reloadable registries ----

    /** the 1.20.1 {@code LootDataManager}, answering from the server's reloadable registries */
    public static final class LootData {
        private final MinecraftServer server;

        LootData(MinecraftServer server) {
            this.server = server;
        }

        public LootTable getLootTable(ResourceLocation id) {
            return server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        }

        public Collection<ResourceLocation> getKeys(Object type) {
            return server.reloadableRegistries().getKeys(Registries.LOOT_TABLE);
        }
    }

    public static LootData getLootData(MinecraftServer server) {
        return new LootData(server);
    }

    // ---- resource packs ----

    /** 1.20.1's {@code Pack.ResourcesSupplier}: opened by pack id */
    @FunctionalInterface
    public interface ResourcesSupplier {
        PackResources open(String id);
    }

    private static Pack.ResourcesSupplier modern(ResourcesSupplier legacy) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return legacy.open(info.id());
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return legacy.open(info.id());
            }
        };
    }

    public static Pack readMetaAndCreate(String id, Component title, boolean required, ResourcesSupplier resources, PackType type, Pack.Position position, PackSource source) {
        return Pack.readMetaAndCreate(new PackLocationInfo(id, title, source, Optional.empty()), modern(resources), type, new PackSelectionConfig(required, position, false));
    }

    public static PathPackResources pathPackResources(String name, Path root, boolean builtin) {
        return new PathPackResources(new PackLocationInfo(name, Component.literal(name), PackSource.BUILT_IN, Optional.empty()), root);
    }

    // ---- recipe lookups: 1.21.1 answers RecipeHolders ----

    public static Optional getRecipeFor(RecipeManager manager, RecipeType type, Container container, Level level) {
        return manager.getRecipeFor(type, LegacyRecipes.toInput(container), level).map(h -> ((RecipeHolder) h).value());
    }

    public static Optional getRecipeFor(RecipeManager manager, RecipeType type, Container container, Level level, ResourceLocation last) {
        RecipeHolder lastHolder = last == null ? null : manager.byKey(last).orElse(null);
        Optional<RecipeHolder> found = manager.getRecipeFor(type, LegacyRecipes.toInput(container), level, lastHolder);
        return found.map(h -> Pair.of(h.id(), h.value()));
    }

    public static List getAllRecipesFor(RecipeManager manager, RecipeType type) {
        List out = new ArrayList();
        for (Object h : manager.getAllRecipesFor(type)) out.add(((RecipeHolder) h).value());
        return out;
    }

    public static List getRecipesFor(RecipeManager manager, RecipeType type, Container container, Level level) {
        List out = new ArrayList();
        for (Object h : manager.getRecipesFor(type, LegacyRecipes.toInput(container), level)) out.add(((RecipeHolder) h).value());
        return out;
    }

    public static NonNullList getRemainingItemsFor(RecipeManager manager, RecipeType type, Container container, Level level) {
        return manager.getRemainingItemsFor(type, LegacyRecipes.toInput(container), level);
    }

    public static Map byType(RecipeManager manager, RecipeType type) {
        Map out = new LinkedHashMap();
        for (Object h : manager.getAllRecipesFor(type)) out.put(((RecipeHolder) h).id(), ((RecipeHolder) h).value());
        return out;
    }

    public static Optional byKey(RecipeManager manager, ResourceLocation id) {
        return manager.byKey(id).map(RecipeHolder::value);
    }

    public static Collection getRecipes(RecipeManager manager) {
        List<Recipe<?>> out = new ArrayList<>();
        for (RecipeHolder<?> h : manager.getRecipes()) out.add(h.value());
        return out;
    }

    public static void replaceRecipes(RecipeManager manager, Iterable recipes) {
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (Object r : recipes) holders.add(r instanceof RecipeHolder<?> h ? h : new RecipeHolder<>(LegacyRecipes.getId((Recipe<?>) r), (Recipe) r));
        manager.replaceRecipes(holders);
    }

    // ---- advancements ----

    public static ResourceLocation advancementId(net.minecraft.advancements.Advancement advancement) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (net.minecraft.advancements.AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
                if (h.value() == advancement) return h.id();
            }
        }
        return ResourceLocation.fromNamespaceAndPath("campat", "unknown_advancement");
    }

    // ---- spawn eggs: entity type from the item tag -> from the stack ----

    /** {"EntityTag": {...}} of 1.20.1 -> a stack carrying ENTITY_DATA */
    public static ItemStack eggStack(CompoundTag tag) {
        ItemStack stack = new ItemStack(Items.STONE);
        if (tag != null && tag.contains("EntityTag", 10)) stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag.getCompound("EntityTag")));
        return stack;
    }

    public static CompoundTag eggTag(ItemStack stack) {
        CustomData data = stack == null ? null : stack.get(DataComponents.ENTITY_DATA);
        if (data == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.put("EntityTag", data.copyTag());
        return tag;
    }

    public static EntityType<?> eggType(SpawnEggItem egg, CompoundTag tag) {
        return egg.getType(eggStack(tag));
    }
}
