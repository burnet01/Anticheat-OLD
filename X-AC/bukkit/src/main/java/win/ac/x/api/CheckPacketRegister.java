package win.ac.x.api;

import lombok.Getter;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class CheckPacketRegister {

    @Getter
    private static final Set<PacketCheckHandler> listeners = new CopyOnWriteArraySet<>();

    public static void addListener(PacketCheckHandler packetListener) {
        listeners.add(packetListener);
    }

    public static void removeListener(PacketCheckHandler packetListener) {
        listeners.remove(packetListener);
    }

    public static void run(Object event) {
        runCustom(event, listeners);
    }

    public static void runCustom(Object event, Set<PacketCheckHandler> stack) {
        for (PacketCheckHandler packetListener : stack) {
            packetListener.event(event);
        }
    }
}
