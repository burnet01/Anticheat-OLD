package win.ac.x.api.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.minestom.server.entity.Entity;

@Data
@AllArgsConstructor
public final class UseEntityEvent {
    private Entity target;
    private boolean attack;
    private int entityId;
    private boolean cancelled;
}