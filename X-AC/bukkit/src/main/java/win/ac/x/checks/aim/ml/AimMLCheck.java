package win.ac.x.checks.aim.ml;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.RotationEvent;
import win.ac.x.api.events.UseEntityEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.managers.CheckManager;
import win.ac.x.managers.DatasetManager;
import win.ac.x.services.CloudClientService;
import win.ac.x.services.CloudClientService.VerdictCallback;
import win.ac.x.services.DatasetRecorder;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.ml.data.ObjectML;
import xac.cloud.proto.CheckVerdict;
import win.ac.x.vectors.Vec2f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AimMLCheck implements PacketCheckHandler, VerdictCallback {

    private static final boolean TEST_MODE = false;
    public static final Map<UUID, Boolean> RECORDING = new ConcurrentHashMap<>();

    private final PlayerProfile profile;
    private final List<Vec2f> rawRotations;
    private final List<Vec2f> rnnRotations;
    private long lastAttack;
    private Map<String, Object> localCfg = new TreeMap<>();
    private final UUID playerUuid;

    public AimMLCheck(PlayerProfile profile) {
        this.profile = profile;
        this.playerUuid = profile != null ? profile.getPlayer().getUniqueId() : null;
        this.rawRotations = new CopyOnWriteArrayList<>();
        this.rnnRotations = new CopyOnWriteArrayList<>();
        this.lastAttack = 0L;
        if (profile != null && CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("unusual_vl", 10);
        localCfg.put("strange_vl", 20);
        localCfg.put("suspected_vl", 40);
        return new ConfigLabel("aim_ml", localCfg);
    }

    @Override
    public void applyConfig(Map<String, Object> params) {
        localCfg = params;
    }

    @Override
    public Map<String, Object> getConfig() {
        return localCfg;
    }

    @Override
    public void event(Object o) {
        if (o instanceof RotationEvent) {
            if (profile.isCinematic()) return;
            if (!((boolean) getConfig().get("enabled"))) return;
            RotationEvent event = (RotationEvent) o;

            if (System.currentTimeMillis() > this.lastAttack + 3000) {
                if (!this.rawRotations.isEmpty() && !RECORDING.containsKey(playerUuid)) {
                    this.rawRotations.clear();
                    this.rnnRotations.clear();
                }
                return;
            }

            Vec2f delta = event.getDelta();
            this.rawRotations.add(delta);
            this.rnnRotations.add(delta);

            UUID puid = playerUuid;
            if (DatasetRecorder.isRecording(puid)) {
                DatasetRecorder.feedYawDelta(puid, (double) delta.getX());
                DatasetRecorder.feedPitchDelta(puid, (double) delta.getY());
            }

            if (CloudClientService.isConnected() && !RECORDING.containsKey(playerUuid)) {
                CloudClientService.sendRotation(
                        playerUuid.toString(),
                        delta.getX(), delta.getY()
                );
            }

            if (this.rnnRotations.size() >= 150) {
                if (!RECORDING.containsKey(playerUuid)) {
                    this.rnnRotations.clear();
                }
            }

            if (this.rawRotations.size() >= 600) {
                if (RECORDING.containsKey(playerUuid)) {
                    saveRecordingSample();
                }
                this.rawRotations.clear();
            }
        } else if (o instanceof UseEntityEvent) {
            UseEntityEvent event = (UseEntityEvent) o;
            if (event.isAttack()) {
                this.lastAttack = System.currentTimeMillis();
                if (CloudClientService.isConnected()) {
                    CloudClientService.sendAttack(playerUuid.toString());
                }
            }
        }
    }

    private void saveRecordingSample() {
        List<ObjectML> objectMLStack = new ArrayList<>();
        ObjectML yaw = new ObjectML(new ArrayList<>());
        ObjectML pitch = new ObjectML(new ArrayList<>());

        for (Vec2f rot : this.rawRotations) {
            yaw.getValues().add((double) rot.getX());
            pitch.getValues().add((double) rot.getY());
        }

        objectMLStack.add(yaw);
        objectMLStack.add(pitch);

        Boolean recordType = RECORDING.get(playerUuid);
        if (recordType != null) {
            AsyncScheduler.run(() -> {
                DatasetManager.saveSample(objectMLStack, recordType);
                DatasetRecorder.stopRecording(playerUuid);
                int total = DatasetManager.getCount() + DatasetRecorder.getFileCount();
                profile.getPlayer().sendMessage("§a§l[DATASET] §aRecording complete! Sample saved. "
                        + "§7Total files: §f" + total + " §7— You can stop moving now.");
            });
            RECORDING.remove(playerUuid);
        }
    }

    @Override
    public void onVerdict(String playerId, CheckVerdict verdict) {
        if (!playerId.equals(playerUuid.toString())) return;
        if (!verdict.getCheckType().startsWith("aim_ml")) return;

        String verdictStr = verdict.getVerdict();
        FlagType type = FlagType.NORMAL;
        switch (verdictStr) {
            case "SUSPECTED": type = FlagType.SUSPECTED; break;
            case "STRANGE": type = FlagType.STRANGE; break;
            case "UNUSUAL": type = FlagType.UNUSUAL; break;
        }

        if (type != FlagType.NORMAL) {
            float vl = 0f;
            String color = "&a";
            switch (type) {
                case UNUSUAL: color = "&e"; vl = ((Number) localCfg.get("unusual_vl")).floatValue() / 10f; break;
                case STRANGE: color = "&6"; vl = ((Number) localCfg.get("strange_vl")).floatValue() / 10f; break;
                case SUSPECTED: color = "&c"; vl = ((Number) localCfg.get("suspected_vl")).floatValue() / 10f; break;
            }
            profile.punish("Aim", "ML", "&fResult: " + color + type + " &8[cloud] " + verdict.getDetails(), vl);
        }
    }

    private enum FlagType {
        NORMAL(0), UNUSUAL(1), STRANGE(2), SUSPECTED(3);
        private final int level;
        FlagType(int level) { this.level = level; }
    }
}