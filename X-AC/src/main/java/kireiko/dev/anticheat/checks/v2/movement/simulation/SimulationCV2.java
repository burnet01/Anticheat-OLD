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

public final class SimulationCV2 implements PacketCheckHandler {
    private static final double LIQUID_GRAVITY = 0.04D;
    private static final double LIQUID_Y_DRAG = 0.8D;
    private static final double LIQUID_XZ_DRAG = 0.8D;
    private static final double MAX_ASCENT_SPEED = 0.25D;
    private static final double MAX_DESCENT_SPEED = -0.4D;
    private static final double BUBBLE_UP_MAX = 0.72D;
    private static final double BUBBLE_DOWN_MAX = -0.51D;
    private static final double MAX_WATER_XZ_BASE = 0.20D;
    private static final double DEPTH_STRIDER_BONUS = 0.133D;
    private static final double MAX_LAVA_XZ = 0.18D;
    private static final double NOISE_FLOOR = 0.012D;
    private static final int LIQUID_ENTRY_GRACE_TICKS = 4;
    private static final double SURFACE_Y_THRESHOLD = 0.08D;
    private static final double WALL_ASCENT_MAX = 0.36D;
    private static final double WALL_ASCENT_CAP = 0.12D;
    private static final float FLAG_BUFFER_THRESHOLD = 8.0f;
    private static final float FLAG_BUFFER_RESET = 3.0f;

    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    private enum Tag {
        WATER, LAVA, BUBBLE_UP, BUBBLE_DOWN, SURFACE, BOBBING, SUBMERGED,
        SWIM_MODE, WALL_TOUCH, DEPTH_STRIDER, DOLPHIN_GRACE, SLOW_FALLING,
        ASCENDING, DESCENDING, SPRINTING, VELOCITY, TELEPORT_GRACE, LIQUID_ENTRY,
        RIPTIDE, CLIMBABLE, V_HOVER, V_CAP, V_PREDICT, H_CAP, H_DRAG
    }

    private static final class State {
        int hDragTicks, vHoverTicks;
        float buf;
        boolean lastWasInLiquid;
        int liquidEntryTick;
    }

    private final Map<UUID, State> statesMap = new HashMap<>();

    private State getState() {
        return statesMap.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new State());
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_simulation_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public SimulationCV2(PlayerProfile profile) {
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
        if (!isInLiquid(player)) {
            State st = getState();
            st.buf = Math.max(0f, st.buf - 0.15f);
            st.hDragTicks = 0;
            st.vHoverTicks = 0;
            st.lastWasInLiquid = false;
            return;
        }

        State st = getState();
        long now = System.currentTimeMillis();
        int currentTick = (int) (now / 50);

        if (!st.lastWasInLiquid) st.liquidEntryTick = currentTick;
        st.lastWasInLiquid = true;

        Location to = event.getTo();
        Location from = event.getFrom();
        double deltaXZ = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double lastDeltaXZ = profile.getPastLoc().isEmpty() ? 0 : Math.hypot(
            to.getX() - profile.getPastLoc().get(profile.getPastLoc().size() - 1).getX(),
            to.getZ() - profile.getPastLoc().get(profile.getPastLoc().size() - 1).getZ());
        double deltaY = to.getY() - from.getY();
        double lastDeltaY = from.getY() - (profile.getPastLoc().isEmpty() ? from.getY() : profile.getPastLoc().get(profile.getPastLoc().size() - 1).getY());
        int airTicks = profile.airTicks;

        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        if (isInLava(player)) tags.add(Tag.LAVA);
        else tags.add(Tag.WATER);

        if (deltaY > 0.5D && lastDeltaY > 0.3D) tags.add(Tag.BUBBLE_UP);
        if (deltaY < -0.4D && lastDeltaY < -0.3D) tags.add(Tag.BUBBLE_DOWN);

        boolean headInAir = !new Location(player.getWorld(),
            (int) Math.floor(to.getX()),
            (int) Math.floor(to.getY() + 1.62),
            (int) Math.floor(to.getZ())).getBlock().isLiquid();
        boolean feetInWater = new Location(player.getWorld(),
            (int) Math.floor(to.getX()),
            (int) Math.floor(to.getY()),
            (int) Math.floor(to.getZ())).getBlock().isLiquid();

        boolean atSurface = profile.isGround() || (airTicks == 0 && Math.abs(deltaY) < SURFACE_Y_THRESHOLD) || (headInAir && feetInWater);

        if (atSurface) {
            tags.add(Tag.SURFACE);
            if (Math.abs(deltaY) < 0.06D) tags.add(Tag.BOBBING);
        } else tags.add(Tag.SUBMERGED);

        if (player.isSwimming()) tags.add(Tag.SWIM_MODE);
        if (deltaY > 0.01D) tags.add(Tag.ASCENDING);
        if (deltaY < -0.01D && !tags.contains(Tag.BUBBLE_DOWN)) tags.add(Tag.DESCENDING);
        if (profile.isSprinting()) tags.add(Tag.SPRINTING);

        if (now - profile.getLastTeleport() < 500) tags.add(Tag.TELEPORT_GRACE);
        if (currentTick - st.liquidEntryTick <= LIQUID_ENTRY_GRACE_TICKS) tags.add(Tag.LIQUID_ENTRY);
        if (isOnClimbable(player)) tags.add(Tag.CLIMBABLE);
        if (isNearHorizontalCollision(to)) tags.add(Tag.WALL_TOUCH);

        if (tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.RIPTIDE) || tags.contains(Tag.CLIMBABLE)) {
            st.buf = Math.max(0f, st.buf - 0.1f);
            return;
        }

