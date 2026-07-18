package win.ac.x.listeners;

import net.minestom.server.event.player.PlayerPacketEvent;

public final class TestListener implements java.util.function.Consumer<PlayerPacketEvent> {
    @Override
    public void accept(PlayerPacketEvent pEvent) {
        var player = pEvent.getPlayer();
        if (player != null) {
            player.sendMessage("e: " + pEvent.getPacket().getClass().getSimpleName());
        }
    }
}