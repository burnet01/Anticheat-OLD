package kireiko.dev.anticheat.checks.v2.bedrock;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class BFlyAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_bfly_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BFlyAV2(PlayerProfile profile) {
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

            if (!pf.ground) {
                double deltaY = e.getDelta().yCoord;
                int airTicks = pf.airTicks;

                if (deltaY >= 0.0) {
                    int limit = 20;

                    if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                        limit += 10;
                    }

                    if (airTicks > limit) {
                        double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                        if (buffer.increase(uuid, 1.0) > 6.0) {
                            pf.punish("BFly", "A", String.format("Bedrock Fly (Hover/Rise). AirTicks: %d, Y: %.4f", airTicks, deltaY), 1.0f);
                            buffer.reset(uuid, 3.0);
                        }
                    } else {
                        buffer.decrease(uuid, 0.1);
                    }
                } else {
                    buffer.decrease(uuid, 0.25);
                }
            } else {
                buffer.decrease(uuid, 0.5);
            }
        }
    }

    private boolean isInWeb(Player player) {
        return player.getLocation().getBlock().getType().name().contains("WEB");
    }
}