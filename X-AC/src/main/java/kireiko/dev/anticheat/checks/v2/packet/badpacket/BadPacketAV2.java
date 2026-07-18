package kireiko.dev.anticheat.checks.v2.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BadPacketAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, PacketCounter> packetMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        localCfg.put("max_packets", 800);
        return new ConfigLabel("v2_badpacket_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BadPacketAV2(PlayerProfile profile) {
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

            if (player.getGameMode() == GameMode.CREATIVE) return;

            PacketCounter counter = packetMap.computeIfAbsent(uuid, k -> new PacketCounter());
            counter.packets++;
            long now = System.currentTimeMillis();

            if (now - counter.lastClear > 1000) {
                int maxPackets = ((Number) localCfg.getOrDefault("max_packets", 800)).intValue();
                if (counter.packets > maxPackets) {
                    double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                    if (buffer.increase(uuid, 1.0) > 5.0) {
                        pf.punish("BadPacket", "A", "Packet Flood Detection. Rate: " + counter.packets + "/s", 1.0f);
                    }
                } else {
                    buffer.decrease(uuid, 0.5);
                }
                counter.packets = 0;
                counter.lastClear = now;
            }
        }
    }

    private static class PacketCounter {
        int packets = 0;
        long lastClear = System.currentTimeMillis();
    }
}