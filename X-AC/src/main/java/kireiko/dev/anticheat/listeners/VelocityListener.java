package kireiko.dev.anticheat.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import kireiko.dev.anticheat.api.data.PlayerContainer;
import kireiko.dev.anticheat.api.events.SVelocityEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class VelocityListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            final PlayerProfile protocol = PlayerContainer.getProfile(player);
            if (protocol == null) return;

            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            int entityId = wrapper.getEntityId();
            if (protocol.getEntityId() == entityId) {
                double x = wrapper.getVelocity().getX();
                double y = wrapper.getVelocity().getY();
                double z = wrapper.getVelocity().getZ();
                protocol.run(new SVelocityEvent(new Vector(x, y, z)));
            }
        }
    }
}