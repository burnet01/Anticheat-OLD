package kireiko.dev.anticheat.checks.v2.movement.baritone;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.*;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.managers.CheckManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.*;

public final class BaritoneCV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();
    private final Map<UUID, BreakData> dataMap = new HashMap<>();

    private static final class BreakData {
        long lastBreakMs;
        long lastDeltaMs;
        int consistent;
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_baritone_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BaritoneCV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent cp = (CPacketEvent) o;
        var packetEvent = cp.getPacketEvent();
        if (packetEvent.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        if (!(packetEvent.getPlayer() instanceof org.bukkit.entity.Player)) return;

        WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(packetEvent);
        if (dig.getAction() != DiggingAction.START_DIGGING && dig.getAction() != DiggingAction.FINISHED_DIGGING) return;

        BreakData bd = dataMap.computeIfAbsent(profile.getPlayer().getUniqueId(), k -> new BreakData());
        long now = System.currentTimeMillis();

        if (bd.lastBreakMs > 0L) {
            long delta = now - bd.lastBreakMs;
            if (delta > 50L && delta < 1000L) {
                long diff = Math.abs(delta - bd.lastDeltaMs);

                long mod = delta % 50;
                boolean tickAligned = mod <= 5 || mod >= 45;

                if (diff <= 3L && !tickAligned) {
                    bd.consistent++;
                } else if (diff > 25L) {
                    bd.consistent = 0;
                } else {
                    bd.consistent = Math.max(0, bd.consistent - 2);
                }

                if (bd.consistent > 15) {
                    profile.punish("Movement", "BaritoneC",
                        String.format("Consistent break intervals d=%d c=%d", delta, bd.consistent), 1.0f);
                }

                bd.lastDeltaMs = delta;
            }
        }

        bd.lastBreakMs = now;
    }
}