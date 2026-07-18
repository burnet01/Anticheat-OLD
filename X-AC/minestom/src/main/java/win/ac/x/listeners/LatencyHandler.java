package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.events.CTransactionEvent;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.timer.TaskSchedule;

public final class LatencyHandler {

    public static void startChecking(PlayerProfile protocol) {
        protocol.transactionId = -1939;
        protocol.transactionBoot = false;
        sendTransaction(protocol, protocol.transactionId);
    }

    public static void sendTransaction(PlayerProfile protocol, short id) {
        protocol.getPlayer().sendPacket(new PingPacket(id));
        protocol.transactionId--;
        if (protocol.transactionId < -1945)
            protocol.transactionId = -1939;
    }

    public static void onPacketReceive(PlayerPacketEvent event) {
        if (!(event.getPacket() instanceof ClientPongPacket)) return;

        Player player = event.getPlayer();
        if (player == null) return;
        final PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;

        int id = ((ClientPongPacket) event.getPacket()).id();
        if (id <= -1939 && id >= -1945) {
            protocol.transactionPing = System.currentTimeMillis() - protocol.transactionTime;
            protocol.getPing().add(protocol.transactionPing);
            protocol.transactionLastTime = System.currentTimeMillis();
            protocol.transactionSentKeep = false;
            CTransactionEvent transactionEvent = new CTransactionEvent(protocol);
            protocol.run(transactionEvent);
            MinecraftServer.getSchedulerManager().submitTask(() -> {
                    sendTransaction(protocol, protocol.transactionId);
                    return TaskSchedule.stop();
            });
        }
    }

    public static void onPacketSend(PlayerPacketOutEvent event) {
        if (!(event.getPacket() instanceof PingPacket)) return;

        Player player = event.getPlayer();
        if (player == null) return;
        final PlayerProfile protocol = PlayerContainer.getProfile(player);
        if (protocol == null) return;
        protocol.transactionSentKeep = true;
        protocol.transactionTime = System.currentTimeMillis();
    }
}