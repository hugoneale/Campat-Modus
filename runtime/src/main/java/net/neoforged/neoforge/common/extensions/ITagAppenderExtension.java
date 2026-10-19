/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface ITagAppenderExtension<T> {
    private TagsProvider.TagAppender<T> self() {
        return (TagsProvider.TagAppender<T>) (Object) this;
    }

    @SuppressWarnings("unchecked")
    TagsProvider.TagAppender<T> addTags(TagKey<T>... values) ;

    TagsProvider.TagAppender<T> addOptionalTag(TagKey<T> value) ;

    @SuppressWarnings("unchecked")
    TagsProvider.TagAppender<T> addOptionalTags(TagKey<T>... values) ;

    TagsProvider.TagAppender<T> replace() ;

    TagsProvider.TagAppender<T> replace(boolean value) ;

    /**
     * Adds a single element's ID to the tag json's remove list. Callable during datageneration.
     * 
     * @param location The ID of the element to remove
     * @return The builder for chaining
     */
    TagsProvider.TagAppender<T> remove(final ResourceLocation location) ;

    /**
     * Adds multiple elements' IDs to the tag json's remove list. Callable during datageneration.
     * 
     * @param locations The IDs of the elements to remove
     * @return The builder for chaining
     */
    TagsProvider.TagAppender<T> remove(final ResourceLocation first, final ResourceLocation... locations) ;

    /**
     * Adds a resource key to the tag json's remove list. Callable during datageneration.
     *
     * @param resourceKey The resource key of the element to remove
     * @return The appender for chaining
     */
    TagsProvider.TagAppender<T> remove(final ResourceKey<T> resourceKey) ;

    /**
     * Adds multiple resource keys to the tag json's remove list. Callable during datageneration.
     *
     * @param resourceKeys The resource keys of the elements to remove
     * @return The appender for chaining
     */
    @SuppressWarnings("unchecked")
    TagsProvider.TagAppender<T> remove(final ResourceKey<T> firstResourceKey, final ResourceKey<T>... resourceKeys) ;

    /**
     * Adds a tag to the tag json's remove list. Callable during datageneration.
     * 
     * @param tag The ID of the tag to remove
     * @return The builder for chaining
     */
    TagsProvider.TagAppender<T> remove(TagKey<T> tag) ;

    /**
     * Adds multiple tags to the tag json's remove list. Callable during datageneration.
     * 
     * @param tags The IDs of the tags to remove
     * @return The builder for chaining
     */
    @SuppressWarnings("unchecked")
    TagsProvider.TagAppender<T> remove(TagKey<T> first, TagKey<T>... tags) ;
}
