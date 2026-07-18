package win.ac.x.utils.protocol;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ProtocolLib {

    public static boolean isTemporary(OfflinePlayer player) {
        return false;
    }

    public static UUID getUUID(Entity entity) {
        return entity.getUniqueId();
    }

    public static int getEntityID(Entity entity) {
        return entity.getEntityId();
    }

    public static Location getLocationOrNull(Entity entity) {
        return entity.getLocation();
    }

    public static Location getLocationOrNull(Player player) {
        return player.getLocation();
    }

    public static Entity getVehicle(Entity entity) {
        return entity.getVehicle();
    }

    public static World getWorld(Entity entity) {
        return entity.getWorld();
    }

}