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

public final class GroundSpoofEV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();
    private float serverFallDistance = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 5);
        return new ConfigLabel("v2_groundspoof_e", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public GroundSpoofEV2(PlayerProfile profile) {
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
        if (frozen) return;

        Player player = profile.getPlayer();
        if (player.getAllowFlight() || player.isFlying() || player.getVehicle() != null || player.isGliding()) {
            serverFallDistance = 0;
            return;
        }

        Location to = profile.getTo();
        if (to.getBlock().isLiquid() || isOnClimbable(player)) {
            serverFallDistance = 0;
            return;
        }

        locationBlock: {
            int x = (int) Math.floor(to.getX());
            int y = (int) Math.floor(to.getY());
            int z = (int) Math.floor(to.getZ());
            String type = to.getWorld().getBlockAt(x, y, z).getType().name();
            if (type.contains("LADDER") || type.contains("VINE") || type.contains("SCAFFOLDING")) {
                serverFallDistance = 0;
                return;
            }
        }

        MoveEvent event = (MoveEvent) o;
        Location from = event.getFrom();
        double deltaY = to.getY() - from.getY();

        if (profile.isGround()) {
            serverFallDistance = 0;
        } else if (deltaY < 0) {
            serverFallDistance += (float) -deltaY;
        }

        if (serverFallDistance > 3.5 && profile.isGround()) {
            if (isOnGround()) return;

            if (buffer.increase(player.getUniqueId(), 1.0) > 3.0) {
                profile.punish("Movement", "GroundSpoofE", String.format("Fall Distance Spoof. Server: %.2f", serverFallDistance), 1.0f);
                buffer.reset(player.getUniqueId(), 1.0);
            }
        } else {
            buffer.decrease(player.getUniqueId(), 0.1);
        }
    }

    private boolean isOnGround() {
        Location loc = profile.getTo();
        return loc.getBlock().getType().isSolid()
            || loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }

    private boolean isOnClimbable(Player player) {
        String type = player.getLocation().getBlock().getType().name();
        return type.contains("LADDER") || type.contains("VINE") || type.contains("SCAFFOLDING");
    }
}