package io.github.hugoneale.campat.runtime.legacy;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;

/** Minecraft 1.20.1's {@code AbstractCriterionTriggerInstance}, as a 1.21.1 {@link SimpleCriterionTrigger.SimpleInstance}. */
public abstract class LegacyCriterionInstance implements SimpleCriterionTrigger.SimpleInstance {
    private final ResourceLocation criterion;
    private final ContextAwarePredicate player;

    public LegacyCriterionInstance(ResourceLocation criterion, ContextAwarePredicate player) {
        this.criterion = criterion;
        this.player = player;
    }

    public ResourceLocation getCriterion() {
        return criterion;
    }

    protected ContextAwarePredicate getPlayerPredicate() {
        return player;
    }

    @Override
    public Optional<ContextAwarePredicate> player() {
        return player == null || player == LegacyCriteria.anyPlayer() ? Optional.empty() : Optional.of(player);
    }

    public JsonObject serializeToJson(LegacyCriteria.SerializationContext context) {
        return new JsonObject();
    }

    @Override
    public String toString() {
        return "AbstractCriterionInstance{criterion=" + criterion + "}";
    }
}
