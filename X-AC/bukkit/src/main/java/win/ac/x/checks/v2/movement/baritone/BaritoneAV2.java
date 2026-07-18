package win.ac.x.checks.v2.movement.baritone;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.*;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;

import java.util.*;

public final class BaritoneAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();
    private final Map<UUID, SampleData> samples = new HashMap<>();

    private static final class SampleData {
        int total;
        int snaps;

        void add(boolean snap) {
            total = Math.min(total + 1, 100);
            if (snap) snaps = Math.min(snaps + 1, 100);
            else snaps = Math.max(0, snaps - 1);
        }

        double snapDensity() {
            return total == 0 ? 0.0D : (double) snaps / (double) total;
        }
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_baritone_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BaritoneAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof RotationEvent)) return;
        RotationEvent event = (RotationEvent) o;

        if (profile.isIgnoreFirstTick()) return;

        var delta = event.getAbsDelta();
        float dyaw = Math.abs(delta.getX());

        if (dyaw < 5.0F) return;

        float yaw = event.getTo().getX();
        float normalized = ((yaw % 360.0F) + 360.0F) % 360.0F;
        float rem45 = normalized % 45.0F;

        boolean snap = rem45 < 0.5F || rem45 > 44.5F;

        SampleData sample = samples.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new SampleData());
        sample.add(snap);

        if (sample.total >= 40) {
            double density = sample.snapDensity();
            if (density > 0.85D && buffer.increase(profile.getPlayer().getUniqueId(), density > 0.90D ? 1.0D : 0.5D) > 8.0D) {
                profile.punish("Movement", "BaritoneA",
                    String.format("Rotational snap density=%.2f", density),
                    (float) ((density - 0.85D) * 20.0D + 5.0D) / 10.0f);
                buffer.reset(profile.getPlayer().getUniqueId(), 2.0D);
            } else {
                buffer.decrease(profile.getPlayer().getUniqueId(), 0.25D);
            }
        }
    }
}