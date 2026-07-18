package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.SVelocityEvent;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;

import java.util.function.Consumer;

public final class VelocityListener implements Consumer<PlayerPacketOutEvent> {

    @Override
    public void accept(PlayerPacketOutEvent pEvent) {
        if (!(pEvent.getPacket() instanceof EntityVelocityPacket)) return;

        Player player = pEvent.getPlayer();
        if (player == null) return;
        final PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;

        EntityVelocityPacket packet = (EntityVelocityPacket) pEvent.getPacket();
        int entityId = packet.entityId();
        if (protocol.getEntityId() == entityId) {
            double x = packet.velocity().x() / 8000.0;
            double y = packet.velocity().y() / 8000.0;
            double z = packet.velocity().z() / 8000.0;
            protocol.run(new SVelocityEvent(new Vec(x, y, z)));
        }
    }
}