package win.ac.x.listeners;

import win.ac.x.XMinestom;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.UseEntityEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.utils.cache.EntityCache;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

import java.util.function.Consumer;

public final class UseEntityListener implements Consumer<PlayerPacketEvent> {

    @Override
    public void accept(PlayerPacketEvent pEvent) {
        if (!(pEvent.getPacket() instanceof ClientInteractEntityPacket)) return;

        Player player = pEvent.getPlayer();
        if (player == null) return;
        PlayerProfile profile = PlayerContainer.getProfile(player);
        if (profile == null) return;

        ClientInteractEntityPacket packet = (ClientInteractEntityPacket) pEvent.getPacket();
        int entityId = packet.targetId();
        boolean attack = !packet.usingSecondaryAction();

        Entity entity = EntityCache.get(entityId);

        if (profile.getAttackBlockToTime() > System.currentTimeMillis()) {
            pEvent.setCancelled(true);
            profile.debug("UseEntity packet blocked");
            XMinestom.blockedPerMinuteCount++;
        }
        UseEntityEvent e = new UseEntityEvent(entity, attack, entityId, false);
        profile.run(e);
        if (e.isCancelled()) {
            pEvent.setCancelled(true);
            profile.debug("UseEntity packet blocked after checking");
            XMinestom.blockedPerMinuteCount++;
        }
    }
}