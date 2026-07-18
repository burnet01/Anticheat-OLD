package kireiko.dev.anticheat.checks.v2.packet.badpacket;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BadPacketJV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, StateData> stateMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 8);
        return new ConfigLabel("v2_badpacket_j", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BadPacketJV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) return;

            ClientVersion clientVersion = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            if (clientVersion.isOlderThan(ClientVersion.V_1_9)) return;

            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
            WrapperPlayClientEntityAction.Action action = wrapper.getAction();

            StateData state = stateMap.computeIfAbsent(uuid, k -> new StateData());

            switch (action) {
                case START_SNEAKING:
                    state.sneaking = true;
                    break;
                case STOP_SNEAKING:
                    state.sneaking = false;
                    break;
                case START_SPRINTING:
                    state.sprinting = true;
                    break;
                case STOP_SPRINTING:
                    state.sprinting = false;
                    break;
            }

            long now = System.currentTimeMillis();

            if (state.sneaking && state.sprinting) {
                if (state.impossibleStartTime == 0) {
                    state.impossibleStartTime = now;
                }

                long duration = now - state.impossibleStartTime;

                if (duration > 100) {
                    double buf = ((Number) localCfg.getOrDefault("buffer", 8)).doubleValue();
                    if (buffer.increase(uuid, 1.0) > 5.0) {
                        pf.punish("BadPacket", "J", "Impossible State: Sneak+Sprint > " + duration + "ms", 1.0f);
                        buffer.reset(uuid, 2.0);
                    }
                }
            } else {
                state.impossibleStartTime = 0;
                buffer.decrease(uuid, 0.1);
            }
        }
    }

    private static class StateData {
        boolean sneaking = false;
        boolean sprinting = false;
        long impossibleStartTime = 0;
    }
}