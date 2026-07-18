package win.ac.x.api;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public final class CheckPacketRegister {

    @Getter
    private static final Set<PacketCheckHandler> listeners = new HashSet<>();

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
            try {
                packetListener.event(event);
            } catch (Exception ignored) {}
        }
    }
}
