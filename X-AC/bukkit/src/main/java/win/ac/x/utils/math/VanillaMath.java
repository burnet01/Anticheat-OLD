package win.ac.x.utils.math;

import win.ac.x.math.FastMath;

public final class VanillaMath implements ClientMath {
    public static float sqrt(float f) {
        return (float) Math.sqrt(f);
    }

    @Override
    public float sin(float value) {
        return FastMath.sin(value);
    }

    @Override
    public float cos(float value) {
        return FastMath.cos(value);
    }
}