package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/** Forge 47's {@code ITeleporter} and Minecraft 1.20.1's {@code PortalInfo}, over 1.21.1's DimensionTransition. */
public final class LegacyTeleporters {
    private LegacyTeleporters() {}

    /** {@code PortalInfo} */
    public static class PortalInfo {
        public final Vec3 pos;
        public final Vec3 speed;
        public final float yRot;
        public final float xRot;

        public PortalInfo(Vec3 pos, Vec3 speed, float yRot, float xRot) {
            this.pos = pos;
            this.speed = speed;
            this.yRot = yRot;
            this.xRot = xRot;
        }
    }

    /** {@code ITeleporter} */
    public interface Teleporter {
        default Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
            return repositionEntity.apply(true);
        }

        default PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
            return isVanilla() ? defaultPortalInfo.apply(destWorld) : new PortalInfo(entity.position(), Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }

        default boolean isVanilla() {
            return false;
        }

        default boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
            return true;
        }
    }

    /** Entity.changeDimension(level, teleporter) */
    public static Entity changeDimension(Entity entity, ServerLevel dest, Teleporter teleporter) {
        PortalInfo info = teleporter.getPortalInfo(entity, dest, l -> new PortalInfo(entity.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot()));
        if (info == null) return null;
        return entity.changeDimension(new DimensionTransition(dest, info.pos, info.speed, info.yRot, info.xRot, DimensionTransition.DO_NOTHING));
    }
}
