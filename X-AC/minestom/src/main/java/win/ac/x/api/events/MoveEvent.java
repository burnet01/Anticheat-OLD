package win.ac.x.api.events;

import win.ac.x.api.player.PlayerProfile;
import win.ac.x.vectors.Vec3;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minestom.server.coordinate.Pos;

@Data
@AllArgsConstructor
public final class MoveEvent {
    private PlayerProfile profile;
    private Pos from;
    private Pos to;

    public Vec3 getDelta() {
        return new Vec3(
                to.x() - from.x(),
                to.y() - from.y(),
                to.z() - from.z()
        );
    }
}