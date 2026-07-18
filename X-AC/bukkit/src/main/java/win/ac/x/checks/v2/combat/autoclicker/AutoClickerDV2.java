package win.ac.x.checks.v2.combat.autoclicker;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import java.util.*;

public final class AutoClickerDV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastClickNano = 0;
    private long lastDelayNano = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_autoclicker_d", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public AutoClickerDV2(PlayerProfile profile) {
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
            long diff = now - lastClickNano;

            if (diff < 8_000_000 && lastDelayNano > 0 && lastDelayNano < 8_000_000) {
                if (buffer.increase(uuid, 2.0) > 6.0) {
                    profile.punish("AutoClicker", "D", String.format("Hardware Double-Click (%.1f ms)", diff / 1_000_000.0), 1.0f);
                    buffer.reset(uuid, 5.0);
                }
            } else if (diff > 8_000_000) {
                buffer.decrease(uuid, 0.4);
            }

            lastDelayNano = diff;
        }

        lastClickNano = now;
    }
}