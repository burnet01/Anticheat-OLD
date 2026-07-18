package win.ac.x.listeners;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.data.RotationsContainer;
import win.ac.x.api.events.MoveEvent;
import win.ac.x.api.events.NoRotationEvent;
import win.ac.x.api.events.RotationEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.player.SensitivityProcessor;
import win.ac.x.services.DatasetRecorder;
import win.ac.x.services.PhysicsSimulationService;
import win.ac.x.utils.ConfigCache;
import win.ac.x.phys.PhysicsEngine;
import win.ac.x.vectors.Vec2f;
import win.ac.x.vectors.Vec3;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionStatusPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;

import java.util.UUID;

public final class RawMovementListener {

    public static void onPacketSend(PlayerPacketOutEvent event) {
        if (event.getPacket() instanceof PlayerPositionAndLookPacket) {
            Player player = event.getPlayer();
            if (player == null) return;
            final PlayerProfile profile = PlayerContainer.getProfile(player);
            if (profile == null) return;
            profile.setLastTeleport(System.currentTimeMillis());
            profile.setIgnoreFirstTick(true);
        }
    }

    public static void onPacketReceive(PlayerPacketEvent event) {
        var packet = event.getPacket();
        boolean isFlying = packet instanceof ClientPlayerPositionStatusPacket
                || packet instanceof ClientPlayerPositionPacket
                || packet instanceof ClientPlayerPositionAndRotationPacket
                || packet instanceof ClientPlayerRotationPacket;

        if (!isFlying) return;

        Player player = event.getPlayer();
        if (player == null) return;
        final PlayerProfile profile = PlayerContainer.getProfile(player);
        if (profile == null) return;

        boolean hasPosition = packet instanceof ClientPlayerPositionPacket
                || packet instanceof ClientPlayerPositionAndRotationPacket;
        boolean hasRotation = packet instanceof ClientPlayerRotationPacket
                || packet instanceof ClientPlayerPositionAndRotationPacket;

        boolean onGround = false;
        if (packet instanceof ClientPlayerPositionStatusPacket) {
            onGround = ((ClientPlayerPositionStatusPacket) packet).onGround();
        } else if (packet instanceof ClientPlayerPositionPacket) {
            onGround = ((ClientPlayerPositionPacket) packet).onGround();
        } else if (packet instanceof ClientPlayerPositionAndRotationPacket) {
            onGround = ((ClientPlayerPositionAndRotationPacket) packet).onGround();
        } else if (packet instanceof ClientPlayerRotationPacket) {
            onGround = ((ClientPlayerRotationPacket) packet).onGround();
        }

        profile.setGround(onGround);
        profile.setAirTicks((profile.isGround()) ? 0 : profile.getAirTicks() + 1);

        Pos oldTo = profile.getTo();
        profile.setFrom(oldTo);
        Pos currentPos = player.getPosition();
        Pos l;

        if (hasPosition) {
            double x, y, z;
            if (packet instanceof ClientPlayerPositionPacket) {
                ClientPlayerPositionPacket p = (ClientPlayerPositionPacket) packet;
                var pos = p.position(); x = pos.x(); y = pos.y(); z = pos.z();
            } else if (packet instanceof ClientPlayerPositionAndRotationPacket) {
                ClientPlayerPositionAndRotationPacket p = (ClientPlayerPositionAndRotationPacket) packet;
                var pos = p.position(); x = pos.x(); y = pos.y(); z = pos.z();
            } else {
                x = currentPos.x(); y = currentPos.y(); z = currentPos.z();
            }
            double[] v = new double[]{x, y, z};
            for (Double check : v)
                if (check.isNaN() || check.isInfinite() || Math.abs(check) > 3E8) {
                    return;
                }
            if (hasRotation) {
                float yaw = 0, pitch = 0;
                if (packet instanceof ClientPlayerPositionAndRotationPacket) {
                    ClientPlayerPositionAndRotationPacket p = (ClientPlayerPositionAndRotationPacket) packet;
                    yaw = p.position().yaw();
                    pitch = p.position().pitch();
                } else {
                    yaw = currentPos.yaw();
                    pitch = currentPos.pitch();
                }
                for (Float check : new Float[]{yaw, pitch})
                    if (check.isNaN() || check.isInfinite() || Math.abs(check) > 3E8) {
                        return;
                    }
                l = new Pos(x, y, z, yaw, pitch);
            } else {
                l = new Pos(x, y, z, currentPos.yaw(), currentPos.pitch());
            }
        } else {
            l = new Pos(currentPos.x(), currentPos.y(), currentPos.z(), currentPos.yaw(), currentPos.pitch());
        }
        profile.setTo(l);

        if (hasRotation) {
            SensitivityProcessor controller = profile.getSensitivityProcessor();
            controller.setLastDeltaPitch(controller.getLastDeltaPitch());
            Vec2f from = new Vec2f((float) profile.getFrom().yaw(), (float) profile.getFrom().pitch());
            Vec2f to = new Vec2f((float) profile.getTo().yaw(), (float) profile.getTo().pitch());
            RotationEvent rotationEvent = new RotationEvent(profile, to, from);
            controller.setDeltaPitch(rotationEvent.getDelta().getY());
            controller.processSensitivity();
            boolean isTeleporting = (System.currentTimeMillis() - profile.getLastTeleport() < 500) || profile.isIgnoreFirstTick();

            if (ConfigCache.ROTATIONS_CONTAINER
                            && !profile.isIgnoreFirstTick()
                            && !isTeleporting) {
                RotationsContainer.register(player.getUuid(), rotationEvent.getDelta());
            }

            profile.getCinematicComponent().process(rotationEvent);
            if (!isTeleporting) {
                profile.run(rotationEvent);
            }
            profile.setIgnoreFirstTick(false);
        } else {
            if (!profile.isIgnoreFirstTick() && profile.getLastTeleport() + 1000 < System.currentTimeMillis()) {
                Vec delta = profile.getTo().asVec().sub(profile.getFrom().asVec());
                if (delta.length() > 1e-4) {
                    profile.run(new NoRotationEvent(profile));
                }
            }
        }

        profile.getPastLoc().add(profile.getTo());
        profile.run(new MoveEvent(profile, profile.getFrom(), profile.getTo()));

        feedDatasetRecorder(profile);

        if (profile.transactionBoot) LatencyHandler.startChecking(profile);
    }

