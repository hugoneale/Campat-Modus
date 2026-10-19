package net.neoforged.neoforge.event.brewing;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraftforge.eventbus.api.Event;

/** NeoForge's brewing registration event; fired from Forge's {@code BrewingRecipeRegisterEvent}. */
public class RegisterBrewingRecipesEvent extends Event {
    private final PotionBrewing.Builder builder;
    private final RegistryAccess registryAccess;

    public RegisterBrewingRecipesEvent() {
        this(null, null);
    }

    public RegisterBrewingRecipesEvent(PotionBrewing.Builder builder, RegistryAccess registryAccess) {
        this.builder = builder;
        this.registryAccess = registryAccess;
    }

    public PotionBrewing.Builder getBuilder() {
        return builder;
    }

    public RegistryAccess getRegistryAccess() {
        return registryAccess;
    }
}
