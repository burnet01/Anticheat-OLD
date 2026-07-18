package win.ac.x.api.data;

import win.ac.x.api.player.PlayerProfile;

@FunctionalInterface
public interface PunishmentHandler {
    void onPunish(PlayerProfile profile, String check, String component, String info, float vl);
}