package kireiko.dev.millennium.phys;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

public final class PhysicsEngine {

    public static final double GRAVITY = 0.08D;
    public static final double AIR_FRICTION = 0.91D;
    public static final double GROUND_FRICTION = 0.6D;
    public static final double SLIPPERINESS = 0.6D;
    public static final double SPRINT_SPEED = 0.13D;
    public static final double WALK_SPEED = 0.1D;
    public static final double SNEAK_SPEED = 0.03D;
    public static final double JUMP_MOTION = 0.42D;
    public static final double STEP_HEIGHT = 0.6D;
    public static final double PLAYER_WIDTH = 0.6D;
    public static final double PLAYER_HEIGHT_STANDING = 1.8D;
    public static final double PLAYER_HEIGHT_SNEAKING = 1.65D;
    public static final double PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0D;
    public static final double MAX_WALK_SPEED = 0.22D;
    public static final double MAX_SPRINT_SPEED = 0.29D;
    public static final double MAX_SNEAK_SPEED = 0.068D;
    public static final double MAX_AIR_SPEED = 0.026D;
    public static final double MAX_JUMP_SPEED = 0.42D;
    public static final double MAX_VERTICAL_SPEED = 3.92D;
    public static final double WEB_SLOWDOWN = 0.25D;
    public static final double WATER_SLOWDOWN = 0.2D;
    public static final double WATER_GRAVITY = 0.02D;
    public static final double WATER_DRAG = 0.8D;
    public static final double LADDER_CLIMB_SPEED = 0.15D;
    public static final double LADDER_DESCEND_SPEED = 0.15D;
    public static final double LADDER_GRAVITY = 0.0D;

    public static Vector simulateVelocityTick(Vector motion, boolean onGround, boolean inWeb) {
        return simulateVelocityTick(motion, onGround, inWeb, false, false);
    }

    public static Vector simulateVelocityTick(Vector motion, boolean onGround, boolean inWeb, boolean inWater, boolean onClimbable) {
        double x = motion.getX();
        double y = motion.getY();
        double z = motion.getZ();

        if (inWeb) {
            x *= WEB_SLOWDOWN;
            y *= 0.05D;
            z *= WEB_SLOWDOWN;
        }

        if (inWater) {
            x *= WATER_DRAG;
            z *= WATER_DRAG;
            y = y * WATER_DRAG - WATER_GRAVITY;
            return new Vector(x, y, z);
        }

        if (onClimbable) {
            x *= 0.0D;
            z *= 0.0D;
            if (y > LADDER_CLIMB_SPEED) y = LADDER_CLIMB_SPEED;
            if (y < -LADDER_DESCEND_SPEED) y = -LADDER_DESCEND_SPEED;
            return new Vector(0, y, 0);
        }

        double friction = onGround ? GROUND_FRICTION : AIR_FRICTION;
        double slipperiness = SLIPPERINESS;
        double drag = friction * slipperiness;
        x *= drag;
        z *= drag;

        if (!onGround || inWeb) {
            y -= GRAVITY;
            y *= 0.9800000190734863D;
        } else {
            y = 0.0D;
        }

        return new Vector(x, y, z);
    }

    public static Vector simulateMovement(Vector lookDir, double speed, boolean sprinting, boolean sneaking, boolean onGround) {
        if (!onGround) {
            speed *= 0.02D;
        }

        double maxSpeed;
        if (sprinting) {
            maxSpeed = Math.min(speed, MAX_SPRINT_SPEED);
        } else if (sneaking) {
            maxSpeed = Math.min(speed, MAX_SNEAK_SPEED);
        } else {
            maxSpeed = Math.min(speed, MAX_WALK_SPEED);
        }

        return lookDir.clone().multiply(maxSpeed);
    }

    public static double getExpectedMaxHorizontalSpeed(boolean sprinting, boolean sneaking, boolean onGround, boolean inWeb, int airTicks) {
        return getExpectedMaxHorizontalSpeed(sprinting, sneaking, onGround, inWeb, airTicks, false, false);
    }

