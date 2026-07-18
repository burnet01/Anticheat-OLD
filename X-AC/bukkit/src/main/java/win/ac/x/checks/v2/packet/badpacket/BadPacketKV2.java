package win.ac.x.checks.v2.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;

public final class BadPacketKV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        return new ConfigLabel("v2_badpacket_k", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BadPacketKV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.WINDOW_CONFIRMATION) return;

            WrapperPlayClientWindowConfirmation wrapper = new WrapperPlayClientWindowConfirmation(event);
            short actionId = wrapper.getActionId();

            if (actionId < 0) {
                pf.punish("BadPacket", "K", "Negative Transaction ID: " + actionId, 1.0f);
                event.setCancelled(true);
            }

            int windowId = wrapper.getWindowId();
            if (windowId < 0 || windowId > 255) {
                pf.punish("BadPacket", "K", "Invalid Window ID: " + windowId, 1.0f);
                event.setCancelled(true);
            }
        }
    }
}