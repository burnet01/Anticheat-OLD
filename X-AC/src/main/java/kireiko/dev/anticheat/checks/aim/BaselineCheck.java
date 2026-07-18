package kireiko.dev.anticheat.checks.aim;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.events.RotationEvent;
import kireiko.dev.anticheat.api.events.UseEntityEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.services.BaselineService;
import kireiko.dev.anticheat.services.BaselineService.Baseline;
import kireiko.dev.anticheat.services.DatasetRecorder;
import kireiko.dev.anticheat.services.FlagSnapshot;
import kireiko.dev.millennium.math.Simplification;

import java.util.Map;
import java.util.TreeMap;

public final class BaselineCheck implements PacketCheckHandler {

    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();
    private float vl = 0;
    private boolean inCombat = false;
    private long combatStartTime = 0;
    private long lastFlagTime = 0;

    public BaselineCheck(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass())) {
            this.localCfg = CheckManager.getConfig(this.getClass());
        }
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 5);
        localCfg.put("rotation_zscore_threshold", 4.0);
        localCfg.put("movement_zscore_threshold", 3.5);
        localCfg.put("click_zscore_threshold", 4.0);
        localCfg.put("consecutive_spikes", 3);
        return new ConfigLabel("baseline", localCfg);
    }

    @Override
    public void applyConfig(Map<String, Object> params) {
        localCfg = params;
    }

    @Override
    public Map<String, Object> getConfig() {
        return localCfg;
    }

    @Override
    public void event(Object o) {
        if (!(boolean) localCfg.get("enabled")) return;

        Baseline baseline = BaselineService.getBaseline(profile);
        if (baseline == null) return;

        baseline.tick();

        if (o instanceof RotationEvent) {
            RotationEvent event = (RotationEvent) o;
            if (profile.isIgnoreFirstTick() || profile.ignoreCinematic()) return;

            double yawDelta = event.getDelta().getX();
            double pitchDelta = event.getDelta().getY();

            baseline.addYawDelta(yawDelta);
            baseline.addPitchDelta(pitchDelta);

            if (baseline.isEstablished()) {
                double yawDeviation = baseline.checkYawDeviation(yawDelta);
                double pitchDeviation = baseline.checkPitchDeviation(pitchDelta);

                if (yawDeviation > 4.0) {
                    flag("Baseline", "YawSpike",
                        "yaw=" + Simplification.scaleVal(yawDelta, 2)
                        + " z=" + Simplification.scaleVal(yawDeviation, 2),
                        0.0f, 6,
                        yawDeviation, 0, 0, 0);
                }
                if (pitchDeviation > 4.0) {
                    flag("Baseline", "PitchSpike",
                        "pitch=" + Simplification.scaleVal(pitchDelta, 2)
                        + " z=" + Simplification.scaleVal(pitchDeviation, 2),
                        0.0f, 7,
                        0, pitchDeviation, 0, 0);
                }
            }
        }

        if (o instanceof MoveEvent) {
            MoveEvent event = (MoveEvent) o;
            if (profile.isIgnoreFirstTick()) return;

            double hSpeed = event.getDelta().speed();
            baseline.addHorizontalSpeed(hSpeed);

            if (baseline.isEstablished()) {
                double deviation = baseline.checkSpeedDeviation(hSpeed);
                if (deviation > 3.5) {
                    flag("Baseline", "SpeedSpike",
                        "speed=" + Simplification.scaleVal(hSpeed, 2)
                        + " z=" + Simplification.scaleVal(deviation, 2),
                        0.0f, 8,
                        0, 0, deviation, 0);
                }
            }
        }

        if (o instanceof UseEntityEvent) {
            UseEntityEvent event = (UseEntityEvent) o;
            if (event.isAttack() && !event.isCancelled()) {
                combatStartTime = System.currentTimeMillis();
                inCombat = true;
                baseline.addClickInterval();
            }
        }
    }

    private void flag(String check, String component, String info, float m, float vlAdd) {
        flag(check, component, info, m, vlAdd, 0, 0, 0, 0);
    }

    private void flag(String check, String component, String info, float m, float vlAdd,
                      double yawZ, double pitchZ, double speedZ, double clickZ) {
        long now = System.currentTimeMillis();
        if (now - lastFlagTime < 150) return;

        this.vl += vlAdd;
        float vlLimit = ((Number) localCfg.get("buffer")).floatValue() * 10f;
        if (this.vl > vlLimit) {
            captureAndRecord(check, component, info, yawZ, pitchZ, speedZ, clickZ);
            this.profile.punish(check, component, info, m);
            this.vl = vlLimit - 10;
            lastFlagTime = now;
        }
    }

    private void captureAndRecord(String check, String component, String info,
                                   double yawZ, double pitchZ, double speedZ, double clickZ) {
        try {
            Baseline baseline = BaselineService.getBaseline(profile);
            if (baseline == null) return;

            FlagSnapshot snapshot = FlagSnapshot.forBaseline(
                check, component, info, profile.getPlayer().getUniqueId(),
                yawZ, pitchZ, speedZ, clickZ,
                baseline.getYawMean(), baseline.getYawStd(),
                baseline.getPitchMean(), baseline.getPitchStd(),
                baseline.getSpeedMean(), baseline.getSpeedStd(),
                baseline.getClickMean(), baseline.getClickStd(),
                baseline.getRecentYawDeltas(), baseline.getRecentPitchDeltas(), baseline.getRecentSpeeds()
            );
            DatasetRecorder.record(snapshot);
        } catch (Exception ignored) {
        }
    }
}