package kireiko.dev.anticheat.checks.v2.world.scaffold;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public final class ScaffoldAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_scaffold_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public ScaffoldAV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
            if (skipBasic(pf, player)) return;

            Location to = pf.getTo();
            float pitch = to.getPitch();
            float deltaPitch = Math.abs(to.getPitch() - pf.getFrom().getPitch());

            // Simplified scaffold detection: pitch snap when looking down
            if (pitch > 62.0F && deltaPitch > 15.0F) {
                double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                if (buffer.increase(uuid, 1.25) > 5.0) {
                    pf.punish("Scaffold", "A", String.format("Pitch Snap. pitch=%.1f dPitch=%.1f",
                            pitch, deltaPitch), 1.0f);
                    buffer.reset(uuid, 4.0);
                }
            } else {
                buffer.decrease(uuid, 0.4);
            }
        }
    }

    private boolean skipBasic(PlayerProfile pf, Player player) {
        return pf == null || player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isGliding()
                || player.isInsideVehicle();
    }
}