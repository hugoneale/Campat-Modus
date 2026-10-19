package net.neoforged.neoforge.event.entity.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** NeoForge's shield block event; fired from Forge's {@code ShieldBlockEvent}. */
@Cancelable
public class LivingShieldBlockEvent extends LivingEvent {
    private final DamageSource source;
    private final float originalBlocked;
    private float blocked;
    private float shieldDamage;
    private final boolean originalBlock;
    private boolean block;

    public LivingShieldBlockEvent() {
        this(null, null, 0, false);
    }

    public LivingShieldBlockEvent(LivingEntity entity, DamageSource source, float blockedDamage, boolean blocked) {
        super(entity);
        this.source = source;
        this.originalBlocked = blockedDamage;
        this.blocked = blockedDamage;
        this.shieldDamage = blockedDamage;
        this.originalBlock = blocked;
        this.block = blocked;
    }

    public DamageSource getDamageSource() { return source; }
    public float getOriginalBlockedDamage() { return originalBlocked; }
    public float getBlockedDamage() { return blocked; }
    public float shieldDamage() { return block ? shieldDamage : 0; }
    public void setBlockedDamage(float blocked) { this.blocked = Math.max(0, Math.min(blocked, originalBlocked)); }
    public void setShieldDamage(float damage) { this.shieldDamage = damage; }
    public boolean getOriginalBlock() { return originalBlock; }
    public boolean getBlocked() { return block; }
    public void setBlocked(boolean block) { this.block = block; }
}
