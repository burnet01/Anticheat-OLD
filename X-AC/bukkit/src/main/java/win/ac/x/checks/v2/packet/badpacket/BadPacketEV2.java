package win.ac.x.checks.v2.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;

public final class BadPacketEV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        return new ConfigLabel("v2_badpacket_e", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BadPacketEV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            int entityId = wrapper.getEntityId();

            if (entityId == player.getEntityId()) {
                pf.punish("BadPacket", "E", "Self Interaction (Own Entity ID: " + entityId + ")", 1.0f);
                event.setCancelled(true);
            }
        }
    }
}