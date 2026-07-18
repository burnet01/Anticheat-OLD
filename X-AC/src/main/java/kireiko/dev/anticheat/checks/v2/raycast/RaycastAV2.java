package kireiko.dev.anticheat.checks.v2.raycast;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public final class RaycastAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_raycast_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public RaycastAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof CPacketEvent) {
            CPacketEvent e = (CPacketEvent) o;
            PacketReceiveEvent event = e.getPacketEvent();
            Player player = (Player) event.getPlayer();
            PlayerProfile pf = this.profile;
            UUID uuid = player.getUniqueId();

            if (skipBasic(pf, player)) return;

            Vector3i pos = null;
            Vector hit = null;
            String action = null;

            if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                WrapperPlayClientPlayerBlockPlacement p = new WrapperPlayClientPlayerBlockPlacement(event);
                pos = p.getBlockPosition();
                Vector3f c = p.getCursorPosition();
                hit = new Vector(pos.getX() + c.x, pos.getY() + c.y, pos.getZ() + c.z);
                action = "place";
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging d = new WrapperPlayClientPlayerDigging(event);
                if (d.getAction() == DiggingAction.START_DIGGING) {
                    pos = d.getBlockPosition();
                    action = "break";
                }
            }

            if (pos == null) return;

            Block block = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            if (block.getType().isAir() || block.isLiquid()) return;

            Location loc = pf.getTo();
            double eyeHeight = player.getEyeHeight();
            Vector eye = new Vector(loc.getX(), loc.getY() + eyeHeight, loc.getZ());
            Vector target = hit != null ? hit : new Vector(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double dist = eye.distance(target);

            double maxReach = player.getGameMode() == GameMode.CREATIVE ? 6.0 : 5.0;
            if (player.isInsideVehicle()) maxReach += 1.0;

            if (dist > maxReach) {
                double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                if (buffer.increase(uuid, 2.0) > 5.0) {
                    pf.punish("Raycast", "A", String.format("%s reach dist=%.2f max=%.2f", action, dist, maxReach), 1.0f);
                    buffer.reset(uuid, 0.0);
                }
                return;
            }

            if (!hasLineOfSight(player.getWorld(), eye, target, pos, player)) {
                double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                if (buffer.increase(uuid, 1.2) > 12.0) {
                    pf.punish("Raycast", "A", String.format("%s through wall", action), 1.0f);
                    buffer.reset(uuid, 0.0);
                }
            } else {
                buffer.decrease(uuid, 0.1);
            }
        }
    }

    private boolean skipBasic(PlayerProfile pf, Player player) {
        return pf == null || player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isGliding()
                || player.isInsideVehicle();
    }

    private boolean hasLineOfSight(World world, Vector from, Vector to, Vector3i target, Player player) {
        Vector dir = to.clone().subtract(from);
        double dist = dir.length();
        if (dist < 1.0E-5) return true;
        dir.multiply(1.0 / dist);

        for (double d = 0.5; d < dist - 0.5; d += 0.5) {
            Vector p = from.clone().add(dir.clone().multiply(d));
            int x = floor(p.getX());
            int y = floor(p.getY());
            int z = floor(p.getZ());

            if (x == target.getX() && y == target.getY() && z == target.getZ()) continue;
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir() && block.getType().isSolid()) {
                String name = block.getType().name().toLowerCase();
                if (!name.contains("glass") && !name.contains("fence") && !name.contains("wall")) {
                    return false;
                }
            }
        }
        return true;
    }

    private int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}