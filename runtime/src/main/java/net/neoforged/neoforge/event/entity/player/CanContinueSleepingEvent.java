package net.neoforged.neoforge.event.entity.player;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;

/** NeoForge's sleep continuation check; fired from Forge's {@code SleepingLocationCheckEvent}. */
public class CanContinueSleepingEvent extends LivingEvent {
    protected final Player.BedSleepingProblem problem;
    protected boolean mayContinueSleeping;

    public CanContinueSleepingEvent() {
        this(null, null);
    }

    public CanContinueSleepingEvent(LivingEntity entity, Player.BedSleepingProblem problem) {
        super(entity);
        this.problem = problem;
        this.mayContinueSleeping = problem == null;
    }

    public Optional<BlockPos> getSleepingPos() {
        return getEntity() == null ? Optional.empty() : getEntity().getSleepingPos();
    }

    public Player.BedSleepingProblem getProblem() { return problem; }
    public boolean mayContinueSleeping() { return mayContinueSleeping; }
    public void setContinueSleeping(boolean sleep) { this.mayContinueSleeping = sleep; }
}
