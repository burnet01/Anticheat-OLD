package win.ac.x.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.EntityActionEvent;
import win.ac.x.api.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class EntityActionListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            PlayerProfile protocol = PlayerContainer.getProfile(player);
            if (protocol == null) return;

            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
            String typeString = wrapper.getAction().toString();
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