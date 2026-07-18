package kireiko.dev.anticheat.checks.v2.combat.killaura;

import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.api.events.CPacketEvent;
import kireiko.dev.anticheat.api.events.MoveEvent;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.checks.v2.util.CheckBufferV2;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class KillAuraFV2 implements PacketCheckHandler {
    private final PlayerProfile profile;
    private Map<String, Object> localCfg = new TreeMap<>();

    private final CheckBufferV2 buffer = new CheckBufferV2();

    private double currentDeltaY = 0;
    private int currentAirTicks = 0;
    private boolean currentGround = false;

    @Override
    public ConfigLabel config() {
        localCfg.put("enabled", true);
        localCfg.put("buffer", 10);
        return new ConfigLabel("v2_killaura_f", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) { localCfg = params; }
    @Override
    public Map<String, Object> getConfig() { return localCfg; }

    public KillAuraFV2(PlayerProfile profile) {
        this.profile = profile;
        if (CheckManager.classCheck(this.getClass()))
            this.localCfg = CheckManager.getConfig(this.getClass());
    }

    @Override
    public void event(Object o) {
        if (o instanceof MoveEvent) {
            MoveEvent me = (MoveEvent) o;
            currentDeltaY = me.getTo().getY() - me.getFrom().getY();
            currentGround = profile.isGround();
            currentAirTicks = profile.getAirTicks();
            return;
        }
        if (!(o instanceof CPacketEvent)) return;
        CPacketEvent e = (CPacketEvent) o;
        if (e.getPacketEvent().getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(e.getPacketEvent());
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (profile == null) return;

        UUID uuid = profile.getPlayer().getUniqueId();
        boolean clientGround = currentGround;
        double realDeltaY = currentDeltaY;

        if (!clientGround) {
            if (currentAirTicks > 3) {
                buffer.decrease(uuid, 0.1);
                return;
            }

            if (realDeltaY > 0 && realDeltaY < 0.2) {
                if (buffer.increase(uuid, 1.5) > 6.0) {
                    profile.punish("KillAura", "F", String.format("Packet Criticals (Mini-Jump). Y: %.4f, AirTicks: %d", realDeltaY, currentAirTicks), 1.0f);
                    buffer.reset(uuid, 3.0);
                }
            }
        }
    }
}