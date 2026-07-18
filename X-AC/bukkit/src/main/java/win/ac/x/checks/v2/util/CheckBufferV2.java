package win.ac.x.checks.v2.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CheckBufferV2 {
    private final Map<UUID, Double> bufferMap = new ConcurrentHashMap<>();

    public double increase(UUID uuid, double amount) {
        double current = bufferMap.getOrDefault(uuid, 0.0);
        current += amount;
        if (current > 100.0) current = 100.0;
        bufferMap.put(uuid, current);
        return current;
    }

    public void decrease(UUID uuid, double amount) {
        double current = bufferMap.getOrDefault(uuid, 0.0);
        if (current > 0) {
            current -= amount;
            if (current < 0) current = 0;
            bufferMap.put(uuid, current);
        }
    }

    public void reset(UUID uuid, double value) {
        bufferMap.put(uuid, value);
    }

    public double get(UUID uuid) {
        return bufferMap.getOrDefault(uuid, 0.0);
    }

    public void remove(UUID uuid) {
        bufferMap.remove(uuid);
    }
}