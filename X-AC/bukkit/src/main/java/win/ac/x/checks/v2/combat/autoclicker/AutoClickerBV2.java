package win.ac.x.checks.v2.combat.autoclicker;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import java.util.*;

public final class AutoClickerBV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastClickNano = 0;
    private long lastDelayNano = 0;
    private int identicalStreak = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_autoclicker_b", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public AutoClickerBV2(PlayerProfile profile) {
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

        if (lastClickNano != 0) {
            long delay = now - lastClickNano;
            double delayMs = delay / 1_000_000.0;

            if (delayMs < 10) return;

            if (delayMs <= 250) {
                long diff = Math.abs(delay - lastDelayNano);

                if (diff < 1_000_000) {
                    identicalStreak++;
                    if (identicalStreak > 3) {
                        if (buffer.increase(uuid, 2.0) > 5.0) {
                            profile.punish("AutoClicker", "B", String.format("Identical Delays. Streak: %d, Diff: %.2fms", identicalStreak, diff / 1_000_000.0), 1.0f);
                            buffer.reset(uuid, 4.0);
                        }
                    }
                } else {
                    identicalStreak = 0;
                    buffer.decrease(uuid, 0.4);
                }

                lastDelayNano = delay;
            } else {
                identicalStreak = 0;
            }
        }
        lastClickNano = now;
    }
}