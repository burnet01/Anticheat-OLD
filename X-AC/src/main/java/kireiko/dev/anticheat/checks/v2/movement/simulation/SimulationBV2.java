package kireiko.dev.anticheat.checks.v2.movement.simulation;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.*;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class SimulationBV2 implements PacketCheckHandler {
    private static final double ELYTRA_DRAG_XZ = 0.99D;
    private static final double ELYTRA_DRAG_Y = 0.98D;
    private static final double GRAVITY = 0.08D;
    private static final double LIFT_FACTOR = 0.1D;
    private static final double THRUST_FACTOR = 0.04D;
    private static final double THRUST_Y_MULT = 3.2D;
    private static final double STEER_FACTOR = 0.1D;
    private static final double FIREWORK_IMPULSE = 0.1D;
    private static final double FIREWORK_TARGET = 1.5D;
    private static final double FIREWORK_CORRECTION = 0.5D;
    private static final double H_TOLERANCE = 0.065D;
    private static final double V_TOLERANCE = 0.055D;
    private static final double DIR_TOLERANCE = 0.12D;
    private static final double SHARP_TURN_H_GRACE = 0.12D;
    private static final double SHARP_TURN_DIR_GRACE = 0.18D;
    private static final double PITCH_SNAP_V_GRACE = 0.12D;
    private static final double PITCH_SNAP_DIR_GRACE = 0.10D;
    private static final double SLOW_FALL_V_GRACE = 0.15D;
    private static final double VL_DECAY_MULT = 0.990D;
    private static final double VL_DECAY_CONST = 0.012D;
    private static final double VL_FLAG_THRESHOLD = 50.0D;
    private static final double DRIFT_FLAG_THRESHOLD = 0.50D;
    private static final double DRIFT_DECAY = 0.002D;
    private static final double MAX_NATURAL_XZ_SPEED = 3.8D;
    private static final double MAX_BOOST_XZ_SPEED = 6.0D;
    private static final double MAX_NATURAL_GAIN = 0.20D;
    private static final double SHARP_TURN_YAW_THOLD = 25.0D;
    private static final double TURN_GAIN_GRACE = 0.30D;
    private static final double STALL_PITCH = -25.0D;
    private static final double STALL_SPEED_THOLD = 1.8D;
    private static final int ENERGY_WINDOW = 20;
    private static final double MAX_ENERGY_RATE = 0.85D;
    private static final int BOOST_GRACE_TICKS = 36;
    private static final int VELOCITY_GRACE_TICKS = 8;

    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private enum Tag {
        FIREWORK_BOOST, BOOST_START, BOOST_ACTIVE, BOOST_GRACE, SLOW_FALLING,
        RIPTIDE_LAUNCH, VELOCITY, GLIDE_INIT, TELEPORT_GRACE, DIVING, CLIMBING,
        LEVEL_FLIGHT, LIFT_ACTIVE, SHARP_TURN, PITCH_SNAP, GROUND_TOUCH, WALL_TOUCH,
        LIQUID_TOUCH, UNDER_BLOCK, SPEED_CAP, STALL, ENERGY, SPURIOUS_GAIN, LOW_DRAG,
        PREDICT_H, PREDICT_V, DIRECTION, ILLEGAL_AIR_JUMP
    }

    private static final class State {
        int stallTicks, lowDragTicks, predictHTicks, predictVTicks, directionTicks;
        final Deque<Double> energySamples = new ArrayDeque<>(ENERGY_WINDOW + 1);
        double physicsVL, driftScore;
        int hViolTicks, vViolTicks, dirViolTicks;
    }

    private final Map<UUID, State> statesMap = new HashMap<>();

    private State getState() {
        return statesMap.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new State());
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_simulation_b", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public SimulationBV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof MoveEvent)) return;
        MoveEvent event = (MoveEvent) o;
        if (profile.isIgnoreFirstTick()) return;

        Player player = profile.getPlayer();
        State st = getState();

        if (!player.isGliding()) {
            st.physicsVL = Math.max(0, st.physicsVL * VL_DECAY_MULT - VL_DECAY_CONST);
            st.driftScore = Math.max(0, st.driftScore - DRIFT_DECAY);
            st.energySamples.clear();
            resetTransientCounters(st);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - profile.getLastTeleport() < 500) {
            st.physicsVL = Math.max(0, st.physicsVL * 0.5);
            st.driftScore = Math.max(0, st.driftScore * 0.5);
            st.energySamples.clear();
            resetTransientCounters(st);
            return;
        }

        Location to = event.getTo();
        Location from = event.getFrom();
        double deltaXZ = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double lastDeltaXZ = profile.getPastLoc().isEmpty() ? 0 : Math.hypot(
            to.getX() - profile.getPastLoc().get(profile.getPastLoc().size() - 1).getX(),
            to.getZ() - profile.getPastLoc().get(profile.getPastLoc().size() - 1).getZ());
        double deltaY = to.getY() - from.getY();
        double lastDeltaY = from.getY() - (profile.getPastLoc().isEmpty() ? from.getY() : profile.getPastLoc().get(profile.getPastLoc().size() - 1).getY());
        float pitch = to.getPitch();
        float yaw = to.getYaw();

        int slowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING) ?
            Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SLOW_FALLING)).getAmplifier() + 1 : 0;

        EnumSet<Tag> tags = buildTags(player, pitch, lastDeltaY, slowFalling);
        boolean canPredict = canStrictlyPredict(tags);

        if (tags.contains(Tag.GLIDE_INIT) || tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.VELOCITY)) {
            st.physicsVL = Math.max(0, st.physicsVL * 0.7);
            st.driftScore = Math.max(0, st.driftScore * 0.5);
            st.energySamples.clear();
            resetTransientCounters(st);
            return;
        }

        if (canPredict && !tags.contains(Tag.LIFT_ACTIVE) && deltaY > 0.0D && lastDeltaY <= 0.0D && pitch >= -10.0f) {
            tags.add(Tag.ILLEGAL_AIR_JUMP);
            profile.punish("Movement", "SimulationB", String.format(
                "Area Fail (ElytraJump) Y=%.4f lastY=%.4f pitch=%.1f tags=%s", deltaY, lastDeltaY, pitch, tags), 1.0f);
            return;
        }

        double cap = tags.contains(Tag.FIREWORK_BOOST) ? MAX_BOOST_XZ_SPEED : MAX_NATURAL_XZ_SPEED;
        if (tags.contains(Tag.SLOW_FALLING)) cap = Math.min(cap, 1.2D);
        if (tags.contains(Tag.WALL_TOUCH) || tags.contains(Tag.GROUND_TOUCH)) cap += 0.35D;

        if (deltaXZ > cap + 0.05D) {
            tags.add(Tag.SPEED_CAP);
            profile.punish("Movement", "SimulationB", String.format(
                "Area Fail (SpeedCap) XZ=%.3f cap=%.3f tags=%s", deltaXZ, cap, tags),
                (float) (2.0D + (deltaXZ - cap) * 7.0D) / 10.0f);
            return;
        }

        if (canPredict) {
            double effectiveMaxGain = MAX_NATURAL_GAIN;
            if (tags.contains(Tag.SHARP_TURN)) {
                double turnFactor = Math.min(1.0D, 25.0D / 65.0D);
                effectiveMaxGain += TURN_GAIN_GRACE * turnFactor;
            }
            double gain = deltaXZ - (lastDeltaXZ * ELYTRA_DRAG_XZ);
            if (gain > effectiveMaxGain) {
                tags.add(Tag.SPURIOUS_GAIN);
                addViolation(st, 1.5D + (gain - effectiveMaxGain) * 8.0D, gain - effectiveMaxGain);
            }

            if (!tags.contains(Tag.LIFT_ACTIVE) && !tags.contains(Tag.SHARP_TURN) && tags.contains(Tag.LEVEL_FLIGHT)) {
                double expectedMax = lastDeltaXZ * ELYTRA_DRAG_XZ + 0.05D;
                if (deltaXZ > expectedMax + 0.06D) {
                    st.lowDragTicks++;
                    if (st.lowDragTicks >= 4) {
                        tags.add(Tag.LOW_DRAG);
                        addViolation(st, 1.0D + (deltaXZ - expectedMax) * 5.0D, deltaXZ - expectedMax);
                    }
                } else st.lowDragTicks = 0;
            } else st.lowDragTicks = 0;

            if (tags.contains(Tag.CLIMBING) && deltaXZ > STALL_SPEED_THOLD) {
                st.stallTicks++;
                if (st.stallTicks >= 3) {
                    tags.add(Tag.STALL);
                    addViolation(st, 1.5D + (deltaXZ - STALL_SPEED_THOLD) * 4.0D, deltaXZ - STALL_SPEED_THOLD);
                }
            } else st.stallTicks = 0;

            double speedSq = deltaXZ * deltaXZ + deltaY * deltaY;
            st.energySamples.addLast(speedSq);
            if (st.energySamples.size() > ENERGY_WINDOW) st.energySamples.pollFirst();
            if (st.energySamples.size() == ENERGY_WINDOW) {
                double energyRate = (speedSq - st.energySamples.peekFirst()) / ENERGY_WINDOW;
                if (energyRate > MAX_ENERGY_RATE) {
                    tags.add(Tag.ENERGY);
                    addViolation(st, 1.0D + (energyRate - MAX_ENERGY_RATE) * 3.0D, energyRate - MAX_ENERGY_RATE);
                }
            }

            double pitchRad = Math.toRadians(pitch);
            double yawRad = Math.toRadians(yaw);
            double sinYaw = Math.sin(yawRad);
            double cosYaw = Math.cos(yawRad);
            double sinPitch = Math.sin(pitchRad);
            double cosPitch = Math.cos(pitchRad);

            double lookX = -sinYaw * cosPitch;
            double lookY = -sinPitch;
            double lookZ = cosYaw * cosPitch;
            double horizLook = Math.sqrt(lookX * lookX + lookZ * lookZ);

            double lastMotionX = from.getX() - (profile.getPastLoc().isEmpty() ? from.getX() : profile.getPastLoc().get(profile.getPastLoc().size() - 1).getX());
            double lastMotionZ = from.getZ() - (profile.getPastLoc().isEmpty() ? from.getZ() : profile.getPastLoc().get(profile.getPastLoc().size() - 1).getZ());

            double mX = lastMotionX, mY = lastDeltaY, mZ = lastMotionZ;
            mY -= GRAVITY;

            if (mY < 0.0D && horizLook > 0.0D) {
                double cosPitchSq = cosPitch * cosPitch;
                double lift = mY * -LIFT_FACTOR * cosPitchSq;
                mY += lift;
                mX += (lookX * lift) / horizLook;
                mZ += (lookZ * lift) / horizLook;
            }

            if (horizLook > 0.0D) {
                if (lookY < 0.0D) {
                    double dive = lastDeltaXZ * -lookY * THRUST_FACTOR;
                    mY -= dive * THRUST_Y_MULT;
                    mX += (lookX * dive) / horizLook;
                    mZ += (lookZ * dive) / horizLook;
                } else if (lookY > 0.0D) {
                    double climb = lastDeltaXZ * lookY * THRUST_FACTOR;
                    mY += climb * THRUST_Y_MULT;
                    mX -= (lookX * climb) / horizLook;
                    mZ -= (lookZ * climb) / horizLook;
                }
            }

            if (horizLook > 0.0D) {
                mX += ((lookX / horizLook) * lastDeltaXZ - mX) * STEER_FACTOR;
                mZ += ((lookZ / horizLook) * lastDeltaXZ - mZ) * STEER_FACTOR;
            }

            mX *= ELYTRA_DRAG_XZ;
            mY *= ELYTRA_DRAG_Y;
            mZ *= ELYTRA_DRAG_XZ;

            double predictedH = Math.hypot(mX, mZ);
            double hDiff = deltaXZ - predictedH;
            double vDiff = Math.abs(deltaY - mY);
            double vecDiff = Math.hypot(to.getX() - from.getX() - mX, to.getZ() - from.getZ() - mZ);

            double hTol = H_TOLERANCE, vTol = V_TOLERANCE, dirTol = DIR_TOLERANCE;
            if (tags.contains(Tag.SHARP_TURN)) { hTol += SHARP_TURN_H_GRACE; dirTol += SHARP_TURN_DIR_GRACE; }
            if (tags.contains(Tag.PITCH_SNAP)) { vTol += PITCH_SNAP_V_GRACE; dirTol += PITCH_SNAP_DIR_GRACE; }
            if (tags.contains(Tag.SLOW_FALLING)) vTol += SLOW_FALL_V_GRACE;

            if (hDiff > hTol) {
                st.hViolTicks++;
                if (st.hViolTicks >= 3) { tags.add(Tag.PREDICT_H); addViolation(st, 1.0D + hDiff * 5.0D, hDiff); }
            } else st.hViolTicks = Math.max(0, st.hViolTicks - 1);

            if (vDiff > vTol && Math.abs(deltaY) > 0.03D) {
                st.vViolTicks++;
                if (st.vViolTicks >= 3) { tags.add(Tag.PREDICT_V); addViolation(st, 1.0D + vDiff * 5.0D, vDiff); }
            } else st.vViolTicks = Math.max(0, st.vViolTicks - 1);

            if (vecDiff > dirTol && deltaXZ > 0.45D && predictedH > 0.25D) {
                st.dirViolTicks++;
                if (st.dirViolTicks >= 4) { tags.add(Tag.DIRECTION); addViolation(st, 1.0D + vecDiff * 3.0D, vecDiff); }
            } else st.dirViolTicks = Math.max(0, st.dirViolTicks - 1);

            if (st.physicsVL > VL_FLAG_THRESHOLD && st.driftScore > DRIFT_FLAG_THRESHOLD) {
                String reason = tags.contains(Tag.PREDICT_H) ? "ElytraPredictH"
                    : tags.contains(Tag.PREDICT_V) ? "ElytraPredictV"
                    : tags.contains(Tag.DIRECTION) ? "ElytraDirection"
                    : tags.contains(Tag.SPURIOUS_GAIN) ? "SpuriousGain"
                    : tags.contains(Tag.LOW_DRAG) ? "LowDrag"
                    : tags.contains(Tag.STALL) ? "Stall"
                    : tags.contains(Tag.ENERGY) ? "Energy" : "Generic";

                profile.punish("Movement", "SimulationB", String.format(
                    "Area Fail (%s) XZ=%.3f predXZ=%.3f Y=%.3f predY=%.3f vl=%.1f drift=%.3f tags=%s",
                    reason, deltaXZ, predictedH, deltaY, mY, st.physicsVL, st.driftScore, tags),
                    (float) (st.physicsVL / 100.0f));

                st.physicsVL = Math.min(st.physicsVL, VL_FLAG_THRESHOLD * 0.6);
                st.driftScore = Math.min(st.driftScore, DRIFT_FLAG_THRESHOLD * 0.4);
                return;
            }
        } else resetTransientCounters(st);

        st.physicsVL = Math.max(0, st.physicsVL * VL_DECAY_MULT - VL_DECAY_CONST);
        st.driftScore = Math.max(0, st.driftScore - DRIFT_DECAY);
    }

    private void addViolation(State st, double vlIncrease, double rawDeviation) {
        st.physicsVL = Math.min(200.0D, st.physicsVL + vlIncrease);
        st.driftScore = Math.min(1.0D, st.driftScore + rawDeviation * 0.4D);
    }

    private EnumSet<Tag> buildTags(Player player, float pitch, double lastDeltaY, int slowFalling) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        tags.add(Tag.LEVEL_FLIGHT);
        if (pitch > 5.0f) tags.add(Tag.DIVING);
        else if (pitch < STALL_PITCH) tags.add(Tag.CLIMBING);
        if (lastDeltaY < -0.2D) tags.add(Tag.LIFT_ACTIVE);
        if (slowFalling > 0) tags.add(Tag.SLOW_FALLING);
        return tags;
    }

    private boolean canStrictlyPredict(EnumSet<Tag> tags) {
        return !tags.contains(Tag.FIREWORK_BOOST) && !tags.contains(Tag.VELOCITY)
            && !tags.contains(Tag.WALL_TOUCH) && !tags.contains(Tag.GROUND_TOUCH)
            && !tags.contains(Tag.LIQUID_TOUCH) && !tags.contains(Tag.UNDER_BLOCK);
    }

    private void resetTransientCounters(State st) {
        st.stallTicks = 0; st.lowDragTicks = 0;
        st.hViolTicks = 0; st.vViolTicks = 0; st.dirViolTicks = 0;
    }
}