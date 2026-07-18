package win.ac.x.checks.v2.combat.killaura;

import win.ac.x.api.PacketCheckHandler;
import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.api.events.CPacketEvent;
import win.ac.x.api.events.UseEntityEvent;
import win.ac.x.managers.CheckManager;
import win.ac.x.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.*;

public final class KillAuraHV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final int SAMPLE_SIZE = 15;
    private static final double VARIANCE_THRESHOLD = 25.0;
    private static final double QUANTIZATION_THRESHOLD = 0.85;

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private long lastAttackTime = 0;
    private final Deque<Long> intervals = new ArrayDeque<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 15);
        return new ConfigLabel("v2_killaura_h", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraHV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        boolean isAttack = false;
        if (o instanceof UseEntityEvent) {
            isAttack = ((UseEntityEvent) o).isAttack();
        } else if (o instanceof CPacketEvent) {
            CPacketEvent e = (CPacketEvent) o;
            if (e.getPacketEvent().getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(e.getPacketEvent());
                isAttack = interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
            }
        }
        if (!isAttack) return;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();

        if (lastAttackTime > 0) {
            long interval = now - lastAttackTime;

            if (interval >= 50 && interval <= 500) {
                intervals.addLast(interval);
                while (intervals.size() > SAMPLE_SIZE) {
                    intervals.pollFirst();
                }
            }
        }
        lastAttackTime = now;

        if (intervals.size() < SAMPLE_SIZE) return;

        double variance = getVariance();
        boolean lowVariance = variance < VARIANCE_THRESHOLD;

        double quantizationScore = getQuantizationScore();
        boolean quantized = quantizationScore > QUANTIZATION_THRESHOLD;

        double avgInterval = getAverage();
        double cps = 1000.0 / avgInterval;
        boolean perfectCps = Math.abs(cps - Math.round(cps)) < 0.05;

        int suspicionLevel = 0;
        if (lowVariance) suspicionLevel++;
        if (quantized) suspicionLevel++;
        if (perfectCps && cps >= 10) suspicionLevel++;

        if (suspicionLevel >= 2) {
            double severity = suspicionLevel * 1.0;
            if (buffer.increase(uuid, severity) > 10.0) {
                profile.punish("KillAura", "H", String.format("Attack Pattern. Var: %.1f, Quant: %.2f, CPS: %.1f",
                        variance, quantizationScore, cps), 1.0f);
                buffer.reset(uuid, 5.0);
            }
        } else {
            buffer.decrease(uuid, 0.4);
        }
    }

    private double getAverage() {
        long sum = 0;
        for (Long interval : intervals) sum += interval;
        return intervals.isEmpty() ? 0.0 : (double) sum / intervals.size();
    }

    private double getVariance() {
        double mean = getAverage();
        double sum = 0.0;
        for (Long interval : intervals) {
            double diff = interval - mean;
            sum += diff * diff;
        }
        return intervals.isEmpty() ? 0.0 : sum / intervals.size();
    }

    private double getQuantizationScore() {
        int quantized = 0;
        for (Long interval : intervals) {
            if (interval % 50 < 5 || interval % 50 > 45) {
                quantized++;
            }
        }
        return intervals.isEmpty() ? 0.0 : (double) quantized / intervals.size();
    }
}