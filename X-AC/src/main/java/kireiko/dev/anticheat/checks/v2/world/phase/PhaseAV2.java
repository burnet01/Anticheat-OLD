package kireiko.dev.anticheat.checks.v2.world.phase;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.millennium.vectors.Vec3;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public final class PhaseAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 6);
        return new ConfigLabel("v2_phase_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public PhaseAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof MoveEvent) {
            MoveEvent e = (MoveEvent) o;
            PlayerProfile pf = e.getProfile();
            Player player = pf.getPlayer();
            UUID uuid = player.getUniqueId();

            if (skipBasic(pf, player)) return;

            if (pf.teleportTicks < 20 || pf.airTicks < 40) {
                buffer.decrease(uuid, 0.4);
                return;
            }

            Vec3 delta = e.getDelta();
            double deltaXZ = Math.hypot(delta.xCoord, delta.zCoord);
            if (Math.abs(delta.yCoord) < 0.06 && deltaXZ < 0.08) return;

            Location loc = pf.getTo();
            double x = loc.getX(), y = loc.getY(), z = loc.getZ();

            double minX = x - 0.3, maxX = x + 0.3;
            double minY = y, maxY = y + 1.8;
            double minZ = z - 0.3, maxZ = z + 0.3;

            World world = player.getWorld();
            boolean collision = false;
            for (int bx = floor(minX); bx <= floor(maxX); bx++) {
                for (int by = floor(minY); by <= floor(maxY); by++) {
                    for (int bz = floor(minZ); bz <= floor(maxZ); bz++) {
                        Block block = world.getBlockAt(bx, by, bz);
                        if (!block.getType().isAir() && block.getType().isSolid() && isHardCollision(block)) {
                            collision = true;
                            break;
                        }
                    }
                    if (collision) break;
                }
                if (collision) break;
            }

            if (collision) {
                double buf = ((Number) localCfg.getOrDefault("buffer", 6)).doubleValue();
                if (buffer.increase(uuid, 1.0) > 3.0) {
                    pf.punish("Phase", "A", "Phase inside solid block", 1.0f);
                    buffer.reset(uuid, 1.0);
                }
            } else {
                buffer.decrease(uuid, 0.2);
            }
        }
    }

    private boolean skipBasic(PlayerProfile pf, Player player) {
        return pf == null || player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isGliding()
                || player.isInsideVehicle();
    }

    private boolean isHardCollision(Block block) {
        String n = block.getType().name().toLowerCase();
        return !(n.contains("water") || n.contains("lava") || n.contains("web")
                || n.contains("vine") || n.contains("ladder")
                || n.contains("scaffolding") || n.contains("carpet"));
    }

    private int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}