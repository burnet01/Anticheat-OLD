package kireiko.dev.anticheat.checks.v2.packet.order;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketOrderDV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, OrderData> dataMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_packetorder_d", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public PacketOrderDV2(PlayerProfile profile) {
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

            OrderData orderData = dataMap.computeIfAbsent(uuid, k -> new OrderData());

            if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
                orderData.lastSwingTick = pf.airTicks;
                orderData.pendingAttackTick = -1;
            } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    int diff = pf.airTicks - orderData.lastSwingTick;
                    if (diff > 10) {
                        if (orderData.pendingAttackTick == -1) {
                            orderData.pendingAttackTick = pf.airTicks;
                        }
                    }
                }
            }
        } else if (o instanceof MoveEvent) {
            MoveEvent e = (MoveEvent) o;
            PlayerProfile pf = e.getProfile();
            Player player = pf.getPlayer();
            UUID uuid = player.getUniqueId();

            OrderData orderData = dataMap.get(uuid);
            if (orderData == null) return;

            if (orderData.pendingAttackTick != -1) {
                if (pf.airTicks - orderData.pendingAttackTick > 2) {
                    double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                    if (buffer.increase(uuid, 1.0) > 5.0) {
                        pf.punish("PacketOrder", "D", "NoSwing (Attack without Animation)", 1.0f);
                        buffer.reset(uuid, 2.0);
                    }
                    orderData.pendingAttackTick = -1;
                }
            } else {
                buffer.decrease(uuid, 0.1);
            }
        }
    }

    private static class OrderData {
        int lastSwingTick = -1;
        int pendingAttackTick = -1;
    }
}