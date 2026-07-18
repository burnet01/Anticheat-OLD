package kireiko.dev.anticheat.checks.movement;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.events.SVelocityEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.services.DatasetRecorder;
import kireiko.dev.anticheat.services.FlagSnapshot;
import kireiko.dev.anticheat.services.PhysicsSimulationService;
import kireiko.dev.anticheat.services.PhysicsSimulationService.DriftRecord;
import kireiko.dev.millennium.phys.PhysicsEngine;
import kireiko.dev.millennium.phys.PhysicsEngine.ShadowState;
import kireiko.dev.millennium.math.Simplification;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * REMOVED from CheckManager — heuristic-based physics checks that false-flag hard.
 * ML detection (OnnxPhysicsCheck) replaced this. Do NOT re-enable.
 */
public final class PhysicsSimulationCheck implements PacketCheckHandler {

    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();
    private float vl = 0;
    private Vector pendingVelocity = null;
    private int velocityTimeout = 0;
    private long lastFlagTime = 0;

    public PhysicsSimulationCheck(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass())) {
            this.localCfg = CheckManager.getConfig(this.getClass());
        }
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 4);
        localCfg.put("drift_tolerance", 0.08);
        localCfg.put("cumulative_tolerance", 2.0);
        localCfg.put("window_ticks", 20);
        return new ConfigLabel("physics_simulation", localCfg);
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

        if (o instanceof SVelocityEvent) {
            SVelocityEvent event = (SVelocityEvent) o;
            pendingVelocity = event.getVelocity().clone();
            velocityTimeout = 10;
            return;
        }

        if (o instanceof MoveEvent) {
            MoveEvent event = (MoveEvent) o;
            Location to = event.getTo();
            Location from = event.getFrom();

            if (profile.isIgnoreFirstTick()) return;

            ShadowState shadow = PhysicsSimulationService.getShadowState(profile);
            if (shadow == null) return;

            Vector velocity = null;
            if (pendingVelocity != null && velocityTimeout > 0) {
                velocity = pendingVelocity;
                velocityTimeout--;
                if (velocityTimeout <= 0) {
                    pendingVelocity = null;
                }
            }

            boolean inWeb = PhysicsEngine.isInWeb(to);
            boolean inLiquid = PhysicsEngine.isInLiquid(to);
            boolean inWater = PhysicsEngine.isInWater(to);
            boolean onClimbable = PhysicsEngine.isOnClimbable(to);

            PhysicsSimulationService.onPlayerMove(
                profile, to, profile.isGround(),
                profile.isSprinting(), profile.isSneaking(),
                velocity
            );

            DriftRecord record = PhysicsSimulationService.getDriftRecord(profile);
            if (record == null) return;

            double driftTolerance = ((Number) localCfg.get("drift_tolerance")).doubleValue();
            double cumulativeTolerance = ((Number) localCfg.get("cumulative_tolerance")).doubleValue();

            kireiko.dev.millennium.vectors.Vec3 deltaVec = event.getDelta();
            Vector delta = new Vector(deltaVec.xCoord, deltaVec.yCoord, deltaVec.zCoord);
            double horizontalSpeed = PhysicsEngine.computeHorizontalSpeed(delta);
            double verticalSpeed = Math.abs(deltaVec.yCoord);

            UUID puid = profile.getPlayer().getUniqueId();
            if (DatasetRecorder.isRecording(puid)) {
                DatasetRecorder.feedHorizontalSpeed(puid, horizontalSpeed);
                DatasetRecorder.feedVerticalSpeed(puid, verticalSpeed);
                double tickDrift = record.getCurrentTickDrift();
                if (tickDrift > 0) DatasetRecorder.feedDrift(puid, tickDrift);
            }

            double expectedMaxHorizontal = PhysicsEngine.getExpectedMaxHorizontalSpeed(
                profile.isSprinting(), profile.isSneaking(),
                profile.isGround(), inWeb, profile.getAirTicks(),
                inWater, onClimbable
            );

            double horizontalExcess = horizontalSpeed - expectedMaxHorizontal;
            if (horizontalExcess > driftTolerance && !inLiquid && !inWeb && !onClimbable && profile.getAirTicks() < 5) {
                double excessMultiplier = horizontalExcess / Math.max(expectedMaxHorizontal, 0.001);
                double vlAdd = Math.min(20, excessMultiplier * 5);
                flag("PhysicsSim", "HorizontalSpeed",
                    "h=" + Simplification.scaleVal(horizontalSpeed, 4)
                    + " max=" + Simplification.scaleVal(expectedMaxHorizontal, 4)
                    + " excess=" + Simplification.scaleVal(horizontalExcess, 4),
                    (float) vlAdd, 8,
                    horizontalSpeed, verticalSpeed, expectedMaxHorizontal, profile.getAirTicks());
            }

            double expectedMaxVertical = PhysicsEngine.getExpectedMaxVerticalSpeed(
                false, profile.isGround(), inWeb, inWater, inLiquid, onClimbable);

            if (verticalSpeed > expectedMaxVertical + driftTolerance
                    && !inLiquid && !inWeb && !onClimbable
                    && pendingVelocity == null) {
                double excessMultiplier = (verticalSpeed - expectedMaxVertical) / Math.max(expectedMaxVertical, 0.001);
                double vlAdd = Math.min(25, excessMultiplier * 8);
                flag("PhysicsSim", "VerticalSpeed",
                    "v=" + Simplification.scaleVal(verticalSpeed, 4),
                    (float) vlAdd, 10,
                    horizontalSpeed, verticalSpeed, expectedMaxHorizontal, profile.getAirTicks());
            }

            if (profile.getAirTicks() > 3 && horizontalSpeed > PhysicsEngine.MAX_AIR_SPEED * 2.0
                    && !inLiquid && !inWeb && !onClimbable) {
                flag("PhysicsSim", "AirStrafe",
                    "h=" + Simplification.scaleVal(horizontalSpeed, 4)
                    + " air=" + profile.getAirTicks(),
                    0.0f, 6,
                    horizontalSpeed, verticalSpeed, expectedMaxHorizontal, profile.getAirTicks());
            }

            if (record.isAlertTriggeredAndReset() && !inLiquid && !inWeb && !onClimbable) {
                double avgDrift = record.getAverageDrift();
                double maxDrift = record.getMaxDrift();
                if (maxDrift > cumulativeTolerance) {
                    flag("PhysicsSim", "Drift",
                        "avg=" + Simplification.scaleVal(avgDrift, 4)
                        + " max=" + Simplification.scaleVal(maxDrift, 4),
                        0.0f, 12,
                        horizontalSpeed, verticalSpeed, expectedMaxHorizontal, profile.getAirTicks());
                }
            }

            if (velocity != null && velocityTimeout == 0) {
                pendingVelocity = null;
            }
        }
    }

    private void flag(String check, String component, String info, float m, float vlAdd) {
        flag(check, component, info, m, vlAdd, 0.0, 0.0, 0.0, 0);
    }

    private void flag(String check, String component, String info, float m, float vlAdd,
                      double hSpeed, double vSpeed, double expectedMaxH, int airTicks) {
        long now = System.currentTimeMillis();
        if (now - lastFlagTime < 100) return;

        this.vl += vlAdd;
        float vlLimit = ((Number) localCfg.get("buffer")).floatValue() * 10f;
        if (this.vl > vlLimit) {
            captureAndRecord(check, component, info, hSpeed, vSpeed, expectedMaxH, airTicks);
            this.profile.punish(check, component, info, m);
            this.vl = vlLimit - 10;
            lastFlagTime = now;
        }
    }

    private void captureAndRecord(String check, String component, String info,
                                   double hSpeed, double vSpeed, double expectedMaxH, int airTicks) {
        try {
            DriftRecord record = PhysicsSimulationService.getDriftRecord(profile);
            double[] driftWindow = record != null ? record.getDriftWindow() : new double[0];
            double driftMax = record != null ? record.getMaxDrift() : 0.0;
            double driftAvg = record != null ? record.getAverageDrift() : 0.0;
            double driftStd = record != null ? record.getDriftStandardDeviation() : 0.0;

            FlagSnapshot snapshot = FlagSnapshot.forPhysics(
                check, component, info, profile.getPlayer().getUniqueId(),
                driftWindow, driftMax, driftAvg, driftStd,
                hSpeed, vSpeed, expectedMaxH, airTicks
            );
            DatasetRecorder.record(snapshot);
        } catch (Exception ignored) {
        }
    }
}