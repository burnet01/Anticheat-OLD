package win.ac.x.checks.v2.combat.reach;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.events.MoveEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public final class ReachAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Integer pendingEntityId = null;

    private static final int MAX_BACKTRACK = 8;
    private static final double BASE_REACH = 3.0;

    private Location lastEyePos = null;
    private Location lastEyePosPrev = null;
    private float lastYaw = 0, lastPitch = 0;
    private float lastPrevYaw = 0, lastPrevPitch = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_reach_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public ReachAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof CPacketEvent) {
            CPacketEvent e = (CPacketEvent) o;
            if (e.getPacketEvent().getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(e.getPacketEvent());
                if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    pendingEntityId = interact.getEntityId();
                }
            }
            return;
        }
        if (o instanceof MoveEvent) {
            MoveEvent me = (MoveEvent) o;
            if (profile != null) {
                lastEyePosPrev = lastEyePos;
                double eyeHeight = profile.isSneaking() ? 1.52 : 1.62;
                lastEyePos = me.getTo().clone().add(0, eyeHeight, 0);
                lastPrevYaw = lastYaw;
                lastPrevPitch = lastPitch;
                lastYaw = me.getTo().getYaw();
                lastPitch = me.getTo().getPitch();
            }
            return;
        }
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        if (pendingEntityId == null) return;

        Integer entityId = pendingEntityId;
        pendingEntityId = null;

        long ping = profile.getPing().stream().mapToLong(Long::longValue).average().isPresent()
                ? (long) profile.getPing().stream().mapToLong(Long::longValue).average().getAsDouble()
                : 0;

        int tickDelay = (int) Math.floor(ping / 50.0);
        if (tickDelay > MAX_BACKTRACK) tickDelay = MAX_BACKTRACK;
        if (tickDelay < 0) tickDelay = 0;

        Vector[] lookVectors = {
                direction(lastYaw, lastPitch),
                direction(lastPrevYaw, lastPitch),
                direction(lastPrevYaw, lastPrevPitch)
        };

        double maxReach = 3.0;
        if (profile.isSprinting()) maxReach += 0.05;
        if (ping > 50) maxReach += (ping * 0.001);

        double minDist = Double.MAX_VALUE;

        org.bukkit.entity.Entity target = win.ac.x.utils.cache.EntityCache.get(entityId);
        if (target == null) return;

        org.bukkit.util.BoundingBox box = target.getBoundingBox().clone();
        double appliedExpansion = (target instanceof Player) ? 0.08 : 0.25;
        box.expand(appliedExpansion);

        for (Vector look : lookVectors) {
            if (lastEyePos != null) {
                double d1 = rayEntryDistance(box, lastEyePos.toVector(), look);
                if (d1 >= 0) minDist = Math.min(minDist, d1);
            }
            if (lastEyePosPrev != null) {
                double d2 = rayEntryDistance(box, lastEyePosPrev.toVector(), look);
                if (d2 >= 0) minDist = Math.min(minDist, d2);
            }
        }

        if (minDist == Double.MAX_VALUE) return;

        double trueReach = minDist;

        if (trueReach > maxReach) {
            double over = trueReach - maxReach;

            if (over > 0.02) {
                if (trueReach > 10.0) return;
                if (buffer.increase(uuid, 1.0 + (over * 2.0)) > 10.0) {
                    profile.punish("Reach", "A", String.format("Reach dist=%.3f max=%.3f over=%.3f", trueReach, maxReach, over), 1.0f);
                    buffer.reset(uuid, 4.5);
                }
            }
        } else {
            buffer.decrease(uuid, 0.6);
        }
    }

    private double rayEntryDistance(org.bukkit.util.BoundingBox box, Vector origin, Vector dir) {
        double invX = 1.0 / dir.getX();
        double invY = 1.0 / dir.getY();
        double invZ = 1.0 / dir.getZ();

        double tMin = Math.min((box.getMinX() - origin.getX()) * invX, (box.getMaxX() - origin.getX()) * invX);
        double tMax = Math.max((box.getMinX() - origin.getX()) * invX, (box.getMaxX() - origin.getX()) * invX);

        double tyMin = Math.min((box.getMinY() - origin.getY()) * invY, (box.getMaxY() - origin.getY()) * invY);
        double tyMax = Math.max((box.getMinY() - origin.getY()) * invY, (box.getMaxY() - origin.getY()) * invY);

        if (tMin > tyMax || tyMin > tMax) return -1;
        tMin = Math.max(tMin, tyMin);
        tMax = Math.min(tMax, tyMax);

        double tzMin = Math.min((box.getMinZ() - origin.getZ()) * invZ, (box.getMaxZ() - origin.getZ()) * invZ);
        double tzMax = Math.max((box.getMinZ() - origin.getZ()) * invZ, (box.getMaxZ() - origin.getZ()) * invZ);

        if (tMin > tzMax || tzMin > tMax) return -1;
        tMin = Math.max(tMin, tzMin);
        tMax = Math.min(tMax, tzMax);

        if (Double.isNaN(tMin)) tMin = Double.NEGATIVE_INFINITY;
        if (Double.isNaN(tMax)) tMax = Double.POSITIVE_INFINITY;

        if (tMax < 0) return -1;
        if (tMin > 6.0) return -1;

        return Math.max(0.0, tMin);
    }

    private Vector direction(float yaw, float pitch) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double cosP = Math.cos(rp);
        return new Vector(-cosP * Math.sin(ry), -Math.sin(rp), cosP * Math.cos(ry));
    }
}