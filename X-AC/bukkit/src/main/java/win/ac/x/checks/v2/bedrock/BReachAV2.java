package win.ac.x.checks.v2.bedrock;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public final class BReachAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final double MAX_REACH = 4.5;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_breach_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BReachAV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

            if (player.getGameMode() == GameMode.CREATIVE) return;

            int entityId = wrapper.getEntityId();
            Entity target = getEntityById(entityId);
            if (target == null) return;

            long ping = pf.transactionPing;
            double baseLimit = MAX_REACH;
            double maxReach = baseLimit + (ping * 0.003);

            if (pf.sprinting) {
                maxReach += 0.4;
            }

            Location eyeLoc = player.getEyeLocation();
            Vector eye = eyeLoc.toVector();

            Location targetLoc = target.getLocation();
            double dist = eye.distance(targetLoc.toVector());

            if (dist > maxReach && dist <= 10.0) {
                double over = dist - maxReach;
                double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                if (buffer.increase(uuid, 1.0 + over) > 15.0) {
                    pf.punish("BReach", "A", String.format("Bedrock Reach. Dist: %.2f, Max: %.2f", dist, maxReach), 1.0f);
                    event.setCancelled(true);
                    buffer.reset(uuid, 4.0);
                }
            } else {
                buffer.decrease(uuid, 0.25);
            }
        }
    }

    private Entity getEntityById(int entityId) {
        return win.ac.x.utils.cache.EntityCache.get(entityId);
    }
}