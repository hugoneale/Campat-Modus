package net.neoforged.neoforge.event.entity.living;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** NeoForge's finalize spawn event; fired from Forge's {@code MobSpawnEvent.FinalizeSpawn}. */
@Cancelable
public class FinalizeSpawnEvent extends MobSpawnEvent {
    private DifficultyInstance difficulty;
    private final MobSpawnType spawnType;
    private SpawnGroupData spawnData;
    private final Either<BlockEntity, Entity> spawner;
    private boolean spawnCancelled;

    public FinalizeSpawnEvent() {
        this(null, null, 0, 0, 0, null, null, null, null);
    }

    public FinalizeSpawnEvent(Mob entity, ServerLevelAccessor level, double x, double y, double z, DifficultyInstance difficulty,
                              MobSpawnType spawnType, SpawnGroupData spawnData, Either<BlockEntity, Entity> spawner) {
        super(entity, level, x, y, z);
        this.difficulty = difficulty;
        this.spawnType = spawnType;
        this.spawnData = spawnData;
        this.spawner = spawner;
    }

    public DifficultyInstance getDifficulty() { return difficulty; }
    public void setDifficulty(DifficultyInstance difficulty) { this.difficulty = difficulty; }
    public MobSpawnType getSpawnType() { return spawnType; }
    public SpawnGroupData getSpawnData() { return spawnData; }
    public void setSpawnData(SpawnGroupData spawnData) { this.spawnData = spawnData; }
    public Either<BlockEntity, Entity> getSpawner() { return spawner; }
    public void setSpawnCancelled(boolean cancel) { this.spawnCancelled = cancel; }
    public boolean isSpawnCancelled() { return spawnCancelled; }
}
