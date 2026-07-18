package kireiko.dev.anticheat.checks.movement;

import kireiko.dev.anticheat.MX;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.services.OnnxInferenceService;
import kireiko.dev.anticheat.services.PhysicsSimulationService;
import kireiko.dev.anticheat.services.PhysicsSimulationService.DriftRecord;
import kireiko.dev.anticheat.utils.ConfigCache;
import kireiko.dev.anticheat.utils.MessageUtils;
import kireiko.dev.millennium.phys.PhysicsEngine;
import kireiko.dev.millennium.math.Simplification;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class OnnxPhysicsCheck implements PacketCheckHandler {

    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final float[][] buffer = new float[20][10];
    private int writeIndex = 0;
    private boolean bufferFull = false;
    private double lastHorizontalSpeed = 0.0;

    private final float[] recentHSpeeds = new float[10];
    private int recentHSpeedIndex = 0;
    private int recentHSpeedCount = 0;

    private boolean inEpisode = false;
    private int episodeDetections = 0;
    private double episodePeakProb = 0.0;
    private String episodeLabel = "";
    private int episodeTicks = 0;
    private int cleanTicks = 0;
    private static final int CLEAN_TICKS_TO_END = 20;
    private static final int THROTTLE_INTERVAL = 5;
    private static final double CONVICTION_THRESHOLD = 0.85;

    public OnnxPhysicsCheck(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass())) {
            this.localCfg = CheckManager.getConfig(this.getClass());
        }
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("threshold", 0.85);
        return new ConfigLabel("onnx_physics", localCfg);
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
        if (!(boolean) localCfg.get("enabled")) return;
        if (!OnnxInferenceService.isPhysicsReady()) return;

        if (o instanceof MoveEvent) {
            MoveEvent event = (MoveEvent) o;
            Location to = event.getTo();

            if (profile.isIgnoreFirstTick()) return;

            long now = System.currentTimeMillis();
            if (now - profile.getLastTeleport() < 500) {
                resetBuffer();
                return;
            }

            kireiko.dev.millennium.vectors.Vec3 deltaVec = event.getDelta();
            Vector delta = new Vector(deltaVec.xCoord, deltaVec.yCoord, deltaVec.zCoord);
            double hSpeed = PhysicsEngine.computeHorizontalSpeed(delta);
            double vSpeed = Math.abs(deltaVec.yCoord);

            boolean inWeb = PhysicsEngine.isInWeb(to);
            boolean inLiquid = PhysicsEngine.isInLiquid(to);
            boolean inWater = PhysicsEngine.isInWater(to);
            boolean onClimbable = PhysicsEngine.isOnClimbable(to);
            double expectedMaxH = PhysicsEngine.getExpectedMaxHorizontalSpeed(
                profile.isSprinting(), profile.isSneaking(),
                profile.isGround(), inWeb, profile.getAirTicks(),
                inWater, onClimbable
            );

            DriftRecord record = PhysicsSimulationService.getDriftRecord(profile);
            double tickDrift = (record != null) ? record.getCurrentTickDrift() : 0.0;

            double acceleration = hSpeed - lastHorizontalSpeed;
            lastHorizontalSpeed = hSpeed;

            float speedRatio = (float) (expectedMaxH > 0.001 ? hSpeed / expectedMaxH : 0.0);
            float groundState = profile.isGround() ? 1.0f : 0.0f;
            float sprintState = profile.isSprinting() ? 1.0f : 0.0f;

            recentHSpeeds[recentHSpeedIndex] = (float) hSpeed;
            recentHSpeedIndex = (recentHSpeedIndex + 1) % 10;
            recentHSpeedCount = Math.min(recentHSpeedCount + 1, 10);
            float speedVariance = 0.0f;
            if (recentHSpeedCount >= 2) {
                double sum = 0.0;
                for (int i = 0; i < recentHSpeedCount; i++) sum += recentHSpeeds[i];
                double mean = sum / recentHSpeedCount;
                double sumSq = 0.0;
                for (int i = 0; i < recentHSpeedCount; i++) {
                    double diff = recentHSpeeds[i] - mean;
                    sumSq += diff * diff;
                }
                speedVariance = (float) Math.sqrt(sumSq / recentHSpeedCount);
            }

            buffer[writeIndex][0] = (float) tickDrift;
            buffer[writeIndex][1] = (float) hSpeed;
            buffer[writeIndex][2] = (float) vSpeed;
            buffer[writeIndex][3] = (float) expectedMaxH;
            buffer[writeIndex][4] = (float) profile.getAirTicks();
            buffer[writeIndex][5] = (float) acceleration;
            buffer[writeIndex][6] = speedRatio;
            buffer[writeIndex][7] = groundState;
            buffer[writeIndex][8] = sprintState;
            buffer[writeIndex][9] = speedVariance;

            writeIndex++;
            if (writeIndex >= 20) {
                writeIndex = 0;
                bufferFull = true;
            }

            if (bufferFull) {
                runInference();
            }
        }
    }

    private void runInference() {
        List<OnnxInferenceService.ModelResult> results = OnnxInferenceService.runPhysics(buffer);
        float threshold = ((Number) localCfg.get("threshold")).floatValue();

        double maxProb = 0.0;
        String bestLabel = "";

        for (OnnxInferenceService.ModelResult r : results) {
            if (r.probability > maxProb) {
                maxProb = r.probability;
                bestLabel = r.label;
            }
        }

        boolean isCheating = maxProb >= threshold;

        if (isCheating) {
            cleanTicks = 0;
            episodeTicks++;

            if (!inEpisode) {
                if (episodeTicks >= 2) {
                    inEpisode = true;
                    episodeDetections = 1;
                    episodePeakProb = maxProb;
                    episodeLabel = bestLabel;
                    sendLiveUpdate(maxProb);
                }
            } else {
                episodeDetections++;
                if (maxProb > episodePeakProb) episodePeakProb = maxProb;
                double vlAdd = (maxProb - threshold) * 10.0 + 2.0;
                profile.setVl(profile.getVl() + (float) vlAdd);

                if (episodeDetections % THROTTLE_INTERVAL == 0) {
                    sendLiveUpdate(maxProb);
                }
            }
        } else {
            if (inEpisode) {
                cleanTicks++;
                if (cleanTicks >= CLEAN_TICKS_TO_END) {
                    endEpisode();
                }
            }
        }
    }

    private void sendLiveUpdate(double currentProb) {
        String playerName = profile.getPlayer().getName();
        String peakStr = String.valueOf(Simplification.scaleVal(episodePeakProb * 100, 1));
        String curStr = String.valueOf(Simplification.scaleVal(currentProb * 100, 1));
        String durationStr = String.valueOf(Simplification.scaleVal(episodeTicks / 20.0, 1));

        String alertMsg = ConfigCache.ALERT_MSG
            .replace("%player%", playerName)
            .replace("%check%", "OnnxPhysics")
            .replace("%component%", episodeLabel)
            .replace("%info%", ">> x" + episodeDetections + " " + curStr + "% (peak " + peakStr + "%)")
            .replace("%vl%", String.valueOf((int) profile.getVl()))
            .replace("%vlLimit%", String.valueOf((int) ConfigCache.VL_LIMIT));

        String hoverText = ChatColor.YELLOW + "Label: " + ChatColor.RED + episodeLabel + "\n"
            + ChatColor.YELLOW + "Detections: " + ChatColor.RED + episodeDetections + "\n"
            + ChatColor.YELLOW + "Current Probability: " + ChatColor.RED + curStr + "%\n"
            + ChatColor.YELLOW + "Peak Probability: " + ChatColor.RED + peakStr + "%\n"
            + ChatColor.YELLOW + "Duration: " + ChatColor.RED + durationStr + "s (" + episodeTicks + " ticks)\n"
            + ChatColor.YELLOW + "Threshold: " + ChatColor.RED + CONVICTION_THRESHOLD + "\n"
            + ChatColor.YELLOW + "Check: " + ChatColor.RED + "OnnxPhysics (ML)\n"
            + ChatColor.GRAY + "(ongoing)";

        TextComponent component = new TextComponent(MessageUtils.wrapColors(alertMsg));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hoverText).create()));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(MX.permission)) {
                p.spigot().sendMessage(component);
            }
        }
    }

    private void endEpisode() {
        if (!inEpisode) return;
        inEpisode = false;

        String playerName = profile.getPlayer().getName();
        String peakStr = String.valueOf(Simplification.scaleVal(episodePeakProb * 100, 1));
        String durationStr = String.valueOf(Simplification.scaleVal(episodeTicks / 20.0, 1));

        String alertMsg = ConfigCache.ALERT_MSG
            .replace("%player%", playerName)
            .replace("%check%", "OnnxPhysics")
            .replace("%component%", episodeLabel)
            .replace("%info%", "FINAL x" + episodeDetections + " peak " + peakStr + "%")
            .replace("%vl%", String.valueOf((int) profile.getVl()))
            .replace("%vlLimit%", String.valueOf((int) ConfigCache.VL_LIMIT));

        String hoverText = ChatColor.YELLOW + "Label: " + ChatColor.RED + episodeLabel + "\n"
            + ChatColor.YELLOW + "Total Detections: " + ChatColor.RED + episodeDetections + "\n"
            + ChatColor.YELLOW + "Peak Probability: " + ChatColor.RED + peakStr + "%\n"
            + ChatColor.YELLOW + "Duration: " + ChatColor.RED + durationStr + "s (" + episodeTicks + " ticks)\n"
            + ChatColor.YELLOW + "Threshold: " + ChatColor.RED + CONVICTION_THRESHOLD + "\n"
            + ChatColor.YELLOW + "Check: " + ChatColor.RED + "OnnxPhysics (ML)\n"
            + ChatColor.GREEN + "(episode ended)";

        TextComponent component = new TextComponent(MessageUtils.wrapColors(alertMsg));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hoverText).create()));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(MX.permission)) {
                p.spigot().sendMessage(component);
            }
        }

        profile.punish("OnnxPhysics", episodeLabel,
            "x" + episodeDetections + " detections, peak=" + peakStr + "%",
            Math.max(0.1f, (float)(episodePeakProb - CONVICTION_THRESHOLD) * 5f));

        episodeDetections = 0;
        episodePeakProb = 0.0;
        episodeTicks = 0;
        episodeLabel = "";
        cleanTicks = 0;
    }

    private void resetBuffer() {
        writeIndex = 0;
        bufferFull = false;
        lastHorizontalSpeed = 0.0;
        recentHSpeedIndex = 0;
        recentHSpeedCount = 0;
        for (int i = 0; i < 10; i++) recentHSpeeds[i] = 0.0f;
    }
}
