package io.github.hugoneale.campat.runtime.legacy.forge;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

/** Static Forge 47 networking members that Forge 52 changed or removed. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyNetworking {
    private LegacyNetworking() {}

    public static final String ABSENT = "ABSENT 🤔";
    public static final String ACCEPTVANILLA = "ALLOWVANILLA 💓💓💓";

    public static String absent() {
        return ABSENT;
    }

    public static String acceptVanilla() {
        return ACCEPTVANILLA;
    }

    public static LegacySimpleChannel newSimpleChannel(ResourceLocation name, Supplier<String> version, Predicate<String> client, Predicate<String> server) {
        return new LegacySimpleChannel(name, version);
    }

    public static Predicate<String> acceptMissingOr(String version) {
        return v -> ABSENT.equals(v) || ACCEPTVANILLA.equals(v) || version.equals(v);
    }

    public static Predicate<String> acceptMissingOr(Predicate<String> test) {
        return v -> ABSENT.equals(v) || ACCEPTVANILLA.equals(v) || test.test(v);
    }

    public static PacketDistributor.PacketTarget with(PacketDistributor distributor, Supplier supplier) {
        return distributor.with(supplier == null ? null : supplier.get());
    }

    public static Supplier<PacketDistributor.TargetPoint> targetPoint(double x, double y, double z, double r, ResourceKey<Level> dim) {
        PacketDistributor.TargetPoint p = new PacketDistributor.TargetPoint(x, y, z, r, dim);
        return () -> p;
    }

    public static LogicalSide getReceptionSide(NetworkDirection direction) {
        return direction == NetworkDirection.PLAY_TO_SERVER || direction == NetworkDirection.LOGIN_TO_SERVER
            || direction == NetworkDirection.CONFIGURATION_TO_SERVER ? LogicalSide.SERVER : LogicalSide.CLIENT;
    }

    public static LogicalSide getOriginationSide(NetworkDirection direction) {
        return getReceptionSide(direction) == LogicalSide.SERVER ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }

    /** Forge 47's {@code NetworkRegistry.ChannelBuilder} */
    public static final class ChannelBuilder {
        private ResourceLocation name;
        private Supplier<String> version = () -> "1";

        public static ChannelBuilder named(ResourceLocation name) {
            ChannelBuilder b = new ChannelBuilder();
            b.name = name;
            return b;
        }

        public ChannelBuilder networkProtocolVersion(Supplier<String> version) {
            this.version = version;
            return this;
        }

        public ChannelBuilder clientAcceptedVersions(Predicate<String> test) {
            return this;
        }

        public ChannelBuilder serverAcceptedVersions(Predicate<String> test) {
            return this;
        }

        public LegacySimpleChannel simpleChannel() {
            return new LegacySimpleChannel(name, version);
        }

        public LegacyEventChannel eventNetworkChannel() {
            return new LegacyEventChannel(name, version);
        }
    }
}
