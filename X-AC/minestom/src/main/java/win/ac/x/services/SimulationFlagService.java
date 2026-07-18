package win.ac.x.services;

import win.ac.x.api.player.PlayerProfile;
import win.ac.x.core.AsyncScheduler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimulationFlagService {

    @Getter
    private static final List<Flag> flags = new CopyOnWriteArrayList<>();

    public static void init() {
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            AsyncScheduler.run(() -> {
                final Set<Flag> toRemove = new HashSet<>();
                for (Flag flag : flags) {
                    flag.setLocation(flag.getLocation().add(flag.getVector()));
                    if (!isPointWall(flag.getLocation(), 0.3)) {
                        final Pos finalLoc = flag.getLocation();
                        MinecraftServer.getSchedulerManager().submitTask(() -> {
                            flag.getProfile().getPlayer().teleport(finalLoc);
                            return TaskSchedule.stop();
                        });
                        flag.setVector(new Vec(
                                flag.vector.x() * 0.91,
                                flag.vector.y() - (0.08 * 0.98),
                                flag.vector.z() * 0.91));
                    } else toRemove.add(flag);
                }
                flags.removeAll(toRemove);
            });
            return TaskSchedule.tick(1);
        });
    }

    private static boolean isPointWall(Pos location, final double scale) {
        final double x = location.x();
        final double y = location.y() + 0.1;
        final double z = location.z();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    Instance instance = null;
                    if (instance == null) instance = MinecraftServer.getInstanceManager().getInstances().stream().findFirst().orElse(null);
                    if (instance == null) continue;
                    Block block = instance.getBlock(
                            (int) Math.floor(x + (double) dx * scale),
                            (int) Math.floor(y + (double) dy * scale),
                            (int) Math.floor(z + (double) dz * scale));

                    if (!block.isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    public static class Flag {
        private final PlayerProfile profile;
        private Pos location;
        private Vec vector;
    }
}