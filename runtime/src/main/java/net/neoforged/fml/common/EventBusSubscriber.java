/*
 * Copyright (c) Forge Development LLC and contributors
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.minecraftforge.api.distmarker.Dist;

/**
 * NeoForge's automatic subscriber annotation. Unlike Forge's, the bus is chosen per listener
 * method from the event type, which is why this is handled by the runtime and not by Forge.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER };

    String modid() default "";

    /** @deprecated ignored by NeoForge 21.1, the bus is derived from each listener's event type */
    @Deprecated
    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        MOD
    }
}
