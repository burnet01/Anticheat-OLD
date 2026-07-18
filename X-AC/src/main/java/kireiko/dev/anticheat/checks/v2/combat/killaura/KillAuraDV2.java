package kireiko.dev.anticheat.checks.v2.combat.killaura;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.RotationEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class KillAuraDV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 15);
        return new ConfigLabel("v2_killaura_d", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraDV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof RotationEvent)) return;
        RotationEvent event = (RotationEvent) o;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        float deltaYaw = Math.abs(event.getDelta().getX());
        float deltaPitch = Math.abs(event.getDelta().getY());

        if (Math.abs(profile.getTo().getPitch()) > 85.0f) {
            buffer.decrease(uuid, 0.25);
            return;
        }

        if (deltaYaw > 15.0 && deltaPitch == 0.0) {
            if (buffer.increase(uuid, 1.5) > 10.0) {
                profile.punish("KillAura", "D", String.format("Silent/Pitch Lock. dYaw: %.2f, dPitch: %.5f", deltaYaw, deltaPitch), 1.0f);
                buffer.reset(uuid, 5.0);
            }
            return;
        }

        if (deltaPitch > 10.0 && deltaYaw < 0.01 && deltaYaw > 0.0) {
            if (buffer.increase(uuid, 1.0) > 12.0) {
                profile.punish("KillAura", "D", String.format("Vertical Snap. dPitch: %.2f", deltaPitch), 1.0f);
            }
        } else {
            buffer.decrease(uuid, 0.25);
        }
    }
}