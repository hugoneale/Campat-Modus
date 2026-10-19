package io.github.hugoneale.campat.runtime.loading;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Constructs NeoForge mods the way NeoForge's {@code FMLModContainer} does. Called from the
 * {@code @Mod} class that the converter generates for each NeoForge mod id.
 */
public final class NeoEntrypoints {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Type NEO_SUBSCRIBER = Type.getType("Lnet/neoforged/fml/common/EventBusSubscriber;");
    private static final Type NEO_MOD = Type.getType("Lnet/neoforged/fml/common/Mod;");

    /** mod id -> instances of its NeoForge entrypoint classes */
    private static final Map<String, List<Object>> INSTANCES = new ConcurrentHashMap<>();

    private NeoEntrypoints() {}

    public static List<Object> instances(String modId) {
        return INSTANCES.getOrDefault(modId, List.of());
    }

    /**
     * @param spec {@code class;DIST,DIST|class;...}, an empty dist list meaning both sides
     */
    public static void construct(FMLJavaModLoadingContext context, String modId, String spec) {
        ModContainer container = context.getContainer();
        IEventBus modBus = context.getModEventBus();
        ClassLoader loader = NeoEntrypoints.class.getClassLoader();
        List<Object> instances = new ArrayList<>();
        if (!spec.isEmpty()) {
            for (String entry : spec.split("\\|")) {
                String[] parts = entry.split(";", -1);
                String className = parts[0];
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    boolean match = false;
                    for (String d : parts[1].split(",")) if (d.equals(FMLEnvironment.dist.name())) match = true;
                    if (!match) continue;
                }
                try {
                    Class<?> cls = Class.forName(className, true, loader);
                    instances.add(newInstance(cls, modBus, container));
                } catch (InvocationTargetException e) {
                    throw new RuntimeException("Failed to construct NeoForge mod class " + className + " of " + modId,
                        e.getCause() != null ? e.getCause() : e);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to construct NeoForge mod class " + className + " of " + modId, e);
                }
            }
        }
        INSTANCES.put(modId, List.copyOf(instances));
        injectSubscribers(container, modBus);
    }

    private static Object newInstance(Class<?> cls, IEventBus modBus, ModContainer container) throws ReflectiveOperationException {
        Constructor<?>[] ctors = cls.getConstructors();
        if (ctors.length != 1) {
            throw new IllegalStateException("NeoForge mod class " + cls.getName() + " must have exactly one public constructor, found " + ctors.length);
        }
        Constructor<?> ctor = ctors[0];
        Class<?>[] types = ctor.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == IEventBus.class) args[i] = modBus;
            else if (t == ModContainer.class || (t == FMLModContainer.class && container instanceof FMLModContainer)) args[i] = container;
            else if (t == Dist.class) args[i] = FMLEnvironment.dist;
            else throw new IllegalStateException("Unsupported constructor argument " + t.getName() + " in NeoForge mod class "
                    + cls.getName() + ". Allowed: IEventBus, ModContainer, Dist");
        }
        return ctor.newInstance(args);
    }

    /** NeoForge's AutomaticEventSubscriber: picks the bus per listener from the event type. */
    private static void injectSubscribers(ModContainer container, IEventBus modBus) {
        ModFileScanData scan = ModList.get().getModFileById(container.getModId()).getFile().getScanResult();
        if (scan == null) return;
        Map<String, String> modIdsByClass = new ConcurrentHashMap<>();
        for (ModFileScanData.AnnotationData a : scan.getAnnotations()) {
            if (NEO_MOD.equals(a.annotationType())) modIdsByClass.put(a.clazz().getClassName(), (String) a.annotationData().get("value"));
        }
        for (ModFileScanData.AnnotationData a : scan.getAnnotations()) {
            if (!NEO_SUBSCRIBER.equals(a.annotationType())) continue;
            String cls = a.clazz().getClassName();
            String modId = (String) a.annotationData().get("modid");
            if (modId == null || modId.isEmpty()) modId = modIdsByClass.getOrDefault(cls, container.getModId());
            if (!Objects.equals(modId, container.getModId())) continue;
            if (!sides(a.annotationData().get("value")).contains(FMLEnvironment.dist)) continue;
            try {
                register(Class.forName(cls, true, NeoEntrypoints.class.getClassLoader()), modBus);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to load @EventBusSubscriber class " + cls, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static EnumSet<Dist> sides(Object value) {
        if (!(value instanceof List<?> list)) return EnumSet.allOf(Dist.class);
        EnumSet<Dist> set = EnumSet.noneOf(Dist.class);
        for (Object o : list) {
            if (o instanceof ModFileScanData.EnumData e) set.add(Dist.valueOf(e.value()));
        }
        return set;
    }

    /** Registers every static {@code @SubscribeEvent} method of {@code cls} on the bus matching its event type. */
    public static void register(Class<?> cls, IEventBus modBus) {
        for (Method m : cls.getDeclaredMethods()) {
            SubscribeEvent sub = m.getAnnotation(SubscribeEvent.class);
            if (sub == null) continue;
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new IllegalArgumentException("Method " + m + " annotated with @SubscribeEvent is not static");
            }
            if (m.getParameterCount() != 1 || !Event.class.isAssignableFrom(m.getParameterTypes()[0])) {
                throw new IllegalArgumentException("Method " + m + " annotated with @SubscribeEvent must have only one parameter that is an Event subtype");
            }
            Class<?> eventType = m.getParameterTypes()[0];
            IEventBus bus = IModBusEvent.class.isAssignableFrom(eventType) ? modBus : MinecraftForge.EVENT_BUS;
            addListener(bus, sub.priority(), sub.receiveCanceled(), eventType, m);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addListener(IEventBus bus, EventPriority priority, boolean receiveCanceled, Class eventType, Method m) {
        m.setAccessible(true);
        Consumer<Event> consumer = event -> {
            try {
                m.invoke(null, event);
            } catch (InvocationTargetException e) {
                Throwable c = e.getCause();
                if (c instanceof RuntimeException re) throw re;
                if (c instanceof Error er) throw er;
                throw new RuntimeException(c);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };
        bus.addListener(priority, receiveCanceled, eventType, consumer);
    }
}
