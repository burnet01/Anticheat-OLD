package win.ac.x.services;

import win.ac.x.api.player.PlayerProfile;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.phys.PhysicsEngine;
import win.ac.x.phys.PhysicsEngine.ShadowState;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PhysicsSimulationService {

    private static final Map<PlayerProfile, ShadowState> shadowStates = new ConcurrentHashMap<>();
    private static final Map<PlayerProfile, DriftRecord> driftRecords = new ConcurrentHashMap<>();
    private static final Map<PlayerProfile, Instance> profileInstances = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<PlayerProfile> pendingRemoval = new ConcurrentLinkedQueue<>();

    private static final double DRIFT_THRESHOLD_PER_TICK = 0.05;
    private static final double CUMULATIVE_DRIFT_THRESHOLD = 1.5;
    private static final int DRIFT_WINDOW_TICKS = 20;
    private static final int MAX_DRIFT_DATA_POINTS = 40;

    public static void init() {
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            AsyncScheduler.run(() -> processPendingRemovals());
            AsyncScheduler.run(() -> tickSimulation());
            return TaskSchedule.tick(1);
        });
    }

    public static void registerProfile(PlayerProfile profile) {
        if (profile == null || profile.getPlayer() == null) return;
        if (profile.getPlayer().getInstance() == null) return;
        Pos pos = profile.getTo();
        ShadowState shadow = PhysicsEngine.createShadowState(pos.x(), pos.y(), pos.z());
        shadow.setBlockAccessor((bx, by, bz) -> {
            Player p = profile.getPlayer();
            if (p == null || p.getInstance() == null) return "";
            return p.getInstance().getBlock(new BlockVec(bx, by, bz)).name();
        });
        shadowStates.put(profile, shadow);
        driftRecords.put(profile, new DriftRecord());
        profileInstances.put(profile, profile.getPlayer().getInstance());
    }

    public static void unregisterProfile(PlayerProfile profile) {
        pendingRemoval.add(profile);
    }

    private static void processPendingRemovals() {
        PlayerProfile profile;
        while ((profile = pendingRemoval.poll()) != null) {
            shadowStates.remove(profile);
            driftRecords.remove(profile);
            profileInstances.remove(profile);
        }
    }

    public static void onPlayerMove(PlayerProfile profile, Pos realPosition,
                                     boolean onGround, boolean sprinting, boolean sneaking,
                                     Vec velocity) {
        ShadowState shadow = shadowStates.get(profile);
        if (shadow == null) return;

        long now = System.currentTimeMillis();
        DriftRecord record = driftRecords.get(profile);

        Instance instance = profile.getPlayer().getInstance();
        Instance prevInstance = profileInstances.get(profile);
        if (prevInstance != null && instance != prevInstance) {
            shadow.snapToReality(realPosition.x(), realPosition.y(), realPosition.z());
            profileInstances.put(profile, instance);
            if (record != null) record.reset();
            return;
        }
        if (instance != null) {
            profileInstances.put(profile, instance);
        }

        boolean teleported = (now - profile.getLastTeleport() < 500);
        if (teleported || profile.isIgnoreFirstTick()) {
            shadow.snapToReality(realPosition.x(), realPosition.y(), realPosition.z());
            if (record != null) record.reset();
            return;
        }

        double dx = shadow.getPosX() - realPosition.x();
        double dy = shadow.getPosY() - realPosition.y();
        double dz = shadow.getPosZ() - realPosition.z();
        double distSinceLastUpdate = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distSinceLastUpdate > 3.0) {
            shadow.snapToReality(realPosition.x(), realPosition.y(), realPosition.z());
            if (record != null) record.reset();
            return;
        }

        boolean wasInSpecial = shadow.isInSpecialState();
        String blockName = instance != null
            ? instance.getBlock(new BlockVec(realPosition.x(), realPosition.y(), realPosition.z())).name()
            : "";
        boolean nowInSpecial = PhysicsEngine.isInLiquid(blockName) || PhysicsEngine.isOnClimbable(blockName);

        if (wasInSpecial != nowInSpecial) {
            shadow.snapToReality(realPosition.x(), realPosition.y(), realPosition.z());
            if (record != null) record.reset();
            return;
        }

        shadow.updateState(realPosition.x(), realPosition.y(), realPosition.z(), onGround, sprinting, sneaking,
                velocity != null ? velocity.x() : null, velocity != null ? velocity.y() : null, velocity != null ? velocity.z() : null, now);

        if (record != null) {
            record.addDrift(shadow.getCumulativeDrift(), shadow.getDriftTicks(), shadow.computeDrift(realPosition.x(), realPosition.y(), realPosition.z()));
        }
    }

    public static DriftRecord getDriftRecord(PlayerProfile profile) {
        return driftRecords.get(profile);
    }

    public static void feedMoveForRecording(PlayerProfile profile, Pos realPosition) {
        ShadowState shadow = shadowStates.get(profile);
        if (shadow == null) return;

        long now = System.currentTimeMillis();
        if (profile.isIgnoreFirstTick()) return;
        if (now - profile.getLastTeleport() < 500) return;

        UUID uuid = profile.getPlayer().getUuid();
        if (!DatasetRecorder.isRecording(uuid)) return;

        double dx = shadow.getPosX() - realPosition.x();
        double dy = shadow.getPosY() - realPosition.y();
        double dz = shadow.getPosZ() - realPosition.z();
        double distSinceLastUpdate = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distSinceLastUpdate > 3.0) {
            shadow.snapToReality(realPosition.x(), realPosition.y(), realPosition.z());
            return;
        }

        shadow.updateState(realPosition.x(), realPosition.y(), realPosition.z(), profile.isGround(),
            profile.isSprinting(), profile.isSneaking(), null, null, null, now);

        double tickDrift = shadow.computeDrift(realPosition.x(), realPosition.y(), realPosition.z());
        DatasetRecorder.feedDrift(uuid, tickDrift);
        DatasetRecorder.feedSprintState(uuid, profile.isSprinting());

        DriftRecord record = driftRecords.get(profile);
        if (record != null) {
            record.addDrift(shadow.getCumulativeDrift(), shadow.getDriftTicks(), tickDrift);
        }
    }

    private static void tickSimulation() {
        Iterator<Map.Entry<PlayerProfile, ShadowState>> it = shadowStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PlayerProfile, ShadowState> entry = it.next();
            PlayerProfile profile = entry.getKey();
            ShadowState shadow = entry.getValue();

            if (!profile.getPlayer().isOnline()) {
                pendingRemoval.add(profile);
                continue;
            }

            Pos to = profile.getTo();
            if (to == null) {
                shadow.snapToReality(0, 0, 0);
                DriftRecord record = driftRecords.get(profile);
                if (record != null) record.reset();
                continue;
            }

            long now = System.currentTimeMillis();
            if (now - profile.getLastTeleport() < 500 || profile.isIgnoreFirstTick()) {
                shadow.snapToReality(to.x(), to.y(), to.z());
                DriftRecord record = driftRecords.get(profile);
                if (record != null) record.reset();
                continue;
            }

            shadow.updateState(
                to.x(), to.y(), to.z(),
                profile.isGround(),
                profile.isSprinting(),
                profile.isSneaking(),
                null, null, null,
                now
            );
        }
    }

    public static ShadowState getShadowState(PlayerProfile profile) {
        return shadowStates.get(profile);
    }

    public static class DriftRecord {
        private final double[] driftHistory = new double[MAX_DRIFT_DATA_POINTS];
        private int driftIndex = 0;
        private int driftCount = 0;
        private double maxDriftInWindow = 0.0;
        private double sumDriftInWindow = 0.0;
        private int windowTicks = 0;
        private boolean alertTriggered = false;

        public void addDrift(double cumulativeDrift, int driftTicks, double tickDrift) {
            driftHistory[driftIndex % MAX_DRIFT_DATA_POINTS] = tickDrift;
            driftIndex++;
            driftCount = Math.min(driftCount + 1, MAX_DRIFT_DATA_POINTS);

            sumDriftInWindow += tickDrift;
            windowTicks++;
            if (tickDrift > maxDriftInWindow) {
                maxDriftInWindow = tickDrift;
            }

            if (windowTicks >= DRIFT_WINDOW_TICKS) {
                checkWindow();
                windowTicks = 0;
                sumDriftInWindow = 0.0;
                maxDriftInWindow = 0.0;
            }
        }

        private void checkWindow() {
            double avgDrift = sumDriftInWindow / DRIFT_WINDOW_TICKS;
            if (avgDrift > DRIFT_THRESHOLD_PER_TICK && maxDriftInWindow > CUMULATIVE_DRIFT_THRESHOLD) {
                alertTriggered = true;
            }
        }

        public boolean isAlertTriggeredAndReset() {
            if (alertTriggered) {
                alertTriggered = false;
                return true;
            }
            return false;
        }

        public double getAverageDrift() {
            if (driftCount == 0) return 0.0;
            double sum = 0.0;
            int count = Math.min(driftCount, MAX_DRIFT_DATA_POINTS);
            for (int i = 0; i < count; i++) {
                sum += driftHistory[i];
            }
            return sum / count;
        }

        public double getMaxDrift() {
            double max = 0.0;
            int count = Math.min(driftCount, MAX_DRIFT_DATA_POINTS);
            for (int i = 0; i < count; i++) {
                if (driftHistory[i] > max) max = driftHistory[i];
            }
            return max;
        }

        public double getDriftStandardDeviation() {
            if (driftCount < 2) return 0.0;
            double avg = getAverageDrift();
            double sumSq = 0.0;
            int count = Math.min(driftCount, MAX_DRIFT_DATA_POINTS);
            for (int i = 0; i < count; i++) {
                double diff = driftHistory[i] - avg;
                sumSq += diff * diff;
            }
            return Math.sqrt(sumSq / count);
        }

        public double[] getDriftWindow() {
            int count = Math.min(driftCount, MAX_DRIFT_DATA_POINTS);
            if (count == 0) return new double[0];
            double[] window = new double[count];
            System.arraycopy(driftHistory, 0, window, 0, count);
            return window;
        }

        public double getCurrentTickDrift() {
            if (driftCount == 0) return 0.0;
            int idx = (driftIndex - 1 + MAX_DRIFT_DATA_POINTS) % MAX_DRIFT_DATA_POINTS;
            return driftHistory[idx];
        }

        public void reset() {
            driftIndex = 0;
            driftCount = 0;
            windowTicks = 0;
            sumDriftInWindow = 0.0;
            maxDriftInWindow = 0.0;
            alertTriggered = false;
            for (int i = 0; i < MAX_DRIFT_DATA_POINTS; i++) {
                driftHistory[i] = 0.0;
            }
        }
    }
}