        if (tags.contains(Tag.LIQUID_ENTRY)) {
            st.buf = Math.max(0f, st.buf - 0.15f);
            st.hDragTicks = 0;
            return;
        }

        boolean skipVertical = tags.contains(Tag.BUBBLE_UP) || tags.contains(Tag.BUBBLE_DOWN);

        String flagReason = null;
        double severity = 0.0D;

        if (!skipVertical && !tags.contains(Tag.ASCENDING) && !tags.contains(Tag.SWIM_MODE)) {
            if (Math.abs(deltaY) < 1.0E-5D && airTicks == 0 && !profile.isGround()) {
                st.vHoverTicks++;
                if (st.vHoverTicks >= 5) {
                    tags.add(Tag.V_HOVER);
                    flagReason = String.format("Area Fail (V_Hover) Y=%.5f hoverTicks=%d tags=%s", deltaY, st.vHoverTicks, tags);
                    severity = 2.5D;
                }
            } else st.vHoverTicks = 0;
        } else st.vHoverTicks = 0;

        if (flagReason == null && !skipVertical) {
            double maxUp = MAX_ASCENT_SPEED;
            if (profile.isGround()) maxUp = 0.45D;
            else if (tags.contains(Tag.SURFACE)) maxUp = 0.15D;

            if (tags.contains(Tag.WALL_TOUCH) && tags.contains(Tag.ASCENDING)) maxUp = Math.max(maxUp, WALL_ASCENT_MAX);
            if (tags.contains(Tag.SWIM_MODE)) maxUp = Math.max(maxUp, 0.55D);

            double maxDown = MAX_DESCENT_SPEED;

            double yCap = tags.contains(Tag.SURFACE) ? 0.10D : 0.06D;
            if (tags.contains(Tag.WALL_TOUCH) && tags.contains(Tag.ASCENDING)) yCap = Math.max(yCap, WALL_ASCENT_CAP);

            if (deltaY > maxUp + yCap) {
                tags.add(Tag.V_CAP);
                double dev = deltaY - (maxUp + yCap);
                flagReason = String.format("Area Fail (V_Cap/Up) Y=%.3f max=%.3f dev=%.3f tags=%s", deltaY, maxUp, dev, tags);
                severity = 1.5D + dev * 10.0D;
            } else if (deltaY < maxDown - yCap) {
                tags.add(Tag.V_CAP);
                double dev = (maxDown - yCap) - deltaY;
                flagReason = String.format("Area Fail (V_Cap/Down) Y=%.3f min=%.3f dev=%.3f tags=%s", deltaY, maxDown, dev, tags);
                severity = 1.5D + dev * 10.0D;
            }
        }

        if (flagReason == null && !skipVertical && !tags.contains(Tag.SWIM_MODE)) {
            double predSink = (lastDeltaY - LIQUID_GRAVITY) * LIQUID_Y_DRAG;
            double predAscend = (lastDeltaY + LIQUID_GRAVITY) * LIQUID_Y_DRAG;
            double predZero = lastDeltaY * LIQUID_Y_DRAG;

            double vDiff = Math.abs(deltaY - predSink);
            vDiff = Math.min(vDiff, Math.abs(deltaY - predAscend));
            vDiff = Math.min(vDiff, Math.abs(deltaY - predZero));
            vDiff = Math.min(vDiff, Math.abs(deltaY));

            double threshold = 0.05D;
            if (tags.contains(Tag.SWIM_MODE)) threshold += 0.15D;
            if (tags.contains(Tag.SPRINTING) && !tags.contains(Tag.SWIM_MODE)) threshold += 0.04D;
            if (tags.contains(Tag.SURFACE)) threshold += 0.06D;
            if (tags.contains(Tag.WALL_TOUCH)) threshold += 0.12D;

            double reducedDiff = Math.max(0.0D, vDiff - threshold);
            if (reducedDiff > NOISE_FLOOR) {
                tags.add(Tag.V_PREDICT);
                flagReason = String.format("Area Fail (V_Predict) Y=%.4f lastY=%.4f diff=%.4f tags=%s", deltaY, lastDeltaY, vDiff, tags);
                severity = 1.0D + reducedDiff * 12.0D;
            }
        }

