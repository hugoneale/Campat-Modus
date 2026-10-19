/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.HolderSet.ListBacked;

public interface IHolderSetExtension<T> {
    /**
     * <p>Adds a callback to run when this holderset's contents invalidate (i.e. because tags were rebound).</p>
     * 
     * <p>The intended usage and use case is with composite holdersets that need to cache sets/list based on other
     * holdersets, which may be mutable (because they are tag-based or themselves composite holdersets).
     * Composite holdersets should use this to add callbacks to each of their component holdersets when constructed.</p>
     * 
     * @param runnable Runnable to invoke when this component holderset's contents are no longer valid.
     *                 This runnable should only clear caches and allow them to be lazily reevaluated later,
     *                 as not all tag holdersets may have been rebound when this is called.
     *                 This runnable should also invalidate all of the caller's listeners.
     */
    public void addInvalidationListener(Runnable runnable) ;

    /**
     * What format this holderset serializes to in json/nbt/etc
     */
    public SerializationType serializationType() ;

    /**
     * What format a holderset serializes to in json/nbt/etc
     */
    public static enum SerializationType {
        /** Unhandled/unsupported holderset implementation, could serialize as potentially anything **/
        UNKNOWN,
        STRING,
        LIST,
        OBJECT
    }
}
