package win.ac.x.checks.v2.packet.timer;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.MoveEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;
import org.bukkit.entity.Player;

import java.util.*;

public final class TimerAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();
    private long lastMoveNano = System.nanoTime();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 15);
        return new ConfigLabel("v2_timer_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public TimerAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof MoveEvent) {
            MoveEvent e = (MoveEvent) o;
            PlayerProfile pf = e.getProfile();
            Player player = pf.getPlayer();
            UUID uuid = player.getUniqueId();

            if (player.isInsideVehicle() || pf.isIgnoreFirstTick()) {
                buffer.decrease(uuid, 0.5);
                this.lastMoveNano = System.nanoTime();
                return;
            }

            if (pf.teleportTicks > 0) {
                buffer.decrease(uuid, 0.5);
                this.lastMoveNano = System.nanoTime();
                return;
            }

            long now = System.nanoTime();
            long elapsed = now - this.lastMoveNano;
            this.lastMoveNano = now;

            long expected = 50_000_000L;
            long ahead = expected - elapsed;

            if (ahead > 350_000_000L) {
                long aheadMs = ahead / 1_000_000L;
                double severity = aheadMs > 500L ? 2.0 : 1.0;
                double buf = ((Number) localCfg.getOrDefault("buffer", 15)).doubleValue();

                if (buffer.increase(uuid, severity) > buf) {
                    pf.punish("Timer", "A", String.format("Timer speed balance=+%dms", aheadMs), (float) severity);
                    buffer.reset(uuid, 5.0);
                }
            } else {
                buffer.decrease(uuid, 0.1);
            }
        }
    }
}