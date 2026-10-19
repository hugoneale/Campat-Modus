/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface IIntrinsicHolderTagAppenderExtension<T> extends ITagAppenderExtension<T> {
    private IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> self() {
        return (IntrinsicHolderTagsProvider.IntrinsicTagAppender<T>) (Object) this;
    }

    ResourceKey<T> getKey(T value);

    /**
     * Adds a registry entry to the tag json's remove list. Callable during datageneration.
     *
     * @param entry The entry to remove
     * @return The builder for chaining
     */
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(final T entry) ;

    /**
     * Adds multiple registry entries to the tag json's remove list. Callable during datageneration.
     *
     * @param entries The entries to remove
     * @return The builder for chaining
     */
    @SuppressWarnings("unchecked")
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(final T first, final T... entries) ;

    @Override
    @SuppressWarnings("unchecked")
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> addTags(TagKey<T>... values) ;

    @Override
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> replace() ;

    @Override
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> replace(boolean value) ;

    @Override
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(final ResourceLocation location) ;

    @Override
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(final ResourceLocation first, final ResourceLocation... locations) ;

    @Override
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(final ResourceKey<T> resourceKey) ;

    @Override
    @SuppressWarnings("unchecked")
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(final ResourceKey<T> firstResourceKey, final ResourceKey<T>... resourceKeys) ;

    @Override
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(TagKey<T> tag) ;

    @Override
    @SuppressWarnings("unchecked")
    IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> remove(TagKey<T> first, TagKey<T>... tags) ;
}
