package kireiko.dev.anticheat.checks.v2.world.fastbreak;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FastBreakAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, DigInfo> digMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_fastbreak_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public FastBreakAV2(PlayerProfile profile) {
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

            if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
            if (skipBasic(pf, player)) return;

            WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = digging.getAction();

            if (action == DiggingAction.START_DIGGING) {
                digMap.put(uuid, new DigInfo(pf.airTicks, digging.getBlockPosition()));
                return;
            }

            if (action == DiggingAction.CANCELLED_DIGGING) {
                digMap.remove(uuid);
                return;
            }

            if (action != DiggingAction.FINISHED_DIGGING) return;

            DigInfo info = digMap.remove(uuid);
            if (info == null) return;

            int elapsed = pf.airTicks - info.startTick;
            Block block = player.getWorld().getBlockAt(info.pos.getX(), info.pos.getY(), info.pos.getZ());
            String blockName = block.getType().name().toLowerCase();

            int required = requiredTicks(blockName, player);
            if (elapsed < required) {
                double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                if (buffer.increase(uuid, 1.5) > 15.0) {
                    pf.punish("FastBreak", "A", String.format("FastBreak elapsed=%d req=%d block=%s", elapsed, required, blockName), 1.0f);
                    buffer.reset(uuid, 5.0);
                }
            } else {
                buffer.decrease(uuid, 0.5);
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

    private int requiredTicks(String name, Player player) {
        if (name.contains("grass") || name.contains("flower") || name.contains("torch") || name.contains("sapling")) return 0;

        int req = 2;
        if (name.contains("stone") || name.contains("deepslate") || name.contains("ore") || name.contains("brick")) req = 3;
        if (name.contains("obsidian") || name.contains("ancient_debris")) req = 20;

        if (player.hasPotionEffect(PotionEffectType.HASTE)) req = Math.max(0, req - 2);

        int eff = getEnchantLevel(player, "efficiency");
        if (eff >= 5) req = Math.max(0, req - 3);
        else if (eff >= 3) req = Math.max(0, req - 1);

        return req;
    }

    private int getEnchantLevel(Player player, String enchantName) {
        return player.getInventory().getItemInMainHand().getEnchantments().entrySet().stream()
                .filter(e -> e.getKey().getName().equalsIgnoreCase(enchantName))
                .mapToInt(e -> e.getValue())
                .findFirst().orElse(0);
    }

    private static final class DigInfo {
        final int startTick;
        final Vector3i pos;
        DigInfo(int startTick, Vector3i pos) { this.startTick = startTick; this.pos = pos; }
    }
}