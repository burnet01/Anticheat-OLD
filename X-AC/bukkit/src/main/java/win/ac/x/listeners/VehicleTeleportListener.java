package win.ac.x.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class VehicleTeleportListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            PlayerProfile protocol = PlayerContainer.getProfile(player);
            if (protocol == null) return;
            protocol.setLastTeleport(System.currentTimeMillis());
            protocol.setIgnoreFirstTick(true);
        }
    }
}