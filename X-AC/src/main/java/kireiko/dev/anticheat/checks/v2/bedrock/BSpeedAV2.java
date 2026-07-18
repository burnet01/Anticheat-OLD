package kireiko.dev.anticheat.checks.v2.bedrock;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.millennium.vectors.Vec3;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class BSpeedAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final double MAX_SPEED_GROUND = 0.6;
    private static final double MAX_SPEED_AIR = 0.36;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 15);
        return new ConfigLabel("v2_bspeed_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BSpeedAV2(PlayerProfile profile) {
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

            if (player.getGameMode() == GameMode.CREATIVE
                    || player.getAllowFlight()
                    || player.isFlying()
                    || player.isGliding()
                    || player.isInsideVehicle()) {
                return;
            }

            if (pf.teleportTicks > 0 || pf.teleportTicks < 20) {
                buffer.decrease(uuid, 0.5);
                return;
            }

            if (player.hasPotionEffect(PotionEffectType.LEVITATION)
                    || player.isInWater()
                    || isInWeb(player)) {
                buffer.decrease(uuid, 0.5);
                return;
            }

            Vec3 delta = e.getDelta();
            double deltaXZ = Math.hypot(delta.xCoord, delta.zCoord);

            double limit = pf.ground ? MAX_SPEED_GROUND : MAX_SPEED_AIR;

            int speedLevel = getPotionLevel(player, PotionEffectType.SPEED);
            if (speedLevel > 0) {
                limit += (speedLevel * 0.07);
            }

            if (deltaXZ > limit) {
                double over = deltaXZ - limit;
                double buf = ((Number) localCfg.getOrDefault("buffer", 15)).doubleValue();
                if (buffer.increase(uuid, 1.0 + over) > 8.0) {
                    pf.punish("BSpeed", "A", String.format("Bedrock Speed limit. Speed: %.2f, Limit: %.2f", deltaXZ, limit), 1.0f);
                    buffer.reset(uuid, 4.0);
                }
            } else {
                buffer.decrease(uuid, 0.2);
            }
        }
    }

    private int getPotionLevel(Player player, PotionEffectType type) {
        return player.hasPotionEffect(type)
                ? player.getPotionEffect(type).getAmplifier() + 1
                : 0;
    }

    private boolean isInWeb(Player player) {
        return player.getLocation().getBlock().getType().name().contains("WEB");
    }
}