    public static double getExpectedMaxHorizontalSpeed(boolean sprinting, boolean sneaking, boolean onGround, boolean inWeb, int airTicks, boolean inWater, boolean onClimbable) {
        if (inWeb) return MAX_WALK_SPEED * WEB_SLOWDOWN;
        if (inWater) return MAX_WALK_SPEED * WATER_SLOWDOWN;
        if (onClimbable) return MAX_WALK_SPEED * 0.3D;

        if (!onGround) {
            if (airTicks <= 1) return MAX_SPRINT_SPEED * 0.91D;
            return MAX_AIR_SPEED * Math.pow(AIR_FRICTION, Math.max(0, airTicks - 1));
        }

        if (sprinting) return MAX_SPRINT_SPEED;
        if (sneaking) return MAX_SNEAK_SPEED;
        return MAX_WALK_SPEED;
    }

    public static double getExpectedMaxVerticalSpeed(boolean jumping, boolean onGround, boolean inWeb, boolean inWater, boolean inLava) {
        return getExpectedMaxVerticalSpeed(jumping, onGround, inWeb, inWater, inLava, false);
    }

    public static double getExpectedMaxVerticalSpeed(boolean jumping, boolean onGround, boolean inWeb, boolean inWater, boolean inLava, boolean onClimbable) {
        if (inWeb) return 0.05D;
        if (inWater || inLava) return 0.5D;
        if (onClimbable) return LADDER_CLIMB_SPEED;
        if (jumping && onGround) return JUMP_MOTION;
        return MAX_VERTICAL_SPEED;
    }

    public static double computeHorizontalSpeed(Vector delta) {
        return Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ());
    }

    public static boolean isBlockSolid(Material material) {
        if (material == null) return false;
        if (!material.isBlock()) return false;
        String name = material.name();
        if (name.contains("AIR") || name.contains("CAVE_AIR") || name.contains("VOID_AIR")) return false;
        if (name.contains("WATER") || name.contains("LAVA")) return false;
        if (name.contains("SIGN") || name.contains("BANNER") || name.contains("HEAD")
                || name.contains("SKULL")) return false;
        if (name.contains("TORCH") || name.contains("REDSTONE") || name.contains("REPEATER")
                || name.contains("COMPARATOR") || name.contains("RAIL")) return false;
        if (name.contains("BUTTON") || name.contains("LEVER") || name.contains("PRESSURE_PLATE")
                || name.contains("TRIPWIRE") || name.contains("STRING")) return false;
        if (name.contains("SNOW") || name.contains("CARPET") || name.contains("LILY_PAD")
                || name.contains("GRASS") || name.contains("FLOWER") || name.contains("SEAGRASS")
                || name.contains("KELP") || name.contains("CORAL") || name.contains("DEAD")) return false;
        if (name.contains("VINE") || name.contains("LADDER")) return false;
        return material.isSolid();
    }

    public static boolean isBlockSolidAt(Location loc, double expand) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Location check = new Location(loc.getWorld(), x + dx * expand, y + dy * expand, z + dz * expand);
                    if (isBlockSolid(check.getBlock().getType())) return true;
                }
            }
        }
        return false;
    }

    public static boolean isInWeb(Location loc) {
        Material mat = loc.getBlock().getType();
        return mat.name().contains("WEB") || mat == Material.COBWEB;
    }

    public static boolean isInLiquid(Location loc) {
        Material mat = loc.getBlock().getType();
        String name = mat.name();
        return name.contains("WATER") || name.contains("LAVA");
    }

    public static boolean isInWater(Location loc) {
        Material mat = loc.getBlock().getType();
        return mat.name().contains("WATER");
    }

    public static boolean isOnClimbable(Location loc) {
        Material mat = loc.getBlock().getType();
        String name = mat.name();
        if (name.contains("LADDER")) return true;
        if (name.contains("VINE")) return true;
        if (name.contains("SCAFFOLDING")) return true;
        if (name.contains("TWISTING_VINES")) return true;
        if (name.contains("WEEPING_VINES")) return true;
        if (name.contains("CAVE_VINES")) return true;
        return false;
    }

    public static boolean hasSolidGround(Location loc) {
        Location ground = loc.clone().subtract(0, 0.001, 0);
        return isBlockSolid(ground.getBlock().getType());
    }

    public static Vector applyVelocity(Vector currentMotion, Vector velocity) {
        return currentMotion.clone().add(velocity);
    }

    public static Vector computePossibleMotion(Vector lookDir, double speed, boolean sprinting,
                                                boolean sneaking, boolean onGround, boolean jumping,
                                                int airTicks, boolean inWeb, boolean inLiquid) {
        Vector motion;
        if (onGround) {
            motion = simulateMovement(lookDir, speed, sprinting, sneaking, true);
            if (jumping) {
                motion.setY(JUMP_MOTION);
            }
        } else {
            motion = simulateMovement(lookDir, speed, sprinting, sneaking, false);
            motion.setY(0);
        }
        return motion;
    }

    public static ShadowState createShadowState(Location initialLocation) {
        return new ShadowState(initialLocation);
    }

    public static class ShadowState {
        private Location position;
        private Vector motion;
        private boolean onGround;
        private boolean sprinting;
        private boolean sneaking;
        private int airTicks;
        private double cumulativeDrift;
        private int driftTicks;
        private long lastUpdateTime;

        public ShadowState(Location initial) {
            this.position = initial.clone();
            this.motion = new Vector(0, 0, 0);
            this.onGround = true;
            this.sprinting = false;
            this.sneaking = false;
            this.airTicks = 0;
            this.cumulativeDrift = 0.0;
            this.driftTicks = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public void updateState(Location realPosition, boolean realOnGround,
                                 boolean realSprinting, boolean realSneaking,
                                 Vector realVelocity, long now) {
            this.sprinting = realSprinting;
            this.sneaking = realSneaking;
            boolean wasOnGround = this.onGround;
            this.onGround = realOnGround;

            if (realVelocity != null && realVelocity.lengthSquared() > 0.0001) {
                this.motion = realVelocity.clone();
            }

            boolean inWeb = isInWeb(realPosition);
            boolean inLiquid = isInLiquid(realPosition);
            boolean inWater = isInWater(realPosition);
            boolean onClimbable = isOnClimbable(realPosition);
            this.lastInSpecialState = inLiquid || onClimbable || inWeb;

            if (!this.onGround && !onClimbable) {
                this.airTicks++;
            } else {
                this.airTicks = 0;
                if (!wasOnGround && this.motion.getY() < 0) {
                    this.position.setY(Math.floor(this.position.getY()) + 0.001);
                }
            }

            this.motion = simulateVelocityTick(this.motion, this.onGround, inWeb, inWater, onClimbable);

            if (this.onGround && hasSolidGround(this.position)) {
                Location testPos = this.position.clone().add(0, STEP_HEIGHT, 0);
                if (!isBlockSolidAt(testPos, PLAYER_HALF_WIDTH)) {
                    this.motion = simulateVelocityTick(this.motion, false, inWeb, inWater, onClimbable);
                }
            }

            this.position.add(this.motion);
            this.lastUpdateTime = now;
        }

        public double computeDrift(Location realPosition) {
            double dx = Math.abs(realPosition.getX() - this.position.getX());
            double dy = Math.abs(realPosition.getY() - this.position.getY());
            double dz = Math.abs(realPosition.getZ() - this.position.getZ());
            double horizontalDrift = Math.sqrt(dx * dx + dz * dz);
            double drift = horizontalDrift + dy * 0.5;
            this.cumulativeDrift += drift;
            this.driftTicks++;
            return drift;
        }

        public void resetAccumulatedDrift() {
            this.cumulativeDrift = 0.0;
            this.driftTicks = 0;
        }

        private boolean lastInSpecialState = false;

        public boolean isInSpecialState() {
            return lastInSpecialState;
        }

        public void snapToReality(Location realPosition) {
            this.position = realPosition.clone();
            this.motion = new Vector(0, 0, 0);
            this.cumulativeDrift = 0.0;
            this.driftTicks = 0;
            this.lastInSpecialState = false;
        }

        public Location getPosition() { return position; }
        public Vector getMotion() { return motion; }
        public boolean isOnGround() { return onGround; }
        public boolean isSprinting() { return sprinting; }
        public boolean isSneaking() { return sneaking; }
        public int getAirTicks() { return airTicks; }
        public double getCumulativeDrift() { return cumulativeDrift; }
        public int getDriftTicks() { return driftTicks; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }
}
