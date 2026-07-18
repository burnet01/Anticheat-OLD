package win.ac.x.checks.velocity;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.SVelocityEvent;
import win.ac.x.api.player.PlayerProfile;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;

public final class VelocityCheck implements PacketCheckHandler {

    private final PlayerProfile profile;
    private boolean enabled = true;
    private double maxHorizontal = 0.4;
    private double maxVertical = 0.1;

    public VelocityCheck(PlayerProfile profile) {
        this.profile = profile;
    }

    @Override
    public void event(Object event) {
        if (!enabled) return;
        if (event instanceof SVelocityEvent) {
            SVelocityEvent e = (SVelocityEvent) event;
            double hSpeed = Math.sqrt(e.getVelocity().x() * e.getVelocity().x()
                    + e.getVelocity().z() * e.getVelocity().z());
            double vSpeed = e.getVelocity().y();

            if (hSpeed > maxHorizontal || vSpeed > maxVertical) {
                profile.punish("Velocity", "Abnormal", "h=" + hSpeed + " v=" + vSpeed, 1.0f);
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
        this.maxHorizontal = ((Number) params.getOrDefault("maxHorizontal", 0.4)).doubleValue();
        this.maxVertical = ((Number) params.getOrDefault("maxVertical", 0.1)).doubleValue();
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", enabled);
        config.put("maxHorizontal", maxHorizontal);
        config.put("maxVertical", maxVertical);
        return config;
    }

    @Override
    public ConfigLabel config() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        defaults.put("maxHorizontal", 0.4);
        defaults.put("maxVertical", 0.1);
        return new ConfigLabel("velocity", defaults);
    }
}