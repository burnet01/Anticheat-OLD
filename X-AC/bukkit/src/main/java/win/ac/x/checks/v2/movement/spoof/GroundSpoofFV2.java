package win.ac.x.checks.v2.movement.spoof;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.*;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import win.ac.x.X;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GroundSpoofFV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_groundspoof_f", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public GroundSpoofFV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof MoveEvent)) return;
        if (profile.isIgnoreFirstTick()) return;

        long now = System.currentTimeMillis();
        boolean frozen = now - profile.getLastTeleport() < 1000;
        boolean teleportTick = now - profile.getLastTeleport() < 500;
        if (frozen || teleportTick) return;

        Player player = profile.getPlayer();

        if (player.getAllowFlight() || player.getVehicle() != null || player.isGliding()) return;
        if (profile.airTicks < 4) return;

        if (profile.isGround()) {
            boolean physicalGround = safeGround(profile.getTo());

            if (!physicalGround && hasNearbyEntities(player)) {
                physicalGround = true;
            }

            if (!physicalGround) {
                MoveEvent event = (MoveEvent) o;
                double deltaY = event.getTo().getY() - event.getFrom().getY();

                if (deltaY < -0.07 && isGroundBelow(profile.getTo(), 1.2)) {
                    buffer.decrease(player.getUniqueId(), 0.2);
                    return;
                }

                Location loc = new Location(player.getWorld(), event.getTo().getX(), event.getTo().getY() - 0.2, event.getTo().getZ());
                Block block = loc.getBlock();

                if (block.getType() != Material.AIR && block.getType().isSolid()) {
                    return;
                }

                if (buffer.increase(player.getUniqueId(), 0.85) > 5.0) {
                    profile.punish("Movement", "GroundSpoofF", "Physical Ground Spoof (No Collision)", 1.0f);
                    buffer.reset(player.getUniqueId(), 2.0);
                }
            } else {
                buffer.decrease(player.getUniqueId(), 0.5);
            }
        }
    }

    private boolean safeGround(Location loc) {
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY() - 0.1);
        int z = (int) Math.floor(loc.getZ());
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                Block block = loc.getWorld().getBlockAt(x + ox, y, z + oz);
                if (block.getType().isSolid()) return true;
            }
        }
        return false;
    }

    private boolean isGroundBelow(Location loc, double distance) {
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY());
        int z = (int) Math.floor(loc.getZ());
        for (int i = 1; i <= (int) Math.ceil(distance); i++) {
            if (loc.getWorld().getBlockAt(x, y - i, z).getType().isSolid()) return true;
        }
        return false;
    }

    private static final Map<UUID, Boolean> entityCache = new ConcurrentHashMap<>();
    private long lastEntityRefresh = 0;

    private boolean hasNearbyEntities(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastEntityRefresh > 1000) {
            lastEntityRefresh = now;
            Bukkit.getScheduler().runTask(X.getInstance(), () -> {
                boolean nearby = !player.getWorld().getNearbyEntities(player.getLocation(), 1.0, 1.0, 1.0).isEmpty();
                entityCache.put(player.getUniqueId(), nearby);
            });
        }
        return entityCache.getOrDefault(player.getUniqueId(), false);
    }
}