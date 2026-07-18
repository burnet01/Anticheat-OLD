package kireiko.dev.anticheat.checks.v2.world.scaffold;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.managers.CheckManager;

import java.util.*;

final class ScaffoldSupportV2 {
    private ScaffoldSupportV2() {}

    static PlacementContext context(final PlayerProfile profile) {
        if (profile == null) return null;
        return null;
    }

    static boolean shouldLookDown(final PlacementContext ctx) {
        return ctx != null && ctx.placedY <= ctx.clickedY && ctx.requiredPitch >= 45.0F;
    }

    static float yawDistance(final float a, final float b) {
        float diff = Math.abs(a - b) % 360.0F;
        return diff > 180.0F ? 360.0F - diff : diff;
    }

    static final class PlacementContext {
        final int clickedX, clickedY, clickedZ;
        final int placedX, placedY, placedZ;
        final float requiredYaw, requiredPitch;
        final float yawError, pitchError;
        final double reach, placedHorizontal;
        final boolean belowFeet, scaffoldLike;

        PlacementContext(int clickedX, int clickedY, int clickedZ,
                         int placedX, int placedY, int placedZ,
                         float requiredYaw, float requiredPitch,
                         float yawError, float pitchError,
                         double reach, double placedHorizontal,
                         boolean belowFeet, boolean scaffoldLike) {
            this.clickedX = clickedX; this.clickedY = clickedY; this.clickedZ = clickedZ;
            this.placedX = placedX; this.placedY = placedY; this.placedZ = placedZ;
            this.requiredYaw = requiredYaw; this.requiredPitch = requiredPitch;
            this.yawError = yawError; this.pitchError = pitchError;
            this.reach = reach; this.placedHorizontal = placedHorizontal;
            this.belowFeet = belowFeet; this.scaffoldLike = scaffoldLike;
        }
    }
}