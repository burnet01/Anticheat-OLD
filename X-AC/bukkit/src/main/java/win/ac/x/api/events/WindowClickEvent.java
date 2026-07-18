package win.ac.x.api.events;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class WindowClickEvent {
    private PacketReceiveEvent packetEvent;
}