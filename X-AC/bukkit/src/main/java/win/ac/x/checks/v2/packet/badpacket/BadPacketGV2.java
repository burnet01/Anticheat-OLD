package win.ac.x.checks.v2.packet.badpacket;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.MoveEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.managers.CheckManager;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public final class BadPacketGV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        return new ConfigLabel("v2_badpacket_g", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public BadPacketGV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof MoveEvent) {
            MoveEvent e = (MoveEvent) o;
            PlayerProfile pf = e.getProfile();
            Location to = pf.getTo();

            double x = to.getX();
            double y = to.getY();
            double z = to.getZ();

            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                pf.punish("BadPacket", "G", String.format("NaN Position. X: %s, Y: %s, Z: %s", x, y, z), 1.0f);
                return;
            }

            if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
                pf.punish("BadPacket", "G", String.format("Infinite Position. X: %s, Y: %s, Z: %s", x, y, z), 1.0f);
                return;
            }

            double maxCoord = 30000000.0;
            if (Math.abs(x) > maxCoord || Math.abs(z) > maxCoord) {
                pf.punish("BadPacket", "G", String.format("Extreme Position. X: %.0f, Z: %.0f", x, z), 1.0f);
                return;
            }

            World world = to.getWorld();
            if (world != null) {
                int minY = world.getMinHeight() - 64;
                int maxY = world.getMaxHeight() + 64;
                if (y < minY || y > maxY) {
                    pf.punish("BadPacket", "G", String.format("Invalid Y Position: %.2f", y), 1.0f);
                }
            }
        }
    }
}