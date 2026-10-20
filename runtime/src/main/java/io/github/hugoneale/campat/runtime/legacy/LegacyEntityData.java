package io.github.hugoneale.campat.runtime.legacy;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 1.20.1 entities defined their synched data in {@code defineSynchedData()} with
 * {@code entityData.define(...)}; 1.21.1 passes a {@link SynchedEntityData.Builder}. The entity
 * constructor publishes its builder here (mixin) while the old style method runs.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyEntityData {
    private LegacyEntityData() {}

    private static final ThreadLocal<Deque<SynchedEntityData.Builder>> BUILDERS = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(SynchedEntityData.Builder builder) {
        BUILDERS.get().push(builder);
    }

    public static void pop() {
        BUILDERS.get().pop();
    }

    /** supplier for {@code super.defineSynchedData()} calls of old style methods */
    public static SynchedEntityData.Builder builder() {
        return BUILDERS.get().peek();
    }

    public static void define(SynchedEntityData data, EntityDataAccessor accessor, Object value) {
        SynchedEntityData.Builder b = BUILDERS.get().peek();
        if (b != null) {
            b.define(accessor, value);
        } else {
            throw new IllegalStateException("1.20.1 entity data defined outside defineSynchedData: " + accessor);
        }
    }
}
