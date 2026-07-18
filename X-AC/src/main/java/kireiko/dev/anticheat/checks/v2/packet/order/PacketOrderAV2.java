package kireiko.dev.anticheat.checks.v2.packet.order;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketOrderAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, PlaceOrderState> stateMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_packetorder_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public PacketOrderAV2(PlayerProfile profile) {
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

            PacketTypeCommon type = event.getPacketType();
            PlaceOrderState state = stateMap.computeIfAbsent(uuid, k -> new PlaceOrderState());

            if (type == PacketType.Play.Client.ANIMATION) {
                state.lastSwingTick = pf.airTicks;
            } else if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                int diff = pf.airTicks - state.lastSwingTick;

                if (diff > 2) {
                    if (state.pendingPlaceTick == -1) {
                        state.pendingPlaceTick = pf.airTicks;
                    }
                } else {
                    state.pendingPlaceTick = -1;
                    buffer.decrease(uuid, 0.2);
                }
            } else if (WrapperPlayClientPlayerFlying.isFlying(type)) {
                if (state.pendingPlaceTick != -1) {
                    if (pf.airTicks - state.pendingPlaceTick > 2) {
                        double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                        if (buffer.increase(uuid, 1.0) > 6.0) {
                            pf.punish("PacketOrder", "A", "Block placement without animation", 1.0f);
                            buffer.reset(uuid, 3.0);
                        }
                        state.pendingPlaceTick = -1;
                    }
                } else {
                    buffer.decrease(uuid, 0.1);
                }
            }
        }
    }

    private static class PlaceOrderState {
        int lastSwingTick = -100;
        int pendingPlaceTick = -1;
    }
}