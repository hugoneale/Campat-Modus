package io.github.hugoneale.campat.runtime.legacy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * Minecraft 1.20.1's {@code RecordItem}. Music discs became data driven jukebox songs in 1.21, so this
 * keeps the old class shape (sound, comparator output, length) for mods that create or inspect discs.
 */
public class LegacyRecordItem extends Item {
    private static final Map<SoundEvent, LegacyRecordItem> BY_NAME = new HashMap<>();
    private final int analogOutput;
    private final Supplier<SoundEvent> sound;
    private final int lengthInTicks;

    public LegacyRecordItem(int comparatorValue, SoundEvent sound, Item.Properties properties, int lengthInTicks) {
        this(comparatorValue, () -> sound, properties, lengthInTicks);
        BY_NAME.put(sound, this);
    }

    public LegacyRecordItem(int comparatorValue, Supplier<SoundEvent> sound, Item.Properties properties, int lengthInTicks) {
        super(properties);
        this.analogOutput = comparatorValue;
        this.sound = sound;
        this.lengthInTicks = lengthInTicks;
    }

    public static LegacyRecordItem getBySound(SoundEvent sound) {
        return BY_NAME.get(sound);
    }

    public MutableComponent getDisplayName() {
        return Component.translatable(this.getDescriptionId() + ".desc");
    }

    public int getAnalogOutput() {
        return analogOutput;
    }

    public SoundEvent getSound() {
        return sound.get();
    }

    public int getLengthInTicks() {
        return lengthInTicks;
    }
}
