package win.ac.x.checks.v2.movement.inventory;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.*;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.v2.util.CheckBufferV2;
import win.ac.x.managers.CheckManager;

import java.util.*;

public final class InventoryAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private final CheckBufferV2 buffer = new CheckBufferV2();
    private Map<String, Object> localCfg = new TreeMap<>();
    private long lastWindowClick = 0;
    private long lastInventoryClose = 0;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_inventory_a", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public InventoryAV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof WindowClickEvent) {
            lastWindowClick = System.currentTimeMillis();
            return;
        }
        if (o instanceof NoRotationEvent) {
            lastInventoryClose = System.currentTimeMillis();
            return;
        }
        if (o instanceof MoveEvent) {
            if (profile.isIgnoreFirstTick()) return;
            if (profile.getPlayer().isGliding()) return;

            long now = System.currentTimeMillis();
            long clickDelta = now - lastWindowClick;

            if (clickDelta >= 200L) {
                buffer.decrease(profile.getPlayer().getUniqueId(), 0.1D);
                return;
            }

            if (now - lastInventoryClose < 250L) {
                buffer.decrease(profile.getPlayer().getUniqueId(), 0.2D);
                return;
            }

            MoveEvent event = (MoveEvent) o;
            double move = Math.hypot(
                event.getTo().getX() - event.getFrom().getX(),
                event.getTo().getZ() - event.getFrom().getZ());
            double allowed = profile.isGround() ? 0.15D : 0.12D;
            if (profile.airTicks < 4) allowed += 0.05D;

            if (move > allowed) {
                if (buffer.increase(profile.getPlayer().getUniqueId(), 1.0D) > 5.0D) {
                    profile.punish("Movement", "InventoryA",
                        String.format("Inventory move speed=%.3f limit=%.3f clickDelta=%dms", move, allowed, clickDelta), 1.0f);
                    buffer.reset(profile.getPlayer().getUniqueId(), 2.0D);
                }
            } else {
                buffer.decrease(profile.getPlayer().getUniqueId(), 0.2D);
            }
        }
    }
}