package xac.cloud.session;

import win.ac.x.ml.data.ObjectML;
import win.ac.x.vectors.Vec2f;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerSession {

    private final String playerId;
    private final String serverId;

    // Millennium Aim ML buffers
    private final List<Vec2f> rawRotations;
    private final List<Vec2f> rnnRotations;
    private long lastAttackTime;

    // ONNX baseline buffer [40][4]
    private final float[][] baselineBuffer;
    private int baselineWriteIndex;
    private boolean baselineFull;

    // ONNX physics buffer [20][10]
    private final float[][] physicsBuffer;
    private int physicsWriteIndex;
    private boolean physicsFull;

    private double lastHorizontalSpeed;
    private float[] recentHSpeeds;
    private int recentHSpeedIndex;
    private int recentHSpeedCount;

    public PlayerSession(String playerId, String serverId, int rawBufferSize, int rnnBufferSize) {
        this.playerId = playerId;
        this.serverId = serverId;
        this.rawRotations = new CopyOnWriteArrayList<>();
        this.rnnRotations = new CopyOnWriteArrayList<>();
        this.lastAttackTime = 0L;

        this.baselineBuffer = new float[40][4];
        this.baselineWriteIndex = 0;
        this.baselineFull = false;

        this.physicsBuffer = new float[20][10];
        this.physicsWriteIndex = 0;
        this.physicsFull = false;

        this.lastHorizontalSpeed = 0.0;
        this.recentHSpeeds = new float[10];
        this.recentHSpeedIndex = 0;
        this.recentHSpeedCount = 0;
    }

    public String getPlayerId() { return playerId; }
    public String getServerId() { return serverId; }

    // --- Aim ML ---

    public void onAttack(long timestamp) {
        this.lastAttackTime = timestamp;
    }

    public boolean isAttackActive(long now, long timeoutMs) {
        return now - lastAttackTime <= timeoutMs;
    }

    public void addRotation(float yawDelta, float pitchDelta) {
        rawRotations.add(new Vec2f(yawDelta, pitchDelta));
        rnnRotations.add(new Vec2f(yawDelta, pitchDelta));
    }

    public boolean isLegacyReady() {
        return rawRotations.size() >= 600;
    }

    public boolean isRNNReady() {
        return rnnRotations.size() >= 150;
    }

    public List<Vec2f> drainRawRotations() {
        List<Vec2f> copy = new ArrayList<>(rawRotations);
        rawRotations.clear();
        return copy;
    }

    public List<Vec2f> drainRnnRotations() {
        List<Vec2f> copy = new ArrayList<>(rnnRotations);
        rnnRotations.clear();
        return copy;
    }

    public void clearStaleRotations() {
        if (!rawRotations.isEmpty()) rawRotations.clear();
        if (!rnnRotations.isEmpty()) rnnRotations.clear();
    }

    // --- ONNX Baseline ---

    public void feedBaseline(float yawDelta, float pitchDelta, float hSpeed, float clickInterval) {
        baselineBuffer[baselineWriteIndex][0] = yawDelta;
        baselineBuffer[baselineWriteIndex][1] = pitchDelta;
        baselineBuffer[baselineWriteIndex][2] = hSpeed;
        baselineBuffer[baselineWriteIndex][3] = clickInterval;
        baselineWriteIndex++;
        if (baselineWriteIndex >= 40) {
            baselineWriteIndex = 0;
            baselineFull = true;
        }
    }

    public boolean isBaselineReady() {
        return baselineFull;
    }

    public float[][] getBaselineBuffer() {
        return baselineBuffer;
    }

    // --- ONNX Physics ---

    public void setLastHorizontalSpeed(double speed) {
        this.lastHorizontalSpeed = speed;
    }

    public double getLastHorizontalSpeed() {
        return lastHorizontalSpeed;
    }

    public void feedPhysics(float[] features) {
        System.arraycopy(features, 0, physicsBuffer[physicsWriteIndex], 0, 10);
        physicsWriteIndex++;
        if (physicsWriteIndex >= 20) {
            physicsWriteIndex = 0;
            physicsFull = true;
        }
    }

    public boolean isPhysicsReady() {
        return physicsFull;
    }

    public float[][] getPhysicsBuffer() {
        return physicsBuffer;
    }

    public void feedRecentHSpeed(float speed) {
        recentHSpeeds[recentHSpeedIndex] = speed;
        recentHSpeedIndex = (recentHSpeedIndex + 1) % 10;
        recentHSpeedCount = Math.min(recentHSpeedCount + 1, 10);
    }

    public float computeSpeedVariance() {
        if (recentHSpeedCount < 2) return 0.0f;
        double sum = 0.0;
        for (int i = 0; i < recentHSpeedCount; i++) sum += recentHSpeeds[i];
        double mean = sum / recentHSpeedCount;
        double sumSq = 0.0;
        for (int i = 0; i < recentHSpeedCount; i++) {
            double diff = recentHSpeeds[i] - mean;
            sumSq += diff * diff;
        }
        return (float) Math.sqrt(sumSq / recentHSpeedCount);
    }

    // --- Reset ---

    public void resetAll() {
        rawRotations.clear();
        rnnRotations.clear();
        lastAttackTime = 0;
        baselineWriteIndex = 0;
        baselineFull = false;
        physicsWriteIndex = 0;
        physicsFull = false;
        lastHorizontalSpeed = 0.0;
        recentHSpeedIndex = 0;
        recentHSpeedCount = 0;
        for (int i = 0; i < 10; i++) recentHSpeeds[i] = 0.0f;
    }
}