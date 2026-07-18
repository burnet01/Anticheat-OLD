package win.ac.x.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class OmniPacketListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        java.util.UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;
        protocol.run(new CPacketEvent(event));
    }
}