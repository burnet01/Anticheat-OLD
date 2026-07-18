package win.ac.x.checks.v2.combat.killaura;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.RotationEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class KillAuraCV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 12);
        return new ConfigLabel("v2_killaura_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraCV2(PlayerProfile profile) {
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

        double deltaYaw = Math.abs(event.getDelta().getX());
        double deltaPitch = Math.abs(event.getDelta().getY());
        double lastDeltaYaw = Math.abs(Math.abs(profile.getTo().getYaw()) - Math.abs(profile.getFrom().getYaw()));
        double lastDeltaPitch = Math.abs(Math.abs(profile.getTo().getPitch()) - Math.abs(profile.getFrom().getPitch()));

        boolean snapAndStop = lastDeltaYaw > 30.0 && deltaYaw < 0.1 && deltaYaw > 0.0;

        if (snapAndStop) {
            if (buffer.increase(uuid, 2.0) > 8.0) {
                profile.punish("KillAura", "C", String.format("Snap Aim. Last: %.1f, Now: %.1f", lastDeltaYaw, deltaYaw), 1.0f);
                buffer.reset(uuid, 4.0);
            }
        }

        buffer.decrease(uuid, 0.1);
    }
}