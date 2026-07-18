package win.ac.x.services;

import win.ac.x.X;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.phys.PhysicsEngine;
import win.ac.x.phys.PhysicsEngine.ShadowState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PhysicsSimulationService {

    private static final Map<PlayerProfile, ShadowState> shadowStates = new ConcurrentHashMap<>();
    private static final Map<PlayerProfile, DriftRecord> driftRecords = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<PlayerProfile> pendingRemoval = new ConcurrentLinkedQueue<>();

    private static final double DRIFT_THRESHOLD_PER_TICK = 0.05;
    private static final double CUMULATIVE_DRIFT_THRESHOLD = 1.5;
    private static final int DRIFT_WINDOW_TICKS = 20;
    private static final int MAX_DRIFT_DATA_POINTS = 40;

    public static void init() {
        Bukkit.getScheduler().runTaskTimer(X.getInstance(), () -> {
            AsyncScheduler.run(() -> processPendingRemovals());
            AsyncScheduler.run(() -> tickSimulation());
        }, 0L, 1L);
    }

    public static void registerProfile(PlayerProfile profile) {
        if (profile == null || profile.getPlayer() == null) return;
        Location to = profile.getTo();
        ShadowState shadow = PhysicsEngine.createShadowState(to.getX(), to.getY(), to.getZ());
        shadow.setBlockAccessor((bx, by, bz) ->
            new Location(profile.getPlayer().getWorld(), bx, by, bz).getBlock().getType().name());
        shadowStates.put(profile, shadow);
        driftRecords.put(profile, new DriftRecord());
    }

    public static void unregisterProfile(PlayerProfile profile) {
        pendingRemoval.add(profile);
    }

    private static void processPendingRemovals() {
        PlayerProfile profile;
        while ((profile = pendingRemoval.poll()) != null) {
            shadowStates.remove(profile);
            driftRecords.remove(profile);
        }
    }

    public static void onPlayerMove(PlayerProfile profile, Location realPosition,
                                     boolean onGround, boolean sprinting, boolean sneaking,
                                     Vector velocity) {
        ShadowState shadow = shadowStates.get(profile);
        if (shadow == null) return;

        long now = System.currentTimeMillis();
        DriftRecord record = driftRecords.get(profile);

        if (realPosition.getWorld() != null && !realPosition.getWorld().equals(profile.getPlayer().getWorld())) {
            shadow.snapToReality(realPosition.getX(), realPosition.getY(), realPosition.getZ());
            if (record != null) record.reset();
            return;
        }

        boolean teleported = (now - profile.getLastTeleport() < 500);
        if (teleported || profile.isIgnoreFirstTick()) {
            shadow.snapToReality(realPosition.getX(), realPosition.getY(), realPosition.getZ());
            if (record != null) record.reset();
            return;
        }

        double dx = shadow.getPosX() - realPosition.getX();
        double dy = shadow.getPosY() - realPosition.getY();
        double dz = shadow.getPosZ() - realPosition.getZ();
        double distSinceLastUpdate = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distSinceLastUpdate > 3.0) {
            shadow.snapToReality(realPosition.getX(), realPosition.getY(), realPosition.getZ());
            if (record != null) record.reset();
            return;
        }

        boolean wasInSpecial = shadow.isInSpecialState();
        String blockName = realPosition.getBlock().getType().name();
        boolean nowInSpecial = PhysicsEngine.isInLiquid(blockName) || PhysicsEngine.isOnClimbable(blockName);

        if (wasInSpecial != nowInSpecial) {
            shadow.snapToReality(realPosition.getX(), realPosition.getY(), realPosition.getZ());
            if (record != null) record.reset();
            return;
        }

        shadow.updateState(realPosition.getX(), realPosition.getY(), realPosition.getZ(),
            onGround, sprinting, sneaking,
            velocity != null ? velocity.getX() : null,
            velocity != null ? velocity.getY() : null,
            velocity != null ? velocity.getZ() : null,
            now);

        if (record != null) {
            record.addDrift(shadow.getCumulativeDrift(), shadow.getDriftTicks(),
                shadow.computeDrift(realPosition.getX(), realPosition.getY(), realPosition.getZ()));
        }
    }

    public static DriftRecord getDriftRecord(PlayerProfile profile) {
        return driftRecords.get(profile);
    }

    public static void feedMoveForRecording(PlayerProfile profile, Location realPosition) {
        ShadowState shadow = shadowStates.get(profile);
        if (shadow == null) return;

        long now = System.currentTimeMillis();
        if (profile.isIgnoreFirstTick()) return;
        if (now - profile.getLastTeleport() < 500) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        if (!DatasetRecorder.isRecording(uuid)) return;

        if (realPosition.getWorld() != null && !realPosition.getWorld().equals(profile.getPlayer().getWorld())) {
            shadow.snapToReality(realPosition.getX(), realPosition.getY(), realPosition.getZ());
            return;
        }

        double dx = shadow.getPosX() - realPosition.getX();
        double dy = shadow.getPosY() - realPosition.getY();
        double dz = shadow.getPosZ() - realPosition.getZ();
        double distSinceLastUpdate = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distSinceLastUpdate > 3.0) {
            shadow.snapToReality(realPosition.getX(), realPosition.getY(), realPosition.getZ());
            return;
        }

        shadow.updateState(realPosition.getX(), realPosition.getY(), realPosition.getZ(),
            profile.isGround(), profile.isSprinting(), profile.isSneaking(),
            null, null, null, now);

        double tickDrift = shadow.computeDrift(realPosition.getX(), realPosition.getY(), realPosition.getZ());
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

            Location to = profile.getTo();
            if (to == null || (to.getWorld() != null && !to.getWorld().equals(profile.getPlayer().getWorld()))) {
                shadow.snapToReality(to.getX(), to.getY(), to.getZ());
                DriftRecord record = driftRecords.get(profile);
                if (record != null) record.reset();
                continue;
            }

            long now = System.currentTimeMillis();
            if (now - profile.getLastTeleport() < 500 || profile.isIgnoreFirstTick()) {
                shadow.snapToReality(to.getX(), to.getY(), to.getZ());
                DriftRecord record = driftRecords.get(profile);
                if (record != null) record.reset();
                continue;
            }

            shadow.updateState(
                to.getX(), to.getY(), to.getZ(),
                profile.isGround(),
                profile.isSprinting(),
                profile.isSneaking(),
                null,
                null,
                null,
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
