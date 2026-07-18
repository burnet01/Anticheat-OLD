package win.ac.x.utils.cache;

import net.minestom.server.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCache {

    private EntityCache() {}

    private static final Map<Integer, WeakReference<Entity>> CACHE = new ConcurrentHashMap<>();

    public static Entity get(int entityId) {
        WeakReference<Entity> ref = CACHE.get(entityId);
        if (ref == null) return null;
        Entity e = ref.get();
        if (e == null) {
            CACHE.remove(entityId);
            return null;
        }
        return e;
    }

    public static void track(Entity entity) {
        if (entity == null) return;
        CACHE.put(entity.getEntityId(), new WeakReference<>(entity));
    }

    public static void untrack(int entityId) {
        CACHE.remove(entityId);
    }

    public static void clear() {
        CACHE.clear();
    }
}