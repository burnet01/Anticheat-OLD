package win.ac.x.utils.protocol;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.util.UUID;

public final class ProtocolLib {

    public static boolean isTemporary(Player player) {
        return false;
    }

    public static UUID getUUID(Entity entity) {
        return entity.getUuid();
    }

    public static int getEntityID(Entity entity) {
        return entity.getEntityId();
    }

    public static Pos getPositionOrNull(Entity entity) {
        return entity.getPosition();
    }

    public static Pos getPositionOrNull(Player player) {
        return player.getPosition();
    }

    public static Entity getVehicle(Entity entity) {
        return entity.getVehicle();
    }

    public static Instance getInstance(Entity entity) {
        return entity.getInstance();
    }
}