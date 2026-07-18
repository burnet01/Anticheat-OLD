package kireiko.dev.anticheat.managers;

import kireiko.dev.anticheat.MX;
import kireiko.dev.anticheat.api.PacketCheckHandler;
import kireiko.dev.anticheat.api.data.ConfigLabel;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.checks.aim.AimAnalysisCheck;
import kireiko.dev.anticheat.checks.aim.AimComplexCheck;
import kireiko.dev.anticheat.checks.aim.AimHeuristicCheck;
import kireiko.dev.anticheat.checks.aim.AimStatisticsCheck;
import kireiko.dev.anticheat.checks.aim.OnnxBaselineCheck;
import kireiko.dev.anticheat.checks.aim.ml.AimMLCheck;
import kireiko.dev.anticheat.checks.clicks.AutoClickerCheck;
import kireiko.dev.anticheat.checks.movement.BaritoneCheck;
import kireiko.dev.anticheat.checks.movement.GhostBlockAbuseCheck;
import kireiko.dev.anticheat.checks.movement.OnnxPhysicsCheck;
import kireiko.dev.anticheat.checks.protocol.SprintCheck;
import kireiko.dev.anticheat.checks.velocity.VelocityCheck;
import kireiko.dev.anticheat.checks.v2.bedrock.BFlyAV2;
import kireiko.dev.anticheat.checks.v2.bedrock.BReachAV2;
import kireiko.dev.anticheat.checks.v2.bedrock.BSpeedAV2;
import kireiko.dev.anticheat.checks.v2.combat.anchor.AnchorAuraAV2;
import kireiko.dev.anticheat.checks.v2.combat.autoclicker.AutoClickerAV2;
import kireiko.dev.anticheat.checks.v2.combat.autoclicker.AutoClickerBV2;
import kireiko.dev.anticheat.checks.v2.combat.autoclicker.AutoClickerCV2;
import kireiko.dev.anticheat.checks.v2.combat.autoclicker.AutoClickerDV2;
import kireiko.dev.anticheat.checks.v2.combat.autoclicker.AutoClickerEV2;
import kireiko.dev.anticheat.checks.v2.combat.crystal.CrystalAuraAV2;
import kireiko.dev.anticheat.checks.v2.combat.hitbox.HitboxAV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraBV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraCV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraDV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraEV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraFV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraGV2;
import kireiko.dev.anticheat.checks.v2.combat.killaura.KillAuraHV2;
import kireiko.dev.anticheat.checks.v2.combat.reach.ReachAV2;
import kireiko.dev.anticheat.checks.v2.movement.baritone.BaritoneAV2;
import kireiko.dev.anticheat.checks.v2.movement.baritone.BaritoneBV2;
import kireiko.dev.anticheat.checks.v2.movement.baritone.BaritoneCV2;
import kireiko.dev.anticheat.checks.v2.movement.inventory.InventoryAV2;
import kireiko.dev.anticheat.checks.v2.movement.simulation.SimulationBV2;
import kireiko.dev.anticheat.checks.v2.movement.simulation.SimulationCV2;
import kireiko.dev.anticheat.checks.v2.movement.simulation.SimulationDV2;
import kireiko.dev.anticheat.checks.v2.movement.spoof.GroundSpoofBV2;
import kireiko.dev.anticheat.checks.v2.movement.spoof.GroundSpoofCV2;
import kireiko.dev.anticheat.checks.v2.movement.spoof.GroundSpoofDV2;
import kireiko.dev.anticheat.checks.v2.movement.spoof.GroundSpoofEV2;
import kireiko.dev.anticheat.checks.v2.movement.spoof.GroundSpoofFV2;
import kireiko.dev.anticheat.checks.v2.movement.spoof.GroundSpoofGV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketAV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketCV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketEV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketGV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketHV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketJV2;
import kireiko.dev.anticheat.checks.v2.packet.badpacket.BadPacketKV2;
import kireiko.dev.anticheat.checks.v2.packet.crasher.CrasherAV2;
import kireiko.dev.anticheat.checks.v2.packet.invalid.InvalidAV2;
import kireiko.dev.anticheat.checks.v2.packet.order.PacketOrderAV2;
import kireiko.dev.anticheat.checks.v2.packet.order.PacketOrderBV2;
import kireiko.dev.anticheat.checks.v2.packet.order.PacketOrderCV2;
import kireiko.dev.anticheat.checks.v2.packet.order.PacketOrderDV2;
import kireiko.dev.anticheat.checks.v2.packet.timer.TimerAV2;
import kireiko.dev.anticheat.checks.v2.raycast.RaycastAV2;
import kireiko.dev.anticheat.checks.v2.world.fastbreak.FastBreakAV2;
import kireiko.dev.anticheat.checks.v2.world.phase.PhaseAV2;
import kireiko.dev.anticheat.checks.v2.world.scaffold.ScaffoldAV2;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class CheckManager {
    @Getter
    private Set<Class<? extends PacketCheckHandler>> checks = new HashSet<>();
    @Getter
    private final Map<String, PacketCheckHandler> instances = new ConcurrentHashMap<>();

    static {
        checks.addAll(Arrays.asList(
                        AimHeuristicCheck.class,
                        AimComplexCheck.class,
                        AimAnalysisCheck.class,
                        AimStatisticsCheck.class,
                        AimMLCheck.class,
                        VelocityCheck.class,
                        AutoClickerCheck.class,
                        BaritoneCheck.class,
                        GhostBlockAbuseCheck.class,
                        SprintCheck.class,
                        OnnxPhysicsCheck.class,
                        OnnxBaselineCheck.class,

                        AutoClickerAV2.class,
                        AutoClickerBV2.class,
                        AutoClickerCV2.class,
                        AutoClickerDV2.class,
                        AutoClickerEV2.class,
                        HitboxAV2.class,
                        ReachAV2.class,
                        KillAuraBV2.class,
                        KillAuraCV2.class,
                        KillAuraDV2.class,
                        KillAuraEV2.class,
                        KillAuraFV2.class,
                        KillAuraGV2.class,
                        KillAuraHV2.class,
                        CrystalAuraAV2.class,
                        AnchorAuraAV2.class,

                        SimulationBV2.class,
                        SimulationCV2.class,
                        SimulationDV2.class,
                        GroundSpoofBV2.class,
                        GroundSpoofCV2.class,
                        GroundSpoofDV2.class,
                        GroundSpoofEV2.class,
                        GroundSpoofFV2.class,
                        GroundSpoofGV2.class,
                        InventoryAV2.class,
                        BaritoneAV2.class,
                        BaritoneBV2.class,
                        BaritoneCV2.class,

                        TimerAV2.class,
                        BadPacketAV2.class,
                        BadPacketCV2.class,
                        BadPacketEV2.class,
                        BadPacketGV2.class,
                        BadPacketHV2.class,
                        BadPacketJV2.class,
                        BadPacketKV2.class,
                        CrasherAV2.class,
                        InvalidAV2.class,
                        PacketOrderAV2.class,
                        PacketOrderBV2.class,
                        PacketOrderCV2.class,
                        PacketOrderDV2.class,

                        FastBreakAV2.class,
                        PhaseAV2.class,
                        ScaffoldAV2.class,
                        RaycastAV2.class,

                        BSpeedAV2.class,
                        BFlyAV2.class,
                        BReachAV2.class
        ));
    }

    @SneakyThrows
    public void init() {
        instances.clear();
        JavaPlugin plugin = MX.getInstance();
        File file = new File(plugin.getDataFolder(), "checks.yml");
        YamlConfiguration cfg = file.exists()
                        ? YamlConfiguration.loadConfiguration(file)
                        : new YamlConfiguration();

        for (Class<? extends PacketCheckHandler> handlerClass : checks) {
            PacketCheckHandler check = handlerClass
                            .getConstructor(PlayerProfile.class)
                            .newInstance((Object) null);
            ConfigLabel defaultLabel = check.config();

            String sectionName = defaultLabel.getName();
            Map<String, Object> defaultParams = defaultLabel.getParameters();

            ConfigurationSection section = cfg.getConfigurationSection(sectionName);
            if (section == null) {
                section = cfg.createSection(sectionName);
            }
            for (Map.Entry<String, Object> e : defaultParams.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                if (val instanceof Map) {
                    if (!section.isConfigurationSection(key)) {
                        section.createSection(key, (Map<?, ?>) val);
                    }
                } else {
                    if (!section.contains(key)) {
                        section.set(key, val);
                    }
                }
            }
            Map<String, Object> mergedParams = new HashMap<>();
            for (Map.Entry<String, Object> e : defaultParams.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();

                if (val instanceof Map) {
                    ConfigurationSection sub = section.getConfigurationSection(key);
                    mergedParams.put(key, sub != null
                                    ? sub.getValues(false)
                                    : new TreeMap<>());
                } else {
                    mergedParams.put(key, section.get(key));
                }
            }
            check.applyConfig(mergedParams);
            instances.put(check.getClass().getName(), check);
        }
        cfg.save(file);
    }
    public boolean classCheck(Class<?> clazz) {
        return (CheckManager.getInstances().containsKey(clazz.getName()));
    }
    public Map<String, Object> getConfig(Class<?> clazz) {
        return (CheckManager.getInstances().get(clazz.getName())).getConfig();
    }
}
