package kireiko.dev.anticheat.checks.v2.movement.simulation;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.*;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SimulationDV2 implements PacketCheckHandler {
    private static final double BOAT_MAX_XZ = 1.25D;
    private static final double BOAT_ICE_MAX_XZ = 5.0D;
    private static final double BOAT_BLUE_ICE_MAX_XZ = 9.5D;
    private static final double BOAT_MAX_Y = 0.7D;
    private static final double HORSE_MAX_XZ = 1.15D;
    private static final double HORSE_MAX_Y = 1.05D;
    private static final double CAMEL_MAX_XZ = 1.40D;
    private static final double CAMEL_MAX_Y = 1.05D;
    private static final double LLAMA_MAX_XZ = 0.60D;
    private static final double LLAMA_MAX_Y = 0.65D;
    private static final double PIG_MAX_XZ = 0.50D;
    private static final double PIG_MAX_Y = 0.52D;
    private static final double STRIDER_MAX_XZ = 0.65D;
    private static final double STRIDER_MAX_Y = 0.50D;
    private static final double MINECART_MAX_XZ = 8.50D;
    private static final double MINECART_MAX_Y = 1.00D;
    private static final double UNKNOWN_MAX_XZ = 1.50D;
    private static final double UNKNOWN_MAX_Y = 1.05D;
    private static final double ENTITY_PUSH_MAX_XZ = 1.5D;
    private static final double ENTITY_PUSH_MAX_Y = 0.95D;
    private static final int MOUNT_GRACE_TICKS = 15;
    private static final int DISMOUNT_GRACE_TICKS = 20;
    private static final double BUFFER_FLAG_THRESHOLD = 6.0D;
    private static final double BUFFER_RESET_VALUE = 3.0D;

    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    private enum Tag {
        BOAT, HORSE, DONKEY, MULE, LLAMA, PIG, STRIDER, CAMEL, MINECART, UNKNOWN_MOUNT,
        ON_ICE, ON_BLUE_ICE, IN_LIQUID, BOUNCY_BLOCK, NEAR_ENTITY_PUSH,
        MOUNT_GRACE, DISMOUNT_GRACE, TELEPORT_GRACE, VELOCITY_RECEIVED, SERVER_FROZEN,
        V_SPEED, V_FLY, V_PUSH_XZ, V_PUSH_Y
    }

    private static final class State {
        long mountTick = -1000;
        long dismountTick = -1000;
        String vehicleTypeName = "";
    }

    private final Map<UUID, State> statesMap = new HashMap<>();

    private State getState() {
        return statesMap.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new State());
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_simulation_d", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public SimulationDV2(PlayerProfile profile) {
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
        long now = System.currentTimeMillis();

        boolean currentlyMounted = player.getVehicle() != null;
        boolean wasMounted = !st.vehicleTypeName.isEmpty();

        if (currentlyMounted && !wasMounted) {
            st.mountTick = now;
            Entity vehicle = player.getVehicle();
            st.vehicleTypeName = vehicle != null ? vehicle.getType().name() : "UNKNOWN";
        } else if (!currentlyMounted && wasMounted) {
            st.dismountTick = now;
            st.vehicleTypeName = "";
        }

        if (currentlyMounted) {
            Entity vehicle = player.getVehicle();
            if (vehicle == null) return;

            EnumSet<Tag> tags = buildVehicleTags(player, vehicle, st, now);
            Location to = event.getTo();
            Location from = event.getFrom();
            double distXZ = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            double deltaY = to.getY() - from.getY();
            org.bukkit.util.Vector vehicleVel = vehicle.getVelocity();

            double[] limits = getLimits(tags);
            double maxXZ = limits[0];
            double maxY = limits[1];

            if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.DISMOUNT_GRACE)) {
                if (deltaY > 1.5 || distXZ > 3.0) {
                    tags.remove(Tag.MOUNT_GRACE);
                    tags.remove(Tag.DISMOUNT_GRACE);
                }
            }

            if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.DISMOUNT_GRACE)
                || tags.contains(Tag.TELEPORT_GRACE)) {
                buffer.decrease(player.getUniqueId(), 0.25D);
                return;
            }

            String flagReason = null;
            double severity = 0.0D;

            if (deltaY > maxY && !tags.contains(Tag.BOUNCY_BLOCK) && vehicleVel.getY() < deltaY - 0.1D) {
                tags.add(Tag.V_FLY);
                double dev = deltaY - maxY;
                flagReason = String.format("Area Fail (V_Fly) Y=%.4f max=%.4f vVelY=%.4f tags=%s", deltaY, maxY, vehicleVel.getY(), tags);
                severity = dev * 10.0D;
            }

            if (flagReason == null) {
                double vehicleSpeedXZ = Math.hypot(vehicleVel.getX(), vehicleVel.getZ());
                double minVehicleSpeed = tags.contains(Tag.ON_ICE) || tags.contains(Tag.ON_BLUE_ICE) ? 0.5D : 0.1D;
                if (distXZ > maxXZ && vehicleSpeedXZ < minVehicleSpeed) {
                    tags.add(Tag.V_SPEED);
                    double dev = distXZ - maxXZ;
                    flagReason = String.format("Area Fail (V_Speed) XZ=%.4f max=%.4f vSpd=%.4f tags=%s", distXZ, maxXZ, vehicleSpeedXZ, tags);
                    severity = dev * 8.0D;
                }
            }

            applyFlag(flagReason, severity, tags, true);
            return;
        }

        if (isNearEntity(player)) {
            if (now - st.dismountTick < DISMOUNT_GRACE_TICKS * 50L) {
                buffer.decrease(player.getUniqueId(), 0.25D);
                return;
            }

            EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
            tags.add(Tag.NEAR_ENTITY_PUSH);

            Location to = event.getTo();
            Location from = event.getFrom();
            double distXZ = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            double deltaY = to.getY() - from.getY();

            String flagReason = null;
            double severity = 0.0D;

            double pushMaxXZ = ENTITY_PUSH_MAX_XZ;
            if (distXZ > pushMaxXZ) {
                tags.add(Tag.V_PUSH_XZ);
                double dev = distXZ - pushMaxXZ;
                flagReason = String.format("Area Fail (V_PushXZ) XZ=%.4f max=%.4f tags=%s", distXZ, pushMaxXZ, tags);
                severity = dev * 6.0D;
            }

            if (deltaY > ENTITY_PUSH_MAX_Y) {
                tags.add(Tag.V_PUSH_Y);
                double dev = deltaY - ENTITY_PUSH_MAX_Y;
                String extra = String.format("Area Fail (V_PushY) Y=%.4f max=%.4f tags=%s", deltaY, ENTITY_PUSH_MAX_Y, tags);
                if (flagReason == null || dev * 5.0D > severity) {
                    flagReason = extra;
                    severity = dev * 5.0D;
                }
            }

            applyFlag(flagReason, severity, tags, false);
            return;
        }

        buffer.decrease(player.getUniqueId(), 0.1D);
    }

    private EnumSet<Tag> buildVehicleTags(Player player, Entity vehicle, State st, long now) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        String vName = vehicle.getType().name().toUpperCase();
        if (vName.contains("BOAT")) tags.add(Tag.BOAT);
        else if (vName.contains("CAMEL")) tags.add(Tag.CAMEL);
        else if (vName.contains("HORSE")) tags.add(Tag.HORSE);
        else if (vName.contains("DONKEY")) tags.add(Tag.DONKEY);
        else if (vName.contains("MULE")) tags.add(Tag.MULE);
        else if (vName.contains("LLAMA")) tags.add(Tag.LLAMA);
        else if (vName.contains("PIG")) tags.add(Tag.PIG);
        else if (vName.contains("STRIDER")) tags.add(Tag.STRIDER);
        else if (vName.contains("MINECART")) tags.add(Tag.MINECART);
        else tags.add(Tag.UNKNOWN_MOUNT);

        Location loc = player.getLocation();
        if (isNearIceWide(loc)) {
            tags.add(Tag.ON_ICE);
            if (isNearBlueIce(loc)) tags.add(Tag.ON_BLUE_ICE);
        }
        if (isBouncy(loc)) tags.add(Tag.BOUNCY_BLOCK);
        if (loc.getBlock().isLiquid()) tags.add(Tag.IN_LIQUID);

        if (now - st.mountTick < MOUNT_GRACE_TICKS * 50L) tags.add(Tag.MOUNT_GRACE);
        if (now - st.dismountTick < DISMOUNT_GRACE_TICKS * 50L) tags.add(Tag.DISMOUNT_GRACE);
        if (now - profile.getLastTeleport() < 500) tags.add(Tag.TELEPORT_GRACE);

        return tags;
    }

    private double[] getLimits(EnumSet<Tag> tags) {
        double maxXZ, maxY;
        if (tags.contains(Tag.BOAT)) {
            if (tags.contains(Tag.ON_BLUE_ICE)) maxXZ = BOAT_BLUE_ICE_MAX_XZ;
            else if (tags.contains(Tag.ON_ICE)) maxXZ = BOAT_ICE_MAX_XZ;
            else maxXZ = BOAT_MAX_XZ;
            maxY = BOAT_MAX_Y;
        } else if (tags.contains(Tag.CAMEL)) { maxXZ = CAMEL_MAX_XZ; maxY = CAMEL_MAX_Y; }
        else if (tags.contains(Tag.HORSE) || tags.contains(Tag.DONKEY) || tags.contains(Tag.MULE)) { maxXZ = HORSE_MAX_XZ; maxY = HORSE_MAX_Y; }
        else if (tags.contains(Tag.LLAMA)) { maxXZ = LLAMA_MAX_XZ; maxY = LLAMA_MAX_Y; }
        else if (tags.contains(Tag.PIG)) { maxXZ = PIG_MAX_XZ; maxY = PIG_MAX_Y; }
        else if (tags.contains(Tag.STRIDER)) { maxXZ = STRIDER_MAX_XZ; maxY = STRIDER_MAX_Y; }
        else if (tags.contains(Tag.MINECART)) { maxXZ = MINECART_MAX_XZ; maxY = MINECART_MAX_Y; }
        else { maxXZ = UNKNOWN_MAX_XZ; maxY = UNKNOWN_MAX_Y; }
        return new double[]{maxXZ, maxY};
    }

    private void applyFlag(String flagReason, double severity, EnumSet<Tag> tags, boolean canEject) {
        if (flagReason != null) {
            Player player = profile.getPlayer();
            if (buffer.increase(player.getUniqueId(), severity) > BUFFER_FLAG_THRESHOLD) {
                profile.punish("Movement", "SimulationD", flagReason + String.format(" buffer=%.2f", buffer.get(player.getUniqueId())),
                    (float) (severity / 10.0f));
                if (canEject && player.getVehicle() != null) {
                    player.getVehicle().eject();
                    player.teleport(profile.getTo());
                }
                buffer.reset(player.getUniqueId(), BUFFER_RESET_VALUE);
            }
        } else {
            buffer.decrease(profile.getPlayer().getUniqueId(), 0.1D);
        }
    }

    private static final Map<UUID, Boolean> nearEntityCache = new ConcurrentHashMap<>();
    private long lastNearEntityRefresh = 0;
    private static final long REFRESH_INTERVAL_MS = 1000;

    private boolean isNearEntity(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastNearEntityRefresh > REFRESH_INTERVAL_MS) {
            lastNearEntityRefresh = now;
            Bukkit.getScheduler().runTask(kireiko.dev.anticheat.MX.getInstance(), () -> {
                boolean nearby = !player.getWorld().getNearbyEntities(player.getLocation(), 1.0, 1.0, 1.0,
                    e -> e instanceof Player && !e.equals(player)).isEmpty();
                nearEntityCache.put(player.getUniqueId(), nearby);
            });
        }
        return nearEntityCache.getOrDefault(player.getUniqueId(), false);
    }

    private boolean isNearIceWide(Location loc) {
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                String type = loc.getWorld().getBlockAt(loc.getBlockX() + ox, loc.getBlockY() - 1, loc.getBlockZ() + oz).getType().name();
                if (type.contains("ICE") || type.contains("FROST")) return true;
            }
        }
        return false;
    }

    private boolean isNearBlueIce(Location loc) {
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                String type = loc.getWorld().getBlockAt(loc.getBlockX() + ox, loc.getBlockY() - 1, loc.getBlockZ() + oz).getType().name();
                if (type.contains("BLUE_ICE")) return true;
            }
        }
        return false;
    }

    private boolean isBouncy(Location loc) {
        String type = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).getType().name();
        return type.contains("SLIME") || type.contains("HONEY");
    }
}