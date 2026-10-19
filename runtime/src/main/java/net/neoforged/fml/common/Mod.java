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
 * NeoForge's mod entrypoint annotation. Kept under its NeoForge name so that Forge does not treat
 * annotated classes as Forge entrypoints; the converter generates the Forge entrypoint instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();

    Dist[] dist() default { Dist.CLIENT, Dist.DEDICATED_SERVER };
}
