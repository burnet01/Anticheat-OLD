package win.ac.x.services;

import java.util.UUID;

public class FlagSnapshot {

    public String checkName;
    public String component;
    public String info;
    public UUID playerUuid;
    public long timestamp;
    public String label;

    public double[] driftWindow;
    public double driftMax;
    public double driftAvg;
    public double driftStd;

    public double horizontalSpeed;
    public double verticalSpeed;
    public double expectedMaxHorizontal;
    public int airTicks;

    public double yawZScore;
    public double pitchZScore;
    public double speedZScore;
    public double clickZScore;

    public double yawMean;
    public double yawStd;
    public double pitchMean;
    public double pitchStd;
    public double speedMean;
    public double speedStd;
    public double clickMean;
    public double clickStd;

    public double[] recentYawDeltas;
    public double[] recentPitchDeltas;
    public double[] recentSpeeds;

    public static FlagSnapshot forPhysics(
            String checkName, String component, String info, UUID playerUuid,
            double[] driftWindow, double driftMax, double driftAvg, double driftStd,
            double horizontalSpeed, double verticalSpeed, double expectedMaxHorizontal, int airTicks) {
        FlagSnapshot s = new FlagSnapshot();
        s.checkName = checkName;
        s.component = component;
        s.info = info;
        s.playerUuid = playerUuid;
        s.timestamp = System.currentTimeMillis();
        s.driftWindow = driftWindow;
        s.driftMax = driftMax;
        s.driftAvg = driftAvg;
        s.driftStd = driftStd;
        s.horizontalSpeed = horizontalSpeed;
        s.verticalSpeed = verticalSpeed;
        s.expectedMaxHorizontal = expectedMaxHorizontal;
        s.airTicks = airTicks;
        return s;
    }

    public static FlagSnapshot forBaseline(
            String checkName, String component, String info, UUID playerUuid,
            double yawZScore, double pitchZScore, double speedZScore, double clickZScore,
            double yawMean, double yawStd, double pitchMean, double pitchStd,
            double speedMean, double speedStd, double clickMean, double clickStd,
            double[] recentYawDeltas, double[] recentPitchDeltas, double[] recentSpeeds) {
        FlagSnapshot s = new FlagSnapshot();
        s.checkName = checkName;
        s.component = component;
        s.info = info;
        s.playerUuid = playerUuid;
        s.timestamp = System.currentTimeMillis();
        s.yawZScore = yawZScore;
        s.pitchZScore = pitchZScore;
        s.speedZScore = speedZScore;
        s.clickZScore = clickZScore;
        s.yawMean = yawMean;
        s.yawStd = yawStd;
        s.pitchMean = pitchMean;
        s.pitchStd = pitchStd;
        s.speedMean = speedMean;
        s.speedStd = speedStd;
        s.clickMean = clickMean;
        s.clickStd = clickStd;
        s.recentYawDeltas = recentYawDeltas;
        s.recentPitchDeltas = recentPitchDeltas;
        s.recentSpeeds = recentSpeeds;
        return s;
    }
}