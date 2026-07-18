package win.ac.x.services;

import win.ac.x.X;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.aim.OnnxBaselineCheck;
import win.ac.x.checks.aim.ml.AimMLCheck;
import win.ac.x.checks.movement.OnnxPhysicsCheck;
import xac.cloud.proto.CheckVerdict;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VerdictDispatcher {

    private static final Map<UUID, Set<Object>> playerChecks = new ConcurrentHashMap<>();

    public static void registerPlayer(PlayerProfile profile) {
        UUID uuid = profile.getPlayer().getUniqueId();
        Set<Object> checks = new HashSet<>();
        for (Object check : profile.getChecks()) {
            if (check instanceof AimMLCheck || check instanceof OnnxBaselineCheck || check instanceof OnnxPhysicsCheck) {
                checks.add(check);
            }
        }
        playerChecks.put(uuid, checks);
    }

    public static void unregisterPlayer(UUID uuid) {
        playerChecks.remove(uuid);
    }

    public static void dispatch(String playerId, CheckVerdict verdict) {
        try {
            UUID uuid = UUID.fromString(playerId);
            Set<Object> checks = playerChecks.get(uuid);
            if (checks == null) return;

            for (Object check : checks) {
                if (check instanceof AimMLCheck) {
                    ((AimMLCheck) check).onVerdict(playerId, verdict);
                } else if (check instanceof OnnxBaselineCheck) {
                    ((OnnxBaselineCheck) check).onCloudVerdict(verdict);
                } else if (check instanceof OnnxPhysicsCheck) {
                    ((OnnxPhysicsCheck) check).onCloudVerdict(verdict);
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid UUID - ignore
        }
    }

    public static void init() {
        CloudClientService.setVerdictCallback(VerdictDispatcher::dispatch);
        X.getInstance().getLogger().info("[XAC-Cloud] Verdict dispatcher initialized.");
    }
}
