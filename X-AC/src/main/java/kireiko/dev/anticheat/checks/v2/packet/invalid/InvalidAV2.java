package kireiko.dev.anticheat.checks.v2.packet.invalid;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.RotationEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.millennium.vectors.Vec2f;
import org.bukkit.entity.Player;

import java.util.*;

public final class InvalidAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final double MAX_PITCH = 90.0;
    private static final double MIN_PITCH = -90.0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 5);
        return new ConfigLabel("v2_invalid_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public InvalidAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof RotationEvent) {
            RotationEvent e = (RotationEvent) o;
            PlayerProfile pf = e.getProfile();
            Player player = pf.getPlayer();
            UUID uuid = player.getUniqueId();

            Vec2f to = e.getTo();
            double pitch = to.getY();

            if (pitch > MAX_PITCH || pitch < MIN_PITCH) {
                pf.punish("Invalid", "A", String.format("Invalid pitch: %.2f (Limits: %.0f to %.0f)", pitch, MIN_PITCH, MAX_PITCH), 1.0f);
            } else {
                buffer.decrease(uuid, 0.1);
            }
        }
    }
}