package kireiko.dev.anticheat.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TestListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        java.util.UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage("e: " + event.getPacketType().getName()
                    + " " + event.getPacketType().getId(
                            com.github.retrooper.packetevents.PacketEvents.getAPI()
                                    .getServerManager().getVersion().toClientVersion()));
        }
    }
}