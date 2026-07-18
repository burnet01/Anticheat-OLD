package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.WindowClickEvent;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;

import java.util.function.Consumer;

public final class InventoryListener implements Consumer<PlayerPacketEvent> {

    @Override
    public void accept(PlayerPacketEvent pEvent) {
        if (!(pEvent.getPacket() instanceof ClientClickWindowPacket)) return;

        Player player = pEvent.getPlayer();
        if (player == null) return;
        PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;
        protocol.run(new WindowClickEvent(pEvent));
    }
}