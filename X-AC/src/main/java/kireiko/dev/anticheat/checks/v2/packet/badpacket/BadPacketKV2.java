package kireiko.dev.anticheat.checks.v2.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.managers.CheckManager;
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