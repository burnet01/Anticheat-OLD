package kireiko.dev.anticheat.checks.v2.combat.anchor;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class AnchorAuraAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastInteractTime = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_anchor_aura_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public AnchorAuraAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent e = (CPacketEvent) o;
        if (e.getPacketEvent().getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        Player player = profile.getPlayer();

        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        boolean holdingGlowstone = (hand != null && hand.getType() == Material.GLOWSTONE) ||
                (offhand != null && offhand.getType() == Material.GLOWSTONE);

        long now = System.currentTimeMillis();
        long diff = now - lastInteractTime;

        if (holdingGlowstone) {
            lastInteractTime = now;
        } else {
            if (diff > 0 && diff < 80) {
                if (buffer.increase(uuid, 1.5) > 6.0) {
                    profile.punish("AnchorAura", "A", String.format("Fast Anchor (Charge->Explode). Delay: %dms", diff), 1.0f);
                    buffer.reset(uuid, 3.0);
                }
            } else {
                buffer.decrease(uuid, 0.1);
            }
        }
    }
}