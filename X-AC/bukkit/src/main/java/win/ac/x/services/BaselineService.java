package win.ac.x.services;

import win.ac.x.api.player.PlayerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BaselineService {

    private static final Map<PlayerProfile, Baseline> baselines = new ConcurrentHashMap<>();
    private static final int BASELINE_PERIOD_TICKS = 600;
    private static final int SAMPLE_LIMIT = 200;

    public static void registerProfile(PlayerProfile profile) {
        baselines.put(profile, new Baseline(profile.getPlayer().getUniqueId()));
    }

    public static void unregisterProfile(PlayerProfile profile) {
        baselines.remove(profile);
    }

    public static Baseline getBaseline(PlayerProfile profile) {
        return baselines.get(profile);
    }

    public static class Baseline {
        private final UUID uuid;
        private final List<Double> yawDeltas = new ArrayList<>();
        private final List<Double> pitchDeltas = new ArrayList<>();
        private final List<Double> horizontalSpeeds = new ArrayList<>();
        private final List<Double> clickIntervals = new ArrayList<>();
        private final List<Double> gcdValues = new ArrayList<>();

        private final List<Double> recentYawDeltas = new ArrayList<>();
        private final List<Double> recentPitchDeltas = new ArrayList<>();
        private final List<Double> recentSpeeds = new ArrayList<>();
        private static final int RECENT_BUFFER_SIZE = 40;

        private int baselineTick = 0;
        private boolean established = false;

        private double yawMean = 0, yawStd = 1;
        private double pitchMean = 0, pitchStd = 1;
        private double speedMean = 0, speedStd = 1;
        private double clickMean = 0, clickStd = 1;

        private int consecutiveYawSpikes = 0;
        private int consecutivePitchSpikes = 0;
        private int consecutiveSpeedSpikes = 0;
        private int consecutiveClickSpikes = 0;

        private long lastClickTime = 0;

        Baseline(UUID uuid) {
            this.uuid = uuid;
        }

        public boolean isEstablished() { return established; }
        public int getBaselineTick() { return baselineTick; }
        public double getYawMean() { return yawMean; }
        public double getYawStd() { return yawStd; }
        public double getPitchMean() { return pitchMean; }
        public double getPitchStd() { return pitchStd; }
        public double getSpeedMean() { return speedMean; }
        public double getSpeedStd() { return speedStd; }
        public double getClickMean() { return clickMean; }
        public double getClickStd() { return clickStd; }

        public double[] getRecentYawDeltas() {
            synchronized (recentYawDeltas) {
                double[] arr = new double[recentYawDeltas.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = recentYawDeltas.get(i);
                return arr;
            }
        }

        public double[] getRecentPitchDeltas() {
            synchronized (recentPitchDeltas) {
                double[] arr = new double[recentPitchDeltas.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = recentPitchDeltas.get(i);
                return arr;
            }
        }

        public double[] getRecentSpeeds() {
            synchronized (recentSpeeds) {
                double[] arr = new double[recentSpeeds.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = recentSpeeds.get(i);
                return arr;
            }
        }

        private void addToRecent(List<Double> list, double value) {
            synchronized (list) {
                list.add(value);
                if (list.size() > RECENT_BUFFER_SIZE) {
                    list.remove(0);
                }
            }
        }

        public void tick() {
            if (!established) {
                baselineTick++;
                if (baselineTick >= BASELINE_PERIOD_TICKS) {
                    computeBaseline();
                    established = true;
                }
            }
        }

        public void addYawDelta(double delta) {
            if (established) return;
            if (Math.abs(delta) > 180) return;
            if (yawDeltas.size() >= SAMPLE_LIMIT) return;
            yawDeltas.add(Math.abs(delta));
        }

        public void addPitchDelta(double delta) {
            if (established) return;
            if (Math.abs(delta) > 90) return;
            if (pitchDeltas.size() >= SAMPLE_LIMIT) return;
            pitchDeltas.add(Math.abs(delta));
        }

        public void addHorizontalSpeed(double speed) {
            if (established) return;
            if (speed > 10) return;
            if (horizontalSpeeds.size() >= SAMPLE_LIMIT) return;
            horizontalSpeeds.add(speed);
        }

        public void addClickInterval() {
            if (established) return;
            long now = System.currentTimeMillis();
            if (lastClickTime > 0) {
                double interval = now - lastClickTime;
                if (interval > 0 && interval < 5000) {
                    if (clickIntervals.size() >= SAMPLE_LIMIT) return;
                    clickIntervals.add(interval);
                }
            }
            lastClickTime = now;
        }

        public double checkYawDeviation(double delta) {
            addToRecent(recentYawDeltas, delta);
            if (DatasetRecorder.isRecording(uuid)) DatasetRecorder.feedYawDelta(uuid, delta);
            if (!established || yawStd < 0.001) return 0;
            double absDelta = Math.abs(delta);
            double zScore = (absDelta - yawMean) / yawStd;

            if (zScore > 4.0) {
                consecutiveYawSpikes++;
                if (consecutiveYawSpikes >= 5) {
                    return zScore;
                }
            } else {
                consecutiveYawSpikes = Math.max(0, consecutiveYawSpikes - 1);
            }
            return 0;
        }

        public double checkPitchDeviation(double delta) {
            addToRecent(recentPitchDeltas, delta);
            if (DatasetRecorder.isRecording(uuid)) DatasetRecorder.feedPitchDelta(uuid, delta);
            if (!established || pitchStd < 0.001) return 0;
            double absDelta = Math.abs(delta);
            double zScore = (absDelta - pitchMean) / pitchStd;

            if (zScore > 4.0) {
                consecutivePitchSpikes++;
                if (consecutivePitchSpikes >= 5) {
                    return zScore;
                }
            } else {
                consecutivePitchSpikes = Math.max(0, consecutivePitchSpikes - 1);
            }
            return 0;
        }

        public double checkSpeedDeviation(double speed) {
            addToRecent(recentSpeeds, speed);
            if (DatasetRecorder.isRecording(uuid)) DatasetRecorder.feedHorizontalSpeed(uuid, speed);
            if (!established || speedStd < 0.001) return 0;
            double zScore = (speed - speedMean) / speedStd;

            if (zScore > 3.5) {
                consecutiveSpeedSpikes++;
                if (consecutiveSpeedSpikes >= 3) {
                    return zScore;
                }
            } else {
                consecutiveSpeedSpikes = Math.max(0, consecutiveSpeedSpikes - 1);
            }
            return 0;
        }

        public double checkClickDeviation(double interval) {
            if (!established || clickStd < 0.001) return 0;
            double zScore = (interval - clickMean) / clickStd;

            if (Math.abs(zScore) > 4.0) {
                consecutiveClickSpikes++;
                if (consecutiveClickSpikes >= 5) {
                    return Math.abs(zScore);
                }
            } else {
                consecutiveClickSpikes = Math.max(0, consecutiveClickSpikes - 1);
            }
            return 0;
        }

        private void computeBaseline() {
            yawMean = computeMean(yawDeltas);
            yawStd = Math.max(0.001, computeStd(yawDeltas, yawMean));

            pitchMean = computeMean(pitchDeltas);
            pitchStd = Math.max(0.001, computeStd(pitchDeltas, pitchMean));

            speedMean = computeMean(horizontalSpeeds);
            speedStd = Math.max(0.001, computeStd(horizontalSpeeds, speedMean));

            clickMean = computeMean(clickIntervals);
            clickStd = Math.max(0.001, computeStd(clickIntervals, clickMean));
            clickMean = clickMean > 0 ? clickMean : 150;
        }

        private double computeMean(List<Double> data) {
            if (data.isEmpty()) return 1.0;
            double sum = 0;
            for (double d : data) sum += d;
            return sum / data.size();
        }

        private double computeStd(List<Double> data, double mean) {
            if (data.size() < 2) return 1.0;
            double sumSq = 0;
            for (double d : data) {
                double diff = d - mean;
                sumSq += diff * diff;
            }
            return Math.sqrt(sumSq / data.size());
        }
    }
}
