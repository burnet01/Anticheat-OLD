package win.ac.x.checks.protocol;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.EntityActionEvent;
import win.ac.x.api.player.PlayerProfile;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;

public final class SprintCheck implements PacketCheckHandler {

    private final PlayerProfile profile;
    private boolean enabled = true;

    public SprintCheck(PlayerProfile profile) {
        this.profile = profile;
    }

    @Override
    public void event(Object event) {
        if (!enabled) return;
        if (event instanceof EntityActionEvent) {
            EntityActionEvent e = (EntityActionEvent) event;
            if (e.getAbilitiesEnum() == null) {
                profile.punish("Sprint", "Invalid", "Null action", 0.5f);
            }
        }
    }

    @Override
    @SneakyThrows
    public PacketCheckHandler clone() {
        return (PacketCheckHandler) super.clone();
    }

    @Override
    public void applyConfig(Map<String, Object> params) {
        this.enabled = (boolean) params.getOrDefault("enabled", true);
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", enabled);
        return config;
    }

    @Override
    public ConfigLabel config() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        return new ConfigLabel("sprint", defaults);
    }
}