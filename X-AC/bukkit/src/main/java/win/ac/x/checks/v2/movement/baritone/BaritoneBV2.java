package win.ac.x.checks.v2.movement.baritone;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.*;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.*;

public final class BaritoneBV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();
    private final Map<UUID, BotData> dataMap = new HashMap<>();

    private static final class BotData {
        long lastDigMs;
        float lastYaw;
        double suspicion;
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_baritone_b", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BaritoneBV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof CPacketEvent) {
            CPacketEvent cp = (CPacketEvent) o;
            var packetEvent = cp.getPacketEvent();
            if (packetEvent.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
            if (!(packetEvent.getPlayer() instanceof org.bukkit.entity.Player)) return;

            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(packetEvent);
            if (dig.getAction() == DiggingAction.START_DIGGING) {
                dataMap.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new BotData()).lastDigMs = System.currentTimeMillis();
            }
            return;
        }
        if (o instanceof RotationEvent) {
            if (profile.isIgnoreFirstTick()) return;

            BotData bd = dataMap.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new BotData());
            if (System.currentTimeMillis() - bd.lastDigMs > 250L) {
                bd.suspicion = Math.max(0.0D, bd.suspicion - 0.2D);
                return;
            }

            RotationEvent event = (RotationEvent) o;
            var delta = event.getAbsDelta();
            float dyaw = Math.abs(delta.getX());
            float dpitch = Math.abs(delta.getY());

            double deltaXZ = profile.getPastLoc().size() < 2 ? 0 : Math.hypot(
                profile.getTo().getX() - profile.getFrom().getX(),
                profile.getTo().getZ() - profile.getFrom().getZ());
            boolean moving = deltaXZ > 0.10D;

            if (moving && dpitch == 0.0F && dyaw > 1.0F) bd.suspicion += 0.8D;
            else if (moving && bd.lastYaw > 10.0F && dyaw < 0.08F) bd.suspicion += 1.2D;
            else bd.suspicion = Math.max(0.0D, bd.suspicion - 0.3D);

            if (bd.suspicion > 7.0D && buffer.increase(profile.getPlayer().getUniqueId(), 1.0D) > 5.0D) {
                profile.punish("Movement", "BaritoneB",
                    String.format("Robotic mining pattern sus=%.2f", bd.suspicion),
                    (float) (bd.suspicion / 10.0f));
                buffer.reset(profile.getPlayer().getUniqueId(), 2.0D);
                bd.suspicion = 2.0D;
            }

            bd.lastYaw = dyaw;
        }
    }
}