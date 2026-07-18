package kireiko.dev.anticheat.checks.v2.combat.autoclicker;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import java.util.*;

public final class AutoClickerCV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Deque<Long> clickTimestamps = new ArrayDeque<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_autoclicker_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public AutoClickerCV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent e = (CPacketEvent) o;
        if (e.getPacketEvent().getPacketType() != PacketType.Play.Client.ANIMATION) return;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();

        clickTimestamps.addLast(now);

        while (!clickTimestamps.isEmpty() && (now - clickTimestamps.peekFirst()) > 1000) {
            clickTimestamps.pollFirst();
        }

        int rawClicks = clickTimestamps.size();
        double limit = 20.0;

        if (rawClicks > limit) {
            double over = rawClicks - limit;

            if (buffer.increase(uuid, 1.0 + (over * 0.5)) > 15.0) {
                profile.punish("AutoClicker", "C", String.format("High CPS (Hard Limit). CPS: %d, Limit: %.1f", rawClicks, limit), 1.0f);
                buffer.reset(uuid, 10.0);
            }
        } else {
            buffer.decrease(uuid, 0.4);
        }
    }
}