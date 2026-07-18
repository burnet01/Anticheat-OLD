package kireiko.dev.anticheat.core;

import kireiko.dev.anticheat.MX;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

public final class AsyncEntityFetcher {
    public static CompletableFuture<Entity> getEntityFromIDAsync(final World world, final int entityId) {
        CompletableFuture<Entity> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(MX.getInstance(), () -> {
            for (Entity entity : world.getEntities()) {
                if (entity.getEntityId() == entityId) {
                    future.complete(entity);
                    return;
                }
            }
            future.completeExceptionally(new RuntimeException("Entity not found: " + entityId));
        });

        return future;
    }
}