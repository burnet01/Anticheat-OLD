package win.ac.x.services;

import win.ac.x.XMinestom;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.vectors.Pair;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnimatedPunishService {
    private static final List<PlayerProfile> punished = new ArrayList<>();

    public static void punish(PlayerProfile profile, Pair<String, String> bane) {
        profile.setBanAnimPositions(new Pair<>(profile.getTo(), profile.getTo()));
        profile.setBanAnimInfo(bane);
        punished.add(profile);
    }

    public static void init() {
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            AsyncScheduler.run(() -> punishAnim());
            return TaskSchedule.tick(1);
        });
    }

    private static void punishAnim() {
        Set<PlayerProfile> rm = new HashSet<>();
        for (PlayerProfile profile : punished) {
            if (profile.punishAnimation > 100) {
                MinecraftServer.getSchedulerManager().submitTask(() -> {
                    if (profile.getBanAnimInfo() != null) {
                        XMinestom.getPunishmentHandler().onPunish(profile, profile.getBanAnimInfo().getX(),
                            profile.getBanAnimInfo().getY(), profile.getBanAnimInfo().getX() + "/" + profile.getBanAnimInfo().getY(), profile.getVl());
                    }
                    return TaskSchedule.stop();
                });
                rm.add(profile);
            }
            profile.punishAnimation += 2;
        }
        for (PlayerProfile profile : rm)
            punished.remove(profile);
        rm.clear();
    }
}