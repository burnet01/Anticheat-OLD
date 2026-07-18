package kireiko.dev.anticheat.checks.v2.packet.crasher;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;

public final class CrasherAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final int MAX_CHANNEL_LENGTH = 32;
    private static final int MAX_PAYLOAD_SIZE = 32767;
    private static final int SUSPICIOUS_PAYLOAD_SIZE = 30000;
    private static final int BOOK_MAX_PAYLOAD = 25000;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 5);
        return new ConfigLabel("v2_crasher_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public CrasherAV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) return;

            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
            String channel = wrapper.getChannelName();

            if (channel == null) return;

            if (channel.length() > MAX_CHANNEL_LENGTH) {
                event.setCancelled(true);
                pf.punish("Crasher", "A", String.format("Invalid channel length: %d (max: %d)", channel.length(), MAX_CHANNEL_LENGTH), 1.0f);
                return;
            }

            byte[] payload = wrapper.getData();
            if (payload == null) return;

            int payloadSize = payload.length;
            boolean isBookChannel = channel.contains("book") || channel.contains("MC|BEdit")
                    || channel.contains("MC|BSign");
            int maxAllowedSize = isBookChannel ? BOOK_MAX_PAYLOAD : SUSPICIOUS_PAYLOAD_SIZE;

            if (payloadSize > MAX_PAYLOAD_SIZE) {
                event.setCancelled(true);
                pf.punish("Crasher", "A", String.format("Oversized payload (Crasher): %d bytes", payloadSize), 1.0f);
                return;
            }

            if (payloadSize > maxAllowedSize) {
                event.setCancelled(true);
                double buf = ((Number) localCfg.getOrDefault("buffer", 5)).doubleValue();
                if (buffer.increase(uuid, 2.0) > 3.0) {
                    pf.punish("Crasher", "A", String.format("Large payload on %s: %d bytes", channel, payloadSize), 1.0f);
                    buffer.reset(uuid, 1.0);
                }
            } else {
                buffer.decrease(uuid, 0.1);
            }
        }
    }
}