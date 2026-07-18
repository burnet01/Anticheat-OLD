package kireiko.dev.anticheat.checks.v2.combat.crystal;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class CrystalAuraAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastPlaceTime = 0;
    private final Map<Integer, Long> crystalSpawnTimes = new HashMap<>();
    private long lastCleanup = System.currentTimeMillis();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_crystal_aura_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public CrystalAuraAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent e = (CPacketEvent) o;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();

        if (System.currentTimeMillis() - lastCleanup > 60000) {
            crystalSpawnTimes.clear();
            lastCleanup = System.currentTimeMillis();
        }

        if (e.getPacketEvent().getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(e.getPacketEvent());
            Player player = profile.getPlayer();
            ItemStack stack = player.getInventory().getItemInMainHand();
            if (stack == null || stack.getType() != Material.END_CRYSTAL) {
                stack = player.getInventory().getItemInOffHand();
            }

            if (stack != null && stack.getType() == Material.END_CRYSTAL) {
                lastPlaceTime = System.currentTimeMillis();
            }
        } else if (e.getPacketEvent().getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(e.getPacketEvent());

            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                long now = System.currentTimeMillis();
                long diff = now - lastPlaceTime;

                if (diff < 100) {
                    if (diff < 40) {
                        if (buffer.increase(uuid, 2.0) > 6.0) {
                            profile.punish("CrystalAura", "A", String.format("Fast Crystal (Place->Break). Delay: %dms", diff), 1.0f);
                            buffer.reset(uuid, 3.0);
                        }
                    } else {
                        buffer.decrease(uuid, 0.25);
                    }
                } else {
                    buffer.decrease(uuid, 0.1);
                }

                Player player = profile.getPlayer();
                Entity target = kireiko.dev.anticheat.utils.cache.EntityCache.get(interact.getEntityId());

                if (target == null) return;

                if (target.getType() == EntityType.END_CRYSTAL && target.getTicksLived() <= 0) {
                    if (buffer.increase(uuid, 1.5) > 5.0) {
                        profile.punish("CrystalAura", "A", "Crystal ID Predict (0-Tick Attack)", 1.0f);
                    }
                }
            }
        }
    }
}