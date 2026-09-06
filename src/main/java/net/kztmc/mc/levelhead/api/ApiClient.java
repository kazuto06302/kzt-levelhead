package net.kztmc.mc.levelhead.api;

import net.kztmc.mc.levelhead.cache.PlayerStats;
import java.util.UUID;

public interface ApiClient {
    PlayerStats fetchPlayer(UUID uuid) throws Exception;
}