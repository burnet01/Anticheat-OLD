package win.ac.x.checks.v2.combat.killaura;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.events.MoveEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.*;

public final class KillAuraEV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final double STEP = 0.2;
    private static final double MAX_RAY_LENGTH = 6.0;

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Location lastEye = null;
    private float lastYaw = 0, lastPitch = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_killaura_e", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraEV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof MoveEvent) {
            MoveEvent me = (MoveEvent) o;
            double eyeHeight = profile.isSneaking() ? 1.52 : 1.62;
            lastEye = me.getTo().clone().add(0, eyeHeight, 0);
            lastYaw = me.getTo().getYaw();
            lastPitch = me.getTo().getPitch();
            return;
        }
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent e = (CPacketEvent) o;
        if (e.getPacketEvent().getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(e.getPacketEvent());
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (profile == null || lastEye == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();

        Entity target = win.ac.x.utils.cache.EntityCache.get(interact.getEntityId());
        if (target == null) return;

        org.bukkit.util.BoundingBox box = target.getBoundingBox();
        double[] direction = computeDirection(lastYaw, lastPitch);
        double distance = distanceToBox(lastEye.getX(), lastEye.getY(), lastEye.getZ(), box);

        if (distance < 1.0 || distance > MAX_RAY_LENGTH) return;

        boolean blocked = rayIntersectsSolid(profile.getPlayer().getWorld(), lastEye.getX(), lastEye.getY(), lastEye.getZ(),
                direction[0], direction[1], direction[2], Math.min(distance, MAX_RAY_LENGTH));

        if (blocked) {
            if (buffer.increase(uuid, 1.0) > 5.0) {
                profile.punish("KillAura", "E", "Wall Hit (Async Raytrace)", 1.0f);
            }
        } else {
            buffer.decrease(uuid, 0.05);
        }
    }

    private static double[] computeDirection(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new double[] {
                -Math.sin(yawRad) * cosPitch,
                -Math.sin(pitchRad),
                Math.cos(yawRad) * cosPitch
        };
    }

    private static boolean rayIntersectsSolid(org.bukkit.World world, double ox, double oy, double oz,
                                               double dx, double dy, double dz,
                                               double maxDistance) {
        if (world == null) return false;
        for (double d = 1.0; d < maxDistance - 0.5; d += 0.4) {
            int bx = floor(ox + (dx * d));
            int by = floor(oy + (dy * d));
            int bz = floor(oz + (dz * d));

            if (world.getBlockAt(bx, by, bz).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double distanceToBox(double x, double y, double z, org.bukkit.util.BoundingBox box) {
        double dx = Math.max(Math.max(box.getMinX() - x, 0.0), x - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - y, 0.0), y - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - z, 0.0), z - box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}