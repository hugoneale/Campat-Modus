package net.neoforged.neoforge.network.event;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** NeoForge's configuration task registration (mod bus); fed from Forge's {@code GatherLoginConfigurationTasksEvent}. */
public class RegisterConfigurationTasksEvent extends Event implements IModBusEvent {
    private final ServerConfigurationPacketListener listener;
    private final Queue<ConfigurationTask> tasks = new ArrayDeque<>();
    private final Consumer<ConfigurationTask> sink;

    public RegisterConfigurationTasksEvent() {
        this(null, t -> {});
    }

    public RegisterConfigurationTasksEvent(ServerConfigurationPacketListener listener) {
        this(listener, t -> {});
    }

    public RegisterConfigurationTasksEvent(ServerConfigurationPacketListener listener, Consumer<ConfigurationTask> sink) {
        this.listener = listener;
        this.sink = sink;
    }

    public void register(ConfigurationTask task) {
        tasks.add(task);
        sink.accept(task);
    }

    public Queue<ConfigurationTask> getConfigurationTasks() { return tasks; }
    public ServerConfigurationPacketListener getListener() { return listener; }
}
