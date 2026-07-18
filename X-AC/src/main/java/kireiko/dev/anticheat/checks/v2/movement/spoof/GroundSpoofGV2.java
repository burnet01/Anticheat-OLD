package kireiko.dev.anticheat.checks.v2.movement.spoof;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.*;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public final class GroundSpoofGV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 8);
        return new ConfigLabel("v2_groundspoof_g", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public GroundSpoofGV2(PlayerProfile profile) {
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

        if (player.getAllowFlight() || player.isFlying() || player.getVehicle() != null || player.isGliding()) return;

        if (profile.isGround()) {
            Location to = ((MoveEvent) o).getTo();
            Location from = ((MoveEvent) o).getFrom();
            double deltaY = to.getY() - from.getY();
            double deltaX = to.getX() - from.getX();
            double deltaZ = to.getZ() - from.getZ();

            if (deltaY < -0.15) {
                buffer.decrease(player.getUniqueId(), 0.2);
                return;
            }

            if (profile.airTicks < 5) return;

            boolean physicallyOnGround = safeGround(to);

            if (!physicallyOnGround) {
                if (isUnderBlock() || buffer.increase(player.getUniqueId(), 1.0) > 5.0) {
                    profile.punish("Movement", "GroundSpoofG", "Ground Status Pulse (No Movement)", 1.0f);
                    buffer.reset(player.getUniqueId(), 2.0);
                }
            } else {
                buffer.decrease(player.getUniqueId(), 0.25);
            }
        }
    }

    private boolean safeGround(Location loc) {
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY() - 0.1);
        int z = (int) Math.floor(loc.getZ());
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (loc.getWorld().getBlockAt(x + ox, y, z + oz).getType().isSolid()) return true;
            }
        }
        return false;
    }

    private boolean isUnderBlock() {
        Location loc = profile.getTo();
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY() + 1.8);
        int z = (int) Math.floor(loc.getZ());
        return new Location(loc.getWorld(), x, y, z).getBlock().getType().isSolid();
    }
}