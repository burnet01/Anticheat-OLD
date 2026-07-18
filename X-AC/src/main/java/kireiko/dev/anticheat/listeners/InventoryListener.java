package kireiko.dev.anticheat.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import kireiko.dev.anticheat.api.data.PlayerContainer;
import kireiko.dev.anticheat.api.events.WindowClickEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class InventoryListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            PlayerProfile protocol = PlayerContainer.getProfile(player);
            if (protocol == null) return;
            protocol.run(new WindowClickEvent(event));
        }
    }
}