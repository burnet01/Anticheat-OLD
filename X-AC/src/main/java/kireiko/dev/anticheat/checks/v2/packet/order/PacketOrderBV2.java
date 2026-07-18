package kireiko.dev.anticheat.checks.v2.packet.order;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
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

public final class PacketOrderBV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Map<UUID, ItemUseState> stateMap = new ConcurrentHashMap<>();
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_packetorder_b", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public PacketOrderBV2(PlayerProfile profile) {
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

            PacketTypeCommon type = event.getPacketType();
            ItemUseState state = stateMap.computeIfAbsent(uuid, k -> new ItemUseState());

            if (type == PacketType.Play.Client.USE_ITEM) {
                if (isSlowItem(player)) {
                    state.usingItem = true;
                    state.useStartTick = pf.airTicks;
                }
            } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
                DiggingAction action = wrapper.getAction();

                if (action == DiggingAction.RELEASE_USE_ITEM
                        || action == DiggingAction.DROP_ITEM
                        || action == DiggingAction.DROP_ITEM_STACK) {
                    state.usingItem = false;
                }

                state.lastDiggingTick = pf.airTicks;
            } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {

                    boolean confirmed = state.usingItem
                            && isUsingItem(player)
                            && isSlowItem(player);

                    int ticksSinceDigging = pf.airTicks - state.lastDiggingTick;
                    boolean inDiggingGrace = ticksSinceDigging >= 0 && ticksSinceDigging <= 3;

                    int useAge = pf.airTicks - state.useStartTick;
                    boolean confirmedDuration = useAge >= 3;

                    if (confirmed && confirmedDuration && !inDiggingGrace) {
                        double buf = ((Number) localCfg.getOrDefault("buffer", 10)).doubleValue();
                        if (buffer.increase(uuid, 1.0) > 8.0) {
                            pf.punish("PacketOrder", "B", String.format("Attack while using item (useTicks=%d)", useAge), 1.0f);
                            buffer.reset(uuid, 4.0);
                        }
                    } else {
                        buffer.decrease(uuid, 0.2);
                    }
                }
            } else if (isFlying(type)) {
                if (!isUsingItem(player)) {
                    state.usingItem = false;
                }
            }
        }
    }

    private boolean isSlowItem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR)
            hand = player.getInventory().getItemInOffHand();
        if (hand == null || hand.getType() == Material.AIR) return false;
        String t = hand.getType().name().toLowerCase();
        return t.contains("sword") || t.contains("bow") || t.contains("food")
                || t.contains("potion") || t.contains("shield") || t.contains("trident")
                || t.contains("crossbow");
    }

    private boolean isUsingItem(Player player) {
        return player.isBlocking() || player.isHandRaised();
    }

    private boolean isFlying(PacketTypeCommon type) {
        return type == PacketType.Play.Client.PLAYER_FLYING
                || type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_ROTATION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    }

    private static class ItemUseState {
        boolean usingItem = false;
        int useStartTick = -100;
        int lastDiggingTick = -100;
    }
}