package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** 1.20.1's {@code PressurePlateBlock.Sensitivity}; 1.21.1 takes it from the BlockSetType. */
public final class LegacyPressurePlates {
    private LegacyPressurePlates() {}

    public enum Sensitivity {
        EVERYTHING,
        MOBS
    }

    /** PressurePlateBlock(sensitivity, properties, setType) -> (setType, properties) */
    public static Object[] pressurePlate(Sensitivity sensitivity, BlockBehaviour.Properties properties, BlockSetType type) {
        return new Object[]{type, properties};
    }
}