    private static void feedDatasetRecorder(PlayerProfile profile) {
        Player player = profile.getPlayer();
        UUID uuid = player.getUuid();
        if (!DatasetRecorder.isRecording(uuid)) return;
        if (profile.isIgnoreFirstTick()) return;

        long now = System.currentTimeMillis();
        if (now - profile.getLastTeleport() < 500) return;

        Pos from = profile.getFrom();
        Pos to = profile.getTo();
        Vec3 deltaVec = new Vec3(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        double hSpeed = PhysicsEngine.computeHorizontalSpeed(deltaVec);
        double vSpeed = Math.abs(deltaVec.yCoord);

        DatasetRecorder.feedHorizontalSpeed(uuid, hSpeed);
        DatasetRecorder.feedVerticalSpeed(uuid, vSpeed);

        Instance instance = profile.getPlayer().getInstance();
        String blockName = instance != null
            ? instance.getBlock(new BlockVec(to.x(), to.y(), to.z())).name()
            : "";
        boolean inWeb = PhysicsEngine.isInWeb(blockName);
        boolean inWater = PhysicsEngine.isInWater(blockName);
        boolean onClimbable = PhysicsEngine.isOnClimbable(blockName);
        double expectedMaxH = PhysicsEngine.getExpectedMaxHorizontalSpeed(
            profile.isSprinting(), profile.isSneaking(),
            profile.isGround(), inWeb, profile.getAirTicks(),
            inWater, onClimbable
        );
        DatasetRecorder.feedExpectedMaxH(uuid, expectedMaxH);

        double yawDelta = to.yaw() - from.yaw();
        double pitchDelta = to.pitch() - from.pitch();
        DatasetRecorder.feedYawDelta(uuid, yawDelta);
        DatasetRecorder.feedPitchDelta(uuid, pitchDelta);

        PhysicsSimulationService.feedMoveForRecording(profile, to);
    }
}