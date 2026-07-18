package win.ac.x.checks.aim;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.MoveEvent;
import win.ac.x.api.events.RotationEvent;
import win.ac.x.api.events.UseEntityEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.managers.CheckManager;
import win.ac.x.services.CloudClientService;
import xac.cloud.proto.CheckVerdict;
import win.ac.x.utils.ConfigCache;
import win.ac.x.utils.MessageUtils;
import win.ac.x.math.Simplification;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class OnnxBaselineCheck implements PacketCheckHandler {

    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();
    private final UUID playerUuid;

    private double latestHSpeed = 0.0;
    private long lastAttackTime = 0;
    private float latestClickInterval = 0f;

    private boolean inEpisode = false;
    private int episodeDetections = 0;
    private double episodePeakProb = 0.0;
    private String episodeLabel = "";
    private int episodeTicks = 0;
    private int cleanTicks = 0;
    private static final int CLEAN_TICKS_TO_END = 20;
    private static final int THROTTLE_INTERVAL = 5;
    private static final double CONVICTION_THRESHOLD = 0.85;

    public OnnxBaselineCheck(PlayerProfile profile) {
        this.profile = profile;
        this.playerUuid = profile != null ? profile.getPlayer().getUniqueId() : null;
        if (profile != null && CheckManager.classCheck(this.getClass())) {
            this.localCfg = CheckManager.getConfig(this.getClass());
        }
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("threshold", 0.85);
        return new ConfigLabel("onnx_baseline", localCfg);
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

        if (o instanceof RotationEvent) {
            RotationEvent event = (RotationEvent) o;
            if (profile.isIgnoreFirstTick() || profile.ignoreCinematic()) return;

            long now = System.currentTimeMillis();
            if (now - profile.getLastTeleport() < 500) return;

            float yawDelta = event.getDelta().getX();
            float pitchDelta = event.getDelta().getY();

            if (CloudClientService.isConnected()) {
                CloudClientService.sendRotation(playerUuid.toString(), yawDelta, pitchDelta);
            }
        }

        if (o instanceof MoveEvent) {
            MoveEvent event = (MoveEvent) o;
            if (profile.isIgnoreFirstTick()) return;
            latestHSpeed = event.getDelta().speed();
        }

        if (o instanceof UseEntityEvent) {
            UseEntityEvent event = (UseEntityEvent) o;
            if (event.isAttack() && !event.isCancelled()) {
                long now = System.currentTimeMillis();
                if (lastAttackTime > 0) {
                    latestClickInterval = (float) (now - lastAttackTime);
                }
                lastAttackTime = now;
                if (CloudClientService.isConnected()) {
                    CloudClientService.sendAttack(playerUuid.toString());
                }
            }
        }
    }

    public void onCloudVerdict(CheckVerdict verdict) {
        if (!verdict.getCheckType().startsWith("onnx_baseline")) return;
        double prob = verdict.getScore();
        String label = verdict.getCheckType().contains("/")
                ? verdict.getCheckType().substring(verdict.getCheckType().indexOf('/') + 1)
                : "unknown";
        float threshold = ((Number) localCfg.get("threshold")).floatValue();

        boolean isCheating = prob >= threshold;

        if (isCheating) {
            cleanTicks = 0;
            episodeTicks++;

            if (!inEpisode) {
                if (episodeTicks >= 2) {
                    inEpisode = true;
                    episodeDetections = 1;
                    episodePeakProb = prob;
                    episodeLabel = label;
                    sendLiveUpdate(prob);
                }
            } else {
                episodeDetections++;
                if (prob > episodePeakProb) episodePeakProb = prob;
                double vlAdd = (prob - threshold) * 10.0 + 2.0;
                profile.setVl(profile.getVl() + (float) vlAdd);

                if (episodeDetections % THROTTLE_INTERVAL == 0) {
                    sendLiveUpdate(prob);
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
            .replace("%check%", "OnnxBaseline")
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
            + ChatColor.YELLOW + "Check: " + ChatColor.RED + "OnnxBaseline (Cloud ML)\n"
            + ChatColor.GRAY + "(ongoing)";

        TextComponent component = new TextComponent(MessageUtils.wrapColors(alertMsg));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hoverText).create()));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(win.ac.x.X.permission)) {
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
            .replace("%check%", "OnnxBaseline")
            .replace("%component%", episodeLabel)
            .replace("%info%", "FINAL x" + episodeDetections + " peak " + peakStr + "%")
            .replace("%vl%", String.valueOf((int) profile.getVl()))
            .replace("%vlLimit%", String.valueOf((int) ConfigCache.VL_LIMIT));

        String hoverText = ChatColor.YELLOW + "Label: " + ChatColor.RED + episodeLabel + "\n"
            + ChatColor.YELLOW + "Total Detections: " + ChatColor.RED + episodeDetections + "\n"
            + ChatColor.YELLOW + "Peak Probability: " + ChatColor.RED + peakStr + "%\n"
            + ChatColor.YELLOW + "Duration: " + ChatColor.RED + durationStr + "s (" + episodeTicks + " ticks)\n"
            + ChatColor.YELLOW + "Threshold: " + ChatColor.RED + CONVICTION_THRESHOLD + "\n"
            + ChatColor.YELLOW + "Check: " + ChatColor.RED + "OnnxBaseline (Cloud ML)\n"
            + ChatColor.GREEN + "(episode ended)";

        TextComponent component = new TextComponent(MessageUtils.wrapColors(alertMsg));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hoverText).create()));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(win.ac.x.X.permission)) {
                p.spigot().sendMessage(component);
            }
        }

        profile.punish("OnnxBaseline", episodeLabel,
            "x" + episodeDetections + " detections, peak=" + peakStr + "%",
            Math.max(0.1f, (float)(episodePeakProb - CONVICTION_THRESHOLD) * 5f));

        episodeDetections = 0;
        episodePeakProb = 0.0;
        episodeTicks = 0;
        episodeLabel = "";
        cleanTicks = 0;
    }
}
