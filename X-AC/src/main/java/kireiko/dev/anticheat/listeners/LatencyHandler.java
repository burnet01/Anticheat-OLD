package kireiko.dev.anticheat.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import kireiko.dev.anticheat.MX;
import kireiko.dev.anticheat.api.data.PlayerContainer;
import kireiko.dev.anticheat.api.events.CTransactionEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LatencyHandler implements PacketListener {

    public static void startChecking(PlayerProfile protocol) {
        protocol.transactionId = -1939;
        protocol.transactionBoot = false;
        sendTransaction(protocol, protocol.transactionId);
    }

    public static void sendTransaction(PlayerProfile protocol, short id) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(protocol.getPlayer(),
                new WrapperPlayServerPing(id));
        protocol.transactionId--;
        if (protocol.transactionId < -1945)
            protocol.transactionId = -1939;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            final PlayerProfile protocol = PlayerContainer.getProfile(player);
            if (protocol == null) return;

            int id = new WrapperPlayClientPong(event).getId();
            if (id <= -1939 && id >= -1945) {
                protocol.transactionPing = System.currentTimeMillis() - protocol.transactionTime;
                protocol.getPing().add(protocol.transactionPing);
                protocol.transactionLastTime = System.currentTimeMillis();
                protocol.transactionSentKeep = false;
                CTransactionEvent transactionEvent = new CTransactionEvent(protocol);
                protocol.run(transactionEvent);
                Bukkit.getScheduler().runTaskLaterAsynchronously(MX.getInstance(),
                        () -> sendTransaction(protocol, protocol.transactionId), 10L);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PING) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            final PlayerProfile protocol = PlayerContainer.getProfile(player);
            if (protocol == null) return;
            protocol.transactionSentKeep = true;
            protocol.transactionTime = System.currentTimeMillis();
        }
    }
}