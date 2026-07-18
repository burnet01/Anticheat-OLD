package kireiko.dev.anticheat.checks.v2.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BadPacketCV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, Long> useTimes = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_badpacket_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BadPacketCV2(PlayerProfile profile) {
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

            if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
                useTimes.put(uuid, System.currentTimeMillis());
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
                if (wrapper.getAction() == DiggingAction.RELEASE_USE_ITEM) {
                    long now = System.currentTimeMillis();
                    long start = useTimes.getOrDefault(uuid, 0L);
                    long duration = now - start;

                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand == null || hand.getType() == Material.AIR) {
                        hand = player.getInventory().getItemInOffHand();
                    }

                    if (hand != null && hand.getType().name().contains("BOW")) {
                        if (duration < 60) {
                            double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                            if (buffer.increase(uuid, 2.0) > 4.0) {
                                pf.punish("BadPacket", "C", String.format("FastBow Release. Duration: %dms", duration), 1.0f);
                                buffer.reset(uuid, 2.0);
                            }
                        } else {
                            buffer.decrease(uuid, 0.25);
                        }
                    }
                }
            }
        }
    }
}