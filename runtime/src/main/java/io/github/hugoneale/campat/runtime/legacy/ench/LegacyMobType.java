package io.github.hugoneale.campat.runtime.legacy.ench;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;

/** Minecraft 1.20.1's {@code MobType}; 1.21.1 uses entity type tags instead. */
public final class LegacyMobType {
    public static final LegacyMobType UNDEFINED = new LegacyMobType("undefined");
    public static final LegacyMobType UNDEAD = new LegacyMobType("undead");
    public static final LegacyMobType ARTHROPOD = new LegacyMobType("arthropod");
    public static final LegacyMobType ILLAGER = new LegacyMobType("illager");
    public static final LegacyMobType WATER = new LegacyMobType("water");

    private final String name;

    private LegacyMobType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static LegacyMobType of(LivingEntity entity) {
        if (entity == null) return UNDEFINED;
        var type = entity.getType();
        if (type.is(EntityTypeTags.UNDEAD)) return UNDEAD;
        if (type.is(EntityTypeTags.ARTHROPOD)) return ARTHROPOD;
        if (type.is(EntityTypeTags.ILLAGER)) return ILLAGER;
        if (type.is(EntityTypeTags.AQUATIC)) return WATER;
        return UNDEFINED;
    }
}
