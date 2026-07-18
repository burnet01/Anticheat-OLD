package win.ac.x.api.events;

import lombok.Getter;
import net.minestom.server.coordinate.Vec;

@Getter
public final class SVelocityEvent {
    private final Vec velocity;

    public SVelocityEvent(Vec velocity) {
        this.velocity = velocity;
    }
}