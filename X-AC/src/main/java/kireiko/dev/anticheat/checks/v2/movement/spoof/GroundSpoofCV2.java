package kireiko.dev.anticheat.checks.v2.movement.spoof;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.*;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.Location;

import java.util.*;

public final class GroundSpoofCV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 6);
        return new ConfigLabel("v2_groundspoof_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public GroundSpoofCV2(PlayerProfile profile) {
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

        var player = profile.getPlayer();
        if (player.getAllowFlight() || player.isFlying() || player.getVehicle() != null || player.isGliding()) return;

        if (profile.isGround()) {
            Location to = ((MoveEvent) o).getTo();
            Location from = ((MoveEvent) o).getFrom();
            double deltaY = to.getY() - from.getY();

            if (deltaY < -0.15 && !isOnGround()) {
                if (buffer.increase(player.getUniqueId(), 1.0) > 6.0) {
                    profile.punish("Movement", "GroundSpoofC", String.format("Ground Spoof (Falling). dY: %.4f", deltaY), 1.0f);
                    buffer.reset(player.getUniqueId(), 3.0);
                }
            } else {
                buffer.decrease(player.getUniqueId(), 0.5);
            }
        }
    }

    private boolean isOnGround() {
        Location loc = profile.getTo();
        return loc.getBlock().getType().isSolid()
            || loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }
}