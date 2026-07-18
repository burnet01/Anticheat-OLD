package win.ac.x.core;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;

import java.util.concurrent.CompletableFuture;

public final class AsyncEntityFetcher {
    public static CompletableFuture<Entity> getEntityFromIDAsync(final Instance instance, final int entityId) {
        CompletableFuture<Entity> future = new CompletableFuture<>();
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            for (Entity entity : instance.getEntities()) {
                if (entity.getEntityId() == entityId) {
                    future.complete(entity);
                    return TaskSchedule.stop();
                }
            }
            future.completeExceptionally(new RuntimeException("Entity not found: " + entityId));
            return TaskSchedule.stop();
        });

        return future;
    }
}