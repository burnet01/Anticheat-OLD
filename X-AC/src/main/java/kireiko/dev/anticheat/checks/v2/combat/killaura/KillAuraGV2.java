package kireiko.dev.anticheat.checks.v2.combat.killaura;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.events.UseEntityEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import java.util.*;

public final class KillAuraGV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private static final int MAX_TARGETS_WINDOW = 10;
    private static final long TIME_WINDOW_MS = 500L;
    private static final int SUSPICIOUS_TARGET_COUNT = 3;

    private final CheckBufferV2 buffer = new CheckBufferV2();
    private final Deque<TargetEntry> entries = new ArrayDeque<>();

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 12);
        return new ConfigLabel("v2_killaura_g", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraGV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof UseEntityEvent) {
            UseEntityEvent ue = (UseEntityEvent) o;
            if (!ue.isAttack()) return;
            if (profile == null) return;

            UUID uuid = profile.getPlayer().getUniqueId();
            int targetId = ue.getEntityId();
            long now = System.currentTimeMillis();

            entries.addLast(new TargetEntry(targetId, now));
            while (entries.size() > MAX_TARGETS_WINDOW) {
                entries.pollFirst();
            }

            int uniqueTargets = countUniqueTargetsInWindow(now, TIME_WINDOW_MS);
            long fastestSwitch = getFastestSwitchMs(now, TIME_WINDOW_MS);

            if (uniqueTargets >= SUSPICIOUS_TARGET_COUNT) {
                double severity = (uniqueTargets - 2) * 1.5;

                if (fastestSwitch > 0 && fastestSwitch < 50) {
                    severity += 2.0;
                }

                if (buffer.increase(uuid, severity) > 8.0) {
                    profile.punish("KillAura", "G", String.format("Multi-Target. Targets: %d in %dms, FastestSwitch: %dms",
                            uniqueTargets, TIME_WINDOW_MS, fastestSwitch), 1.0f);
                    buffer.reset(uuid, 4.0);
                }
            } else {
                buffer.decrease(uuid, 0.3);
            }
            return;
        }
        if (o instanceof CPacketEvent) {
            CPacketEvent e = (CPacketEvent) o;
            if (e.getPacketEvent().getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.INTERACT_ENTITY) {
                com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity interact =
                        new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity(e.getPacketEvent());
                if (interact.getAction() != com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
                if (profile == null) return;

                UUID uuid = profile.getPlayer().getUniqueId();
                int targetId = interact.getEntityId();
                long now = System.currentTimeMillis();

                entries.addLast(new TargetEntry(targetId, now));
                while (entries.size() > MAX_TARGETS_WINDOW) {
                    entries.pollFirst();
                }

                int uniqueTargets = countUniqueTargetsInWindow(now, TIME_WINDOW_MS);
                long fastestSwitch = getFastestSwitchMs(now, TIME_WINDOW_MS);

                if (uniqueTargets >= SUSPICIOUS_TARGET_COUNT) {
                    double severity = (uniqueTargets - 2) * 1.5;

                    if (fastestSwitch > 0 && fastestSwitch < 50) {
                        severity += 2.0;
                    }

                    if (buffer.increase(uuid, severity) > 8.0) {
                        profile.punish("KillAura", "G", String.format("Multi-Target. Targets: %d in %dms, FastestSwitch: %dms",
                                uniqueTargets, TIME_WINDOW_MS, fastestSwitch), 1.0f);
                        buffer.reset(uuid, 4.0);
                    }
                } else {
                    buffer.decrease(uuid, 0.3);
                }
            }
        }
    }

    private int countUniqueTargetsInWindow(long now, long windowMs) {
        Set<Integer> unique = new HashSet<>();
        for (TargetEntry entry : entries) {
            if (now - entry.timestamp <= windowMs) {
                unique.add(entry.targetId);
            }
        }
        return unique.size();
    }

    private long getFastestSwitchMs(long now, long windowMs) {
        long fastest = Long.MAX_VALUE;
        TargetEntry prev = null;

        for (TargetEntry entry : entries) {
            if (now - entry.timestamp > windowMs) continue;

            if (prev != null && prev.targetId != entry.targetId) {
                long switchTime = entry.timestamp - prev.timestamp;
                if (switchTime < fastest) {
                    fastest = switchTime;
                }
            }
            prev = entry;
        }

        return fastest == Long.MAX_VALUE ? -1 : fastest;
    }

    private static class TargetEntry {
        final int targetId;
        final long timestamp;

        TargetEntry(int targetId, long timestamp) {
            this.targetId = targetId;
            this.timestamp = timestamp;
        }
    }
}