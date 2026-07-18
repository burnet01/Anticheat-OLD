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

import org.bukkit.util.Vector;

import java.util.*;

public final class KillAuraBV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();

    private double lastDeltaXZ = 0;
    private boolean lastSprinting = false;
    private boolean lastGround = false;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_killaura_b", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraBV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof MoveEvent) {
            MoveEvent me = (MoveEvent) o;
            lastDeltaXZ = Math.hypot(
                    me.getTo().getX() - me.getFrom().getX(),
                    me.getTo().getZ() - me.getFrom().getZ()
            );
            lastSprinting = profile.isSprinting();
            lastGround = profile.isGround();
            return;
        }
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent e = (CPacketEvent) o;
        if (e.getPacketEvent().getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(e.getPacketEvent());
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();

        if (lastSprinting && lastDeltaXZ > 0.22) {
            Vector move = new Vector(
                    profile.getTo().getX() - profile.getFrom().getX(),
                    0,
                    profile.getTo().getZ() - profile.getFrom().getZ()
            ).normalize();
            Vector look = getDirection(profile.getTo().getYaw(), 0).normalize();

            double dot = move.dot(look);

            if (dot < 0.6) {
                if (buffer.increase(uuid, 1.0) > 6.0) {
                    profile.punish("KillAura", "B", String.format("Directional Sprint. Dot: %.2f", dot), 1.0f);
                    buffer.reset(uuid, 3.0);
                }
                return;
            }
        }

        if (lastGround && lastSprinting) {
            if (lastDeltaXZ > 0.27) {
                if (buffer.increase(uuid, 1.5) > 8.0) {
                    profile.punish("KillAura", "B", String.format("KeepSprint. Speed: %.4f", lastDeltaXZ), 1.0f);
                    buffer.reset(uuid, 4.0);
                }
            } else {
                buffer.decrease(uuid, 0.2);
            }
        } else {
            buffer.decrease(uuid, 0.1);
        }
    }

    private Vector getDirection(float yaw, float pitch) {
        Vector vector = new Vector();
        double rotX = (double) yaw;
        double rotY = (double) pitch;
        vector.setY(-Math.sin(Math.toRadians(rotY)));
        double xz = Math.cos(Math.toRadians(rotY));
        vector.setX(-xz * Math.sin(Math.toRadians(rotX)));
        vector.setZ(xz * Math.cos(Math.toRadians(rotX)));
        return vector;
    }
}