        if (flagReason == null) {
            double maxXZ;
            if (tags.contains(Tag.LAVA)) maxXZ = MAX_LAVA_XZ;
            else {
                maxXZ = MAX_WATER_XZ_BASE;
                if (tags.contains(Tag.SWIM_MODE)) maxXZ = Math.max(maxXZ, 0.55D);
            }

            if (tags.contains(Tag.SURFACE) || tags.contains(Tag.BOBBING)) {
                if (airTicks <= 5) maxXZ += 0.1D;
            }

            if (deltaXZ > maxXZ + 0.05D) {
                tags.add(Tag.H_CAP);
                double dev = deltaXZ - maxXZ;
                flagReason = String.format("Area Fail (H_Cap) XZ=%.3f max=%.3f dev=%.3f tags=%s", deltaXZ, maxXZ, dev, tags);
                severity = 1.5D + dev * 8.0D;
            }
        }

        if (flagReason == null && !tags.contains(Tag.SURFACE) && !tags.contains(Tag.BOBBING)) {
            double maxInput = tags.contains(Tag.LAVA) ? 0.08D : tags.contains(Tag.SWIM_MODE) ? 0.25D : 0.15D;
            double expectedMax = lastDeltaXZ * LIQUID_XZ_DRAG + maxInput + 0.04D;
            if (deltaXZ > expectedMax) {
                st.hDragTicks++;
                if (st.hDragTicks >= 3) {
                    tags.add(Tag.H_DRAG);
                    double dev = deltaXZ - expectedMax;
                    flagReason = String.format("Area Fail (H_Drag) XZ=%.3f expect≤%.3f consecutive=%d tags=%s", deltaXZ, expectedMax, st.hDragTicks, tags);
                    severity = 1.0D + dev * 6.0D;
                }
            } else st.hDragTicks = 0;
        } else st.hDragTicks = 0;

        if (flagReason != null) {
            st.buf += (float) severity;
            if (st.buf > FLAG_BUFFER_THRESHOLD) {
                profile.punish("Movement", "SimulationC", flagReason + String.format(" buffer=%.2f", st.buf),
                    (float) (st.buf / 10.0f));
                st.buf = Math.min(st.buf, FLAG_BUFFER_RESET);
            }
        } else {
            float decay = (tags.contains(Tag.SURFACE) || tags.contains(Tag.BOBBING)
                || tags.contains(Tag.BUBBLE_UP) || tags.contains(Tag.BUBBLE_DOWN)
                || tags.contains(Tag.WALL_TOUCH)) ? 0.18f : 0.08f;
            st.buf = Math.max(0f, st.buf - decay);
        }
    }

    private boolean isNearHorizontalCollision(Location loc) {
        double x = loc.getX(), y = loc.getY(), z = loc.getZ(), r = 0.36D;
        return hasSolid(loc, x + r, y + 0.20D, z) || hasSolid(loc, x - r, y + 0.20D, z)
            || hasSolid(loc, x, y + 0.20D, z + r) || hasSolid(loc, x, y + 0.20D, z - r)
            || hasSolid(loc, x + r, y + 1.00D, z) || hasSolid(loc, x - r, y + 1.00D, z)
            || hasSolid(loc, x, y + 1.00D, z + r) || hasSolid(loc, x, y + 1.00D, z - r);
    }

    private boolean hasSolid(Location loc, double x, double y, double z) {
        return new Location(loc.getWorld(), (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).getBlock().getType().isSolid();
    }

    private boolean isInLiquid(Player player) {
        return player.getLocation().getBlock().isLiquid();
    }

    private boolean isInLava(Player player) {
        Location loc = player.getLocation();
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY());
        int z = (int) Math.floor(loc.getZ());
        return loc.getWorld().getBlockAt(x, y, z).getType().name().contains("LAVA")
            || loc.getWorld().getBlockAt(x, y + 1, z).getType().name().contains("LAVA");
    }

    private boolean isOnClimbable(Player player) {
        String type = player.getLocation().getBlock().getType().name();
        return type.contains("LADDER") || type.contains("VINE") || type.contains("SCAFFOLDING");
    }
}