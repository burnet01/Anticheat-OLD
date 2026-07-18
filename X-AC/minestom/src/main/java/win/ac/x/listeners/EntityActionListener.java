package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.EntityActionEvent;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;

import java.util.function.Consumer;

public final class EntityActionListener implements Consumer<PlayerPacketEvent> {

    @Override
    public void accept(PlayerPacketEvent pEvent) {
        if (!(pEvent.getPacket() instanceof ClientEntityActionPacket)) return;

        Player player = pEvent.getPlayer();
        if (player == null) return;
        PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;

        ClientEntityActionPacket packet = (ClientEntityActionPacket) pEvent.getPacket();
        String typeString = packet.action().toString();
        AbilitiesEnum type = getEnum(typeString);

        if (typeString != null) {
            if (type == AbilitiesEnum.PRESS_SHIFT_KEY) {
                protocol.sneaking = true;
            } else if (type == AbilitiesEnum.RELEASE_SHIFT_KEY) {
                protocol.sneaking = false;
            } else if (type == AbilitiesEnum.START_SPRINTING) {
                protocol.sprinting = true;
            } else if (type == AbilitiesEnum.STOP_SPRINTING) {
                protocol.sprinting = false;
            }
        }
        EntityActionEvent e = new EntityActionEvent(type);
        protocol.run(e);
    }

    private AbilitiesEnum getEnum(String s) {
        for (AbilitiesEnum type : AbilitiesEnum.values()) {
            if (type.toString().equals(s)) {
                return type;
            }
        }
        return null;
    }

    public enum AbilitiesEnum {
        START_SPRINTING,
        STOP_SPRINTING,
        PRESS_SHIFT_KEY,
        RELEASE_SHIFT_KEY
    }
}