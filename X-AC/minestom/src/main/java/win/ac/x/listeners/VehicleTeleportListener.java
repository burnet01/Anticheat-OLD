package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientVehicleMovePacket;

import java.util.function.Consumer;

public final class VehicleTeleportListener implements Consumer<PlayerPacketEvent> {

    @Override
    public void accept(PlayerPacketEvent pEvent) {
        if (!(pEvent.getPacket() instanceof ClientVehicleMovePacket)) return;

        Player player = pEvent.getPlayer();
        if (player == null) return;
        PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;
        protocol.setLastTeleport(System.currentTimeMillis());
        protocol.setIgnoreFirstTick(true);
    }
}