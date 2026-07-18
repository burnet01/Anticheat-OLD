package win.ac.x.checks.v2.combat.autoclicker;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;

import java.util.HashMap;
import java.util.Map;

public final class AutoClickerAV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private boolean enabled = true;
    public AutoClickerAV2(PlayerProfile profile) { this.profile = profile; }
    @Override public void event(Object event) {}
    @Override public PacketCheckHandler clone() { try { return (PacketCheckHandler) super.clone(); } catch (Exception e) { return null; } }
    @Override public void applyConfig(Map<String, Object> params) { this.enabled = (boolean) params.getOrDefault("enabled", true); }
    @Override public Map<String, Object> getConfig() { Map<String, Object> m = new HashMap<>(); m.put("enabled", enabled); return m; }
    @Override public ConfigLabel config() { Map<String, Object> d = new HashMap<>(); d.put("enabled", true); return new ConfigLabel("autoclicker_a_v2", d); }
}