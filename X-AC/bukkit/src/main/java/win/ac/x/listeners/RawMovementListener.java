package win.ac.x.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

public final class RawMovementListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            final PlayerProfile profile = PlayerContainer.getProfile(player);
            if (profile == null) return;
            profile.setLastTeleport(System.currentTimeMillis());
            profile.setIgnoreFirstTick(true);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                || type == PacketType.Play.Client.PLAYER_ROTATION
                || type == PacketType.Play.Client.PLAYER_FLYING) {

            java.util.UUID uuid = event.getUser().getUUID();
            if (uuid == null) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            final PlayerProfile profile = PlayerContainer.getProfile(player);
            if (profile == null) return;

            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            profile.setGround(flying.isOnGround());
            profile.setAirTicks((profile.isGround()) ? 0 : profile.getAirTicks() + 1);

            profile.setFrom(profile.getTo().clone());
            Location l = profile.getTo().clone();

            boolean hasPosition = flying.hasPositionChanged();
            boolean hasRotation = flying.hasRotationChanged();

            if (hasPosition) {
                double x = flying.getLocation().getX();
                double y = flying.getLocation().getY();
                double z = flying.getLocation().getZ();
                double[] v = new double[]{x, y, z};
                for (Double check : v)
                    if (check.isNaN() || check.isInfinite() || Math.abs(check) > 3E8) {
                        return;
                    }
                l.setX(x);
                l.setY(y);
                l.setZ(z);
            }
            l.setWorld(player.getWorld());
            if (hasRotation) {
                float yaw = flying.getLocation().getYaw();
                float pitch = flying.getLocation().getPitch();
                for (Float check : new Float[]{yaw, pitch})
                    if (check.isNaN() || check.isInfinite() || Math.abs(check) > 3E8) {
                        return;
                    }
                l.setYaw(yaw);
                l.setPitch(pitch);
            }
            profile.setTo(l.clone());
            if (hasRotation) {
                SensitivityProcessor controller = profile.getSensitivityProcessor();
                controller.setLastDeltaPitch(controller.getLastDeltaPitch());
                Vec2f from = new Vec2f(profile.getFrom().getYaw(), profile.getFrom().getPitch());
                Vec2f to = new Vec2f(profile.getTo().getYaw(), profile.getTo().getPitch());
                RotationEvent rotationEvent = new RotationEvent(profile, to, from);
                controller.setDeltaPitch(rotationEvent.getDelta().getY());
                controller.processSensitivity();
                boolean isTeleporting = (System.currentTimeMillis() - profile.getLastTeleport() < 500) || profile.isIgnoreFirstTick();

                if (ConfigCache.ROTATIONS_CONTAINER
                                && !profile.isIgnoreFirstTick()
                                && !isTeleporting) {
                    RotationsContainer.register(player.getUniqueId(), rotationEvent.getDelta());
                }

                profile.getCinematicComponent().process(rotationEvent);
                if (!isTeleporting) {
                    profile.run(rotationEvent);
                }
                profile.setIgnoreFirstTick(false);
            } else {
                if (!profile.isIgnoreFirstTick() && profile.getLastTeleport() + 1000 < System.currentTimeMillis()) {
                    if (profile.getTo().toVector().distance(profile.getFrom().toVector()) > 1e-4) {
                        profile.run(new NoRotationEvent(profile));
                    }
                }
            }

            profile.getPastLoc().add(profile.getTo());
            profile.run(new MoveEvent(profile, profile.getTo(), profile.getFrom()));

            feedDatasetRecorder(profile);

            if (profile.transactionBoot) LatencyHandler.startChecking(profile);
        }
    }

    private void feedDatasetRecorder(PlayerProfile profile) {
        Player player = profile.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!DatasetRecorder.isRecording(uuid)) return;
        if (profile.isIgnoreFirstTick()) return;

        long now = System.currentTimeMillis();
        if (now - profile.getLastTeleport() < 500) return;

        Location from = profile.getFrom();
        Location to = profile.getTo();
        Vec3 deltaVec = new Vec3(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
        double hSpeed = PhysicsEngine.computeHorizontalSpeed(deltaVec);
        double vSpeed = Math.abs(deltaVec.yCoord);

        DatasetRecorder.feedHorizontalSpeed(uuid, hSpeed);
        DatasetRecorder.feedVerticalSpeed(uuid, vSpeed);

        String toBlockName = to.getBlock().getType().name();
        boolean inWeb = PhysicsEngine.isInWeb(toBlockName);
        boolean inWater = PhysicsEngine.isInWater(toBlockName);
        boolean onClimbable = PhysicsEngine.isOnClimbable(toBlockName);
        double expectedMaxH = PhysicsEngine.getExpectedMaxHorizontalSpeed(
            profile.isSprinting(), profile.isSneaking(),
            profile.isGround(), inWeb, profile.getAirTicks(),
            inWater, onClimbable
        );
        DatasetRecorder.feedExpectedMaxH(uuid, expectedMaxH);

        double yawDelta = to.getYaw() - from.getYaw();
        double pitchDelta = to.getPitch() - from.getPitch();
        DatasetRecorder.feedYawDelta(uuid, yawDelta);
        DatasetRecorder.feedPitchDelta(uuid, pitchDelta);

        PhysicsSimulationService.feedMoveForRecording(profile, to);
    }
}