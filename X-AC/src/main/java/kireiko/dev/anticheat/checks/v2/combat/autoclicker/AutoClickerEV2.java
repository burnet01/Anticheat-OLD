package kireiko.dev.anticheat.checks.v2.combat.autoclicker;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import java.util.*;

public final class AutoClickerEV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastClick = 0;
    private final Deque<Long> delays = new ArrayDeque<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_autoclicker_e", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public AutoClickerEV2(PlayerProfile profile) {
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

        if (lastClick != 0) {
            long delay = now - lastClick;

            if (delay < 10) return;

            if (delay <= 250) {
                delays.addLast(delay);

                if (delays.size() > 15) {
                    delays.pollFirst();
                }

                if (delays.size() == 15) {
                    checkGCD(uuid, new ArrayList<>(delays));
                }
            } else {
                delays.clear();
            }
        }
        lastClick = now;
    }

    private void checkGCD(UUID uuid, ArrayList<Long> list) {
        long gcd = 0;
        for (long d : list) {
            gcd = gcd(gcd, d);
        }

        if (gcd > 15) {
            if (buffer.increase(uuid, 1.5) > 6.0) {
                profile.punish("AutoClicker", "E", "Click Pattern (GCD). Grid Lock: " + gcd + "ms", 1.0f);
                buffer.reset(uuid, 5.0);
            }
        } else {
            buffer.decrease(uuid, 0.4);
        }
    }

    private long gcd(long a, long b) {
        if (b == 0) return a;
        return gcd(b, a % b);
    }
}