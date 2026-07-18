package kireiko.dev.anticheat.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import kireiko.dev.anticheat.MX;
import kireiko.dev.anticheat.api.data.PlayerContainer;
import kireiko.dev.anticheat.api.events.UseEntityEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.utils.ConfigCache;
import kireiko.dev.anticheat.utils.cache.EntityCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class UseEntityListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.INTERACT_ENTITY || type == PacketType.Play.Client.ATTACK) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            PlayerProfile profile = PlayerContainer.getProfile(player);
            if (profile == null) return;

            boolean attack;
            int entityId;

            if (type == PacketType.Play.Client.ATTACK) {
                WrapperPlayClientAttack wrapper = new WrapperPlayClientAttack(event);
                entityId = wrapper.getEntityId();
                attack = true;
            } else {
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                entityId = wrapper.getEntityId();
                attack = wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
            }

            Entity entity = EntityCache.get(entityId);
            if (profile.getAttackBlockToTime() > System.currentTimeMillis()) {
                if (ConfigCache.PREVENTION > 0) {
                    event.setCancelled(true);
                    if (ConfigCache.PREVENTION >= 3) {
                        Bukkit.getScheduler().runTask(MX.getInstance(), () -> {
                            player.teleport(player.getLocation());
                        });
                    } else if (ConfigCache.PREVENTION == 1
                                    && attack
                                    && entity instanceof LivingEntity
                                    && player.getLocation().toVector().distance(entity.getLocation().toVector()) < 3.3) {
                        final LivingEntity target = (LivingEntity) entity;
                        Bukkit.getScheduler().runTask(MX.getInstance(), () -> {
                            target.damage(0.5, player);
                        });
                    }
                    profile.debug("UseEntity packet blocked");
                    MX.blockedPerMinuteCount++;
                }
            }
            UseEntityEvent e = new UseEntityEvent(entity, attack, entityId, false);
            profile.run(e);
            if (e.isCancelled()) {
                event.setCancelled(true);
                profile.debug("UseEntity packet blocked after checking");
                MX.blockedPerMinuteCount++;
            }
        }
    }
}