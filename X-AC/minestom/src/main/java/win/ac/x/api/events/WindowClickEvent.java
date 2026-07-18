package win.ac.x.api.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.minestom.server.event.player.PlayerPacketEvent;

@Data
@AllArgsConstructor
public final class WindowClickEvent {
    private PlayerPacketEvent packetEvent;
}