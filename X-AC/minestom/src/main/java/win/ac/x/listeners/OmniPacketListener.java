package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;

import java.util.function.Consumer;

public final class OmniPacketListener implements Consumer<PlayerPacketEvent> {

    @Override
    public void accept(PlayerPacketEvent pEvent) {

        Player player = pEvent.getPlayer();
        if (player == null) return;
        PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;
        protocol.run(new CPacketEvent(pEvent));
    }
}