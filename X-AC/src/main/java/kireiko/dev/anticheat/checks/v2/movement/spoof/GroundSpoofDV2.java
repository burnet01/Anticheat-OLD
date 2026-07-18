package kireiko.dev.anticheat.checks.v2.movement.spoof;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.*;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;

import java.util.*;

public final class GroundSpoofDV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 5);
        return new ConfigLabel("v2_groundspoof_d", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public GroundSpoofDV2(PlayerProfile profile) {
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

        if (!profile.isGround()) return;

        double y = profile.getTo().getY();
        double minHeight = profile.getTo().getWorld().getMinHeight();

        if (y < minHeight - 1.0) {
            if (buffer.increase(profile.getPlayer().getUniqueId(), 2.0) > 2.0) {
                profile.punish("Movement", "GroundSpoofD", String.format("Void Ground Claim. Y: %.2f", y), 1.0f);
                buffer.reset(profile.getPlayer().getUniqueId(), 5.0);
            }
        }
    }
}