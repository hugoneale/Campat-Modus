package io.github.hugoneale.campat.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class ClientNetwork {
    private ClientNetwork() {}

    public static Player player() {
        return Minecraft.getInstance().player;
    }
}
