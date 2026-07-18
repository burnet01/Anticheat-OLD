package win.ac.x.utils.protocol;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionStatusPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;

public final class ProtocolTools {

    public static Pos readPosition(Object packet, Player player) {
        if (packet instanceof ClientPlayerPositionPacket) {
            ClientPlayerPositionPacket p = (ClientPlayerPositionPacket) packet;
            var pos = p.position();
            return new Pos(pos.x(), pos.y(), pos.z());
        } else if (packet instanceof ClientPlayerPositionAndRotationPacket) {
            ClientPlayerPositionAndRotationPacket p = (ClientPlayerPositionAndRotationPacket) packet;
            var pos = p.position();
            return new Pos(pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch());
        }
        return null;
    }

    public static boolean isFlying(Object packet) {
        return packet instanceof ClientPlayerPositionStatusPacket
                || packet instanceof ClientPlayerRotationPacket;
    }

    public static boolean onGroundPacketLevel(Object packet) {
        if (packet instanceof ClientPlayerPositionStatusPacket) return ((ClientPlayerPositionStatusPacket) packet).onGround();
        if (packet instanceof ClientPlayerPositionPacket) return ((ClientPlayerPositionPacket) packet).onGround();
        if (packet instanceof ClientPlayerPositionAndRotationPacket) return ((ClientPlayerPositionAndRotationPacket) packet).onGround();
        if (packet instanceof ClientPlayerRotationPacket) return ((ClientPlayerRotationPacket) packet).onGround();
        return false;
    }

    public static boolean hasPosition(Object type) {
        return type instanceof ClientPlayerPositionPacket
                || type instanceof ClientPlayerPositionAndRotationPacket;
    }

    public static boolean hasRotation(Object type) {
        return type instanceof ClientPlayerRotationPacket
                || type instanceof ClientPlayerPositionAndRotationPacket;
    }

    public static Block getBlockAsync(final Instance instance, final Pos pos) {
        int cx = (int) Math.floor(pos.x()) >> 4;
        int cz = (int) Math.floor(pos.z()) >> 4;
        if (instance.isChunkLoaded(cx, cz)) {
            return instance.getBlock((int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
        }
        return null;
    }

    public enum tpFlags {
        X, Y, Z, Y_ROT, X_ROT
    }
}