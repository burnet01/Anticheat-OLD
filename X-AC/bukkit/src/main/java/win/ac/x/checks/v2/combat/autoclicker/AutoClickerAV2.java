package win.ac.x.checks.v2.combat.autoclicker;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import java.util.*;

public final class AutoClickerAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final int SAMPLE_SIZE = 40;

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastClickTime = 0;
    private final Deque<Integer> delays = new ArrayDeque<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_autoclicker_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public AutoClickerAV2(PlayerProfile profile) {
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
        long now = System.nanoTime();

        if (lastClickTime != 0) {
            long delayNanos = now - lastClickTime;
            double delayMillis = delayNanos / 1_000_000.0;

            if (delayMillis < 10) return;

            if (delayMillis <= 500) {
                delays.addLast((int) delayMillis);

                if (delays.size() > SAMPLE_SIZE) {
                    delays.pollFirst();
                }

                if (delays.size() == SAMPLE_SIZE) {
                    List<Integer> delayList = new ArrayList<>(delays);

                    double mean = delayList.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                    double variance = delayList.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0.0);
                    double stdDev = Math.sqrt(variance);
                    double cps = 1000.0 / mean;
                    double cv = (mean > 0) ? (stdDev / mean) : 0.0;

                    if (cps > 8.0) {
                        if (cv < 0.05) {
                            if (buffer.increase(uuid, 1.5) > 6.0) {
                                profile.punish("AutoClicker", "A", String.format("Robotic Consistency. CPS: %.1f, CV: %.4f", cps, cv), 1.0f);
                                buffer.reset(uuid, 5.0);
                            }
                        } else if (cv < 0.08 && cps > 14.0) {
                            if (buffer.increase(uuid, 1.0) > 10.0) {
                                profile.punish("AutoClicker", "A", String.format("Low Variance. CPS: %.1f, CV: %.3f", cps, cv), 1.0f);
                                buffer.reset(uuid, 7.0);
                            }
                        } else {
                            buffer.decrease(uuid, 0.5);
                        }
                    } else {
                        buffer.decrease(uuid, 0.25);
                    }
                }
            } else {
                delays.clear();
            }
        }
        lastClickTime = now;
    }
}