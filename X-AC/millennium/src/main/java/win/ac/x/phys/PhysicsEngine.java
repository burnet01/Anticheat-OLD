package win.ac.x.phys;

import win.ac.x.vectors.Vec3;

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

    private PhysicsEngine() {}

    public interface BlockAccessor {
        String getBlockName(double x, double y, double z);
    }

    public static double[] simulateVelocityTick(double mx, double my, double mz, boolean onGround, boolean inWeb) {
        return simulateVelocityTick(mx, my, mz, onGround, inWeb, false, false);
    }

    public static double[] simulateVelocityTick(double mx, double my, double mz, boolean onGround,
                                                  boolean inWeb, boolean inWater, boolean onClimbable) {
        double x = mx, y = my, z = mz;

        if (inWeb) {
            x *= WEB_SLOWDOWN;
            y *= 0.05D;
            z *= WEB_SLOWDOWN;
        }

        if (inWater) {
            x *= WATER_DRAG;
            z *= WATER_DRAG;
            y = y * WATER_DRAG - WATER_GRAVITY;
            return new double[]{x, y, z};
        }

        if (onClimbable) {
            if (y > LADDER_CLIMB_SPEED) y = LADDER_CLIMB_SPEED;
            if (y < -LADDER_DESCEND_SPEED) y = -LADDER_DESCEND_SPEED;
            return new double[]{0, y, 0};
        }

        double friction = onGround ? GROUND_FRICTION : AIR_FRICTION;
        double drag = friction * SLIPPERINESS;
        x *= drag;
        z *= drag;

        if (!onGround || inWeb) {
            y -= GRAVITY;
            y *= 0.9800000190734863D;
        } else {
            y = 0.0D;
        }

        return new double[]{x, y, z};
    }

    public static double[] simulateMovement(double yaw, double speed, boolean sprinting, boolean sneaking, boolean onGround) {
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

        double yawRad = Math.toRadians(yaw);
        return new double[]{-Math.sin(yawRad) * maxSpeed, 0, Math.cos(yawRad) * maxSpeed};
    }

    public static double getExpectedMaxHorizontalSpeed(boolean sprinting, boolean sneaking,
                                                        boolean onGround, boolean inWeb, int airTicks) {
        return getExpectedMaxHorizontalSpeed(sprinting, sneaking, onGround, inWeb, airTicks, false, false);
    }

    public static double getExpectedMaxHorizontalSpeed(boolean sprinting, boolean sneaking,
                                                        boolean onGround, boolean inWeb, int airTicks,
                                                        boolean inWater, boolean onClimbable) {
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

    public static double getExpectedMaxVerticalSpeed(boolean jumping, boolean onGround,
                                                      boolean inWeb, boolean inWater, boolean inLava) {
        return getExpectedMaxVerticalSpeed(jumping, onGround, inWeb, inWater, inLava, false);
    }

    public static double getExpectedMaxVerticalSpeed(boolean jumping, boolean onGround,
                                                      boolean inWeb, boolean inWater, boolean inLava,
                                                      boolean onClimbable) {
        if (inWeb) return 0.05D;
        if (inWater || inLava) return 0.5D;
        if (onClimbable) return LADDER_CLIMB_SPEED;
        if (jumping && onGround) return JUMP_MOTION;
        return MAX_VERTICAL_SPEED;
    }

    public static double computeHorizontalSpeed(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double computeHorizontalSpeed(Vec3 delta) {
        return Math.sqrt(delta.xCoord * delta.xCoord + delta.zCoord * delta.zCoord);
    }

    public static boolean isBlockSolid(String blockName) {
        if (blockName == null) return false;
        String name = blockName.toUpperCase();
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
        return true;
    }

    public static boolean isBlockSolidAt(double x, double y, double z, double expand, BlockAccessor blocks) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String blockName = blocks.getBlockName(x + dx * expand, y + dy * expand, z + dz * expand);
                    if (isBlockSolid(blockName)) return true;
                }
            }
        }
        return false;
    }

    public static boolean isInWeb(String blockName) {
        if (blockName == null) return false;
        String name = blockName.toUpperCase();
        return name.contains("WEB") || name.equals("COBWEB");
    }

    public static boolean isInLiquid(String blockName) {
        if (blockName == null) return false;
        String name = blockName.toUpperCase();
        return name.contains("WATER") || name.contains("LAVA");
    }

    public static boolean isInWater(String blockName) {
        if (blockName == null) return false;
        return blockName.toUpperCase().contains("WATER");
    }

    public static boolean isOnClimbable(String blockName) {
        if (blockName == null) return false;
        String name = blockName.toUpperCase();
        if (name.contains("LADDER")) return true;
        if (name.contains("VINE")) return true;
        if (name.contains("SCAFFOLDING")) return true;
        if (name.contains("TWISTING_VINES")) return true;
        if (name.contains("WEEPING_VINES")) return true;
        if (name.contains("CAVE_VINES")) return true;
        return false;
    }

    public static boolean hasSolidGround(double x, double y, double z, BlockAccessor blocks) {
        String blockName = blocks.getBlockName(x, y - 0.001, z);
        return isBlockSolid(blockName);
    }

    public static double[] applyVelocity(double[] currentMotion, double vx, double vy, double vz) {
        return new double[]{currentMotion[0] + vx, currentMotion[1] + vy, currentMotion[2] + vz};
    }

    public static double[] computePossibleMotion(double yaw, double speed, boolean sprinting,
                                                  boolean sneaking, boolean onGround, boolean jumping,
                                                  int airTicks, boolean inWeb, boolean inLiquid) {
        double[] motion;
        if (onGround) {
            motion = simulateMovement(yaw, speed, sprinting, sneaking, true);
            if (jumping) {
                motion[1] = JUMP_MOTION;
            }
        } else {
            motion = simulateMovement(yaw, speed, sprinting, sneaking, false);
            motion[1] = 0;
        }
        return motion;
    }

    public static ShadowState createShadowState(double x, double y, double z) {
        return new ShadowState(x, y, z);
    }

    public static class ShadowState {
        private double posX, posY, posZ;
        private double motionX, motionY, motionZ;
        private boolean onGround;
        private boolean sprinting;
        private boolean sneaking;
        private int airTicks;
        private double cumulativeDrift;
        private int driftTicks;
        private long lastUpdateTime;
        private boolean lastInSpecialState = false;
        private BlockAccessor blockAccessor;

        public ShadowState(double x, double y, double z) {
            this.posX = x; this.posY = y; this.posZ = z;
            this.motionX = motionY = motionZ = 0;
            this.onGround = true;
            this.sprinting = false;
            this.sneaking = false;
            this.airTicks = 0;
            this.cumulativeDrift = 0.0;
            this.driftTicks = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public void setBlockAccessor(BlockAccessor accessor) {
            this.blockAccessor = accessor;
        }

        public void updateState(double realX, double realY, double realZ, boolean realOnGround,
                                 boolean realSprinting, boolean realSneaking,
                                 Double realVelX, Double realVelY, Double realVelZ, long now) {
            this.sprinting = realSprinting;
            this.sneaking = realSneaking;
            boolean wasOnGround = this.onGround;
            this.onGround = realOnGround;

            if (realVelX != null && realVelY != null && realVelZ != null
                    && (realVelX * realVelX + realVelY * realVelY + realVelZ * realVelZ) > 0.0001) {
                this.motionX = realVelX;
                this.motionY = realVelY;
                this.motionZ = realVelZ;
            }

            String blockName = blockAccessor != null ? blockAccessor.getBlockName(realX, realY, realZ) : null;
            boolean inWeb = isInWeb(blockName);
            boolean inLiquid = isInLiquid(blockName);
            boolean inWater = isInWater(blockName);
            boolean onClimbable = isOnClimbable(blockName);
            this.lastInSpecialState = inLiquid || onClimbable || inWeb;

            if (!this.onGround && !onClimbable) {
                this.airTicks++;
            } else {
                this.airTicks = 0;
                if (!wasOnGround && this.motionY < 0) {
                    this.posY = Math.floor(this.posY) + 0.001;
                }
            }

            double[] newMotion = simulateVelocityTick(this.motionX, this.motionY, this.motionZ,
                    this.onGround, inWeb, inWater, onClimbable);
            this.motionX = newMotion[0]; this.motionY = newMotion[1]; this.motionZ = newMotion[2];

            if (this.onGround && blockAccessor != null && hasSolidGround(this.posX, this.posY, this.posZ, blockAccessor)) {
                if (!isBlockSolidAt(this.posX, this.posY + STEP_HEIGHT, this.posZ, PLAYER_HALF_WIDTH, blockAccessor)) {
                    double[] newM = simulateVelocityTick(this.motionX, this.motionY, this.motionZ,
                            false, inWeb, inWater, onClimbable);
                    this.motionX = newM[0]; this.motionY = newM[1]; this.motionZ = newM[2];
                }
            }

            this.posX += this.motionX;
            this.posY += this.motionY;
            this.posZ += this.motionZ;
            this.lastUpdateTime = now;
        }

        public double computeDrift(double realX, double realY, double realZ) {
            double dx = Math.abs(realX - this.posX);
            double dy = Math.abs(realY - this.posY);
            double dz = Math.abs(realZ - this.posZ);
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

        public boolean isInSpecialState() {
            return lastInSpecialState;
        }

        public void snapToReality(double x, double y, double z) {
            this.posX = x; this.posY = y; this.posZ = z;
            this.motionX = motionY = motionZ = 0;
            this.cumulativeDrift = 0.0;
            this.driftTicks = 0;
            this.lastInSpecialState = false;
        }

        public double getPosX() { return posX; }
        public double getPosY() { return posY; }
        public double getPosZ() { return posZ; }
        public double getMotionX() { return motionX; }
        public double getMotionY() { return motionY; }
        public double getMotionZ() { return motionZ; }
        public boolean isOnGround() { return onGround; }
        public boolean isSprinting() { return sprinting; }
        public boolean isSneaking() { return sneaking; }
        public int getAirTicks() { return airTicks; }
        public double getCumulativeDrift() { return cumulativeDrift; }
        public int getDriftTicks() { return driftTicks; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }
}