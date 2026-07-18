package kireiko.dev.anticheat.checks.v2.packet.order;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;
import kireiko.dev.anticheat.managers.CheckManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketOrderCV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, CrystalState> stateMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 15);
        return new ConfigLabel("v2_packetorder_c", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public PacketOrderCV2(PlayerProfile profile) {
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

            CrystalState state = stateMap.computeIfAbsent(uuid, k -> new CrystalState());

            if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                if (isHoldingCrystal(player)) {
                    state.placeTick = pf.airTicks;
                    state.placeCount++;
                }
            } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                if (state.placeTick == pf.airTicks) {
                    state.sameTicks++;

                    if (state.sameTicks >= 5) {
                        double buf = ((Number) localCfg.getOrDefault("buffer", 15)).doubleValue();
                        if (buffer.increase(uuid, 1.5) > 10.0) {
                            pf.punish("PacketOrder", "C", String.format("Crystal automation (sameTickOps=%d, total=%d)",
                                    state.sameTicks, state.placeCount), 1.0f);
                            buffer.reset(uuid, 5.0);
                            state.sameTicks = 0;
                        }
                    }
                } else {
                    if (state.sameTicks > 0) state.sameTicks--;
                    buffer.decrease(uuid, 0.2);
                }
            }
        }
    }

    private boolean isHoldingCrystal(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() == Material.END_CRYSTAL) return true;
        hand = player.getInventory().getItemInOffHand();
        return hand != null && hand.getType() == Material.END_CRYSTAL;
    }

    private static class CrystalState {
        int placeTick = -1;
        int sameTicks = 0;
        int placeCount = 0;
    }
}