package win.ac.x.checks.v2.movement.spoof;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.*;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;
import org.bukkit.Location;

import java.util.*;

public final class GroundSpoofBV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 5);
        return new ConfigLabel("v2_groundspoof_b", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public GroundSpoofBV2(PlayerProfile profile) {
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
            Location loc = profile.getTo();
            double y = loc.getY();
            double yMod = Math.abs(y % 1.0);

            boolean valid = (yMod < 0.001 || yMod > 0.999) ||
                (Math.abs(yMod - 0.5) < 0.001) ||
                (yMod % 0.015625 < 0.001);

            if (!valid) {
                if (isOnGround()) valid = true;
            }

            if (!valid) {
                if (buffer.increase(player.getUniqueId(), 1.0) > 5.0) {
                    profile.punish("Movement", "GroundSpoofB", String.format("Phantom Ground (Invalid Y). Y: %.4f", y), 1.0f);
                    buffer.reset(player.getUniqueId(), 2.0);
                }
            } else {
                buffer.decrease(player.getUniqueId(), 0.25);
            }
        }
    }

    private boolean isOnGround() {
        Location loc = profile.getTo();
        return loc.getBlock().getType().isSolid()
            || loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }
}