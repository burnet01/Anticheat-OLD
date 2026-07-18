package win.ac.x.listeners;

import win.ac.x.utils.cache.EntityCache;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

public final class EntityTrackerListener {

    public static void register() {
        for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (Entity e : instance.getEntities()) {
                EntityCache.track(e);
            }
        }
    }
}