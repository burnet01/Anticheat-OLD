package win.ac.x.api.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.event.player.PlayerPacketEvent;

@Getter
@AllArgsConstructor
public final class CPacketEvent {
    private PlayerPacketEvent packetEvent;
}