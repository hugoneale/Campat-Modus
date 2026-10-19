/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.extensions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;

/**
 * Extension interface for {@link KeyMapping}.
 */
public interface IKeyMappingExtension {
    private KeyMapping self() {
        return (KeyMapping) (Object) this;
    }

    InputConstants.Key getKey();

    /**
     * {@return true if the key conflict context and modifier are active and the keyCode matches this binding, false otherwise}
     */
    boolean isActiveAndMatches(InputConstants.Key keyCode) ;

    public void setToDefault() ;

    void setKeyConflictContext(IKeyConflictContext keyConflictContext);

    IKeyConflictContext getKeyConflictContext();

    KeyModifier getDefaultKeyModifier();

    KeyModifier getKeyModifier();

    void setKeyModifierAndCode(KeyModifier keyModifier, InputConstants.Key keyCode);

    boolean isConflictContextAndModifierActive() ;

    /**
     * Returns true when one of the bindings' key codes conflicts with the other's modifier.
     */
    boolean hasKeyModifierConflict(KeyMapping other) ;

    /**
     * {@return the display name of this key mapping}
     * Defaults to a {@linkplain Component#translatable(String) translatable component} of the {@link KeyMapping#getName() name}.
     */
    default Component getDisplayName() {
        return Component.translatable(self().getName());
    }
}
