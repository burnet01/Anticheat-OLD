package kireiko.dev.anticheat.utils.protocol;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public final class ProtocolTools {

    public static Location readLocation(WrapperPlayClientPlayerFlying flying, Player player) {
        if (flying.hasPositionChanged()) {
            return new Location(
                    player.getWorld(),
                    flying.getLocation().getX(),
                    flying.getLocation().getY(),
                    flying.getLocation().getZ()
            );
        } else {
            return null;
        }
    }

    public static boolean isFlying(PacketTypeCommon p, Location to, Location from) {
        if (p == PacketType.Play.Client.PLAYER_POSITION && to.toVector().equals(from.toVector()))
            return true;
        else return
                (
                        p == PacketType.Play.Client.PLAYER_FLYING
                                ||
                                p == PacketType.Play.Client.PLAYER_ROTATION
                );
    }

    public static boolean onGroundPacketLevel(WrapperPlayClientPlayerFlying flying) {
        return flying.isOnGround();
    }

    public static Set<tpFlags> getTeleportFlags(WrapperPlayClientPlayerFlying flying) {
        Set<tpFlags> flags = new HashSet<>(3);
        if (flying.hasPositionChanged()) {
            flags.add(tpFlags.X);
            flags.add(tpFlags.Y);
            flags.add(tpFlags.Z);
        }
        if (flying.hasRotationChanged()) {
            flags.add(tpFlags.X_ROT);
            flags.add(tpFlags.Y_ROT);
        }
        return flags;
    }

    public static boolean invalidTeleport(Location location) {
        return location == null
                || location.getX() == 8.5D
                || location.getZ() == 8.5D;
    }

    public static boolean isLoadLocation(Location location) {
        return (location.getX() == 1 && location.getY() == 1 && location.getZ() == 1);
    }

    public static Location getLoadLocation(Player player) {
        return new Location(player.getWorld(), 1, 1, 1);
    }

    public static boolean hasPosition(PacketTypeCommon type) {
        return (type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION);
    }

    public static boolean hasRotation(PacketTypeCommon type) {
        return (type == PacketType.Play.Client.PLAYER_ROTATION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION);
    }

    public static Block getBlockAsync(final Location location) {
        if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return location.getWorld().getBlockAt(location);
        } else {
            return null;
        }
    }

    public enum tpFlags {
        X, Y, Z, Y_ROT, X_ROT
    }
}