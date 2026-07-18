package win.ac.x.api.data;

import win.ac.x.api.player.PlayerProfile;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.services.BaselineService;
import win.ac.x.services.PhysicsSimulationService;
import win.ac.x.services.VerdictDispatcher;
import win.ac.x.utils.LogUtils;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerContainer {

    @Getter
    private static final Map<UUID, PlayerProfile> uuidPlayerProfileMap = new ConcurrentHashMap<>();

    public static void init(Player player) {
        AsyncScheduler.run(() -> {
            PlayerProfile profile = new PlayerProfile(player);
            uuidPlayerProfileMap.put(player.getUniqueId(), profile);
            profile.initChecks(profile.getInstance());
            VerdictDispatcher.registerPlayer(profile);
            PhysicsSimulationService.registerProfile(profile);
            BaselineService.registerProfile(profile);
        });
    }

    public static void unload(Player player) {
        PlayerProfile profile = uuidPlayerProfileMap.get(player.getUniqueId());
        if (profile == null) return;
        if (!profile.getLogs().isEmpty()) {
            final StringBuilder logBuilder = new StringBuilder();
            LogUtils.createLog(player.getName());
            for (final String l : profile.getLogs()) logBuilder.append("\n").append(l);
            LogUtils.addLog(player.getName(), logBuilder.toString());
            profile.getLogs().clear();
        }
        PhysicsSimulationService.unregisterProfile(profile);
        BaselineService.unregisterProfile(profile);
        VerdictDispatcher.unregisterPlayer(player.getUniqueId());
        uuidPlayerProfileMap.remove(player.getUniqueId());
        if (profile.getBanAnimInfo() != null && !profile.isIgnoreExitBan()) {
            profile.forcePunish(profile.getBanAnimInfo().getX(), profile.getBanAnimInfo().getY());
        }
    }

    @Nullable
    public static PlayerProfile getProfile(Player player) {
        return uuidPlayerProfileMap.get(player.getUniqueId());
    }

    @Nullable
    public static PlayerProfile getProfileByName(String name) {
        for (PlayerProfile profile : uuidPlayerProfileMap.values()) {
            if (profile.getPlayer().getName().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }
}
