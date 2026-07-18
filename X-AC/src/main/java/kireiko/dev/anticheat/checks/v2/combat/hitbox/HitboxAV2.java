package kireiko.dev.anticheat.checks.v2.combat.hitbox;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.events.RotationEvent;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.*;

public final class HitboxAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Integer pendingEntityId = null;

    private static final int MAX_BACKTRACK = 8;
    private static final double PLAYER_EXPANSION = 0.08;
    private static final double NON_PLAYER_EXPANSION = 0.25;

    private Location lastEyePos = null;
    private float lastYaw = 0, lastPitch = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_hitbox_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public HitboxAV2(PlayerProfile profile) {
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
                double eyeHeight = profile.isSneaking() ? 1.52 : 1.62;
                lastEyePos = me.getTo().clone().add(0, eyeHeight, 0);
                lastYaw = me.getTo().getYaw();
                lastPitch = me.getTo().getPitch();
            }
            return;
        }
        if (!(o instanceof RotationEvent)) return;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        if (pendingEntityId == null) return;

        Integer entityId = pendingEntityId;
        pendingEntityId = null;

        Vector dir = direction(lastYaw, lastPitch);
        Vector eye = lastEyePos.toVector();
        if (eye == null) return;

        long ping = profile.getPing().stream().mapToLong(Long::longValue).average().isPresent()
                ? (long) profile.getPing().stream().mapToLong(Long::longValue).average().getAsDouble()
                : 0;

        double pingExpansion = Math.min(0.15, ping * 0.001);
        double sprintExpansion = profile.isSprinting() ? 0.05 : 0.0;
        double baseExpansion = PLAYER_EXPANSION + pingExpansion + sprintExpansion;

        org.bukkit.entity.Entity target = kireiko.dev.anticheat.utils.cache.EntityCache.get(entityId);
        if (target == null) return;

        org.bukkit.util.BoundingBox box = target.getBoundingBox().clone();
        box.expand(baseExpansion);

        double minDist = rayEntryDistance(box, eye, dir);
        if (minDist < 0) return;

        double maxReach = 3.0;
        if (profile.isSprinting()) maxReach += 0.05;
        if (ping > 50) maxReach += (ping * 0.001);

        double over = minDist - maxReach;

        if (over > 0.02) {
            if (minDist > 10.0) return;

            double severity = 1.0 + (over * 2.0);
            if (severity > 5.0) severity = 5.0;

            if (buffer.increase(uuid, severity) > 10.0) {
                profile.punish("Hitbox", "A", String.format("Hitbox Miss. Reach=%.3f Max=%.3f Over=%.3f", minDist, maxReach, over), 1.0f);
                buffer.reset(uuid, 4.0);
            }
        } else {
            buffer.decrease(uuid, 0.5);
        }
    }

    private Vector direction(float yaw, float pitch) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double cosP = Math.cos(rp);
        return new Vector(-cosP * Math.sin(ry), -Math.sin(rp), cosP * Math.cos(ry));
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
}