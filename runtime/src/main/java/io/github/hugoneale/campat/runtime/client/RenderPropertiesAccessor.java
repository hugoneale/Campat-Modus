package io.github.hugoneale.campat.runtime.client;

/** Implemented by the client extension holder mixins: sets Forge's private render properties field. */
public interface RenderPropertiesAccessor {
    void campat$setRenderProperties(Object properties);
}
