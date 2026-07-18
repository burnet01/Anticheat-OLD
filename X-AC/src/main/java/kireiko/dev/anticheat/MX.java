package kireiko.dev.anticheat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import kireiko.dev.anticheat.api.data.Metrics;
import kireiko.dev.anticheat.api.data.PlayerContainer;
import kireiko.dev.anticheat.api.player.PlayerProfile;
import kireiko.dev.anticheat.commands.MXCommandHandler;
import kireiko.dev.anticheat.core.AsyncScheduler;
import kireiko.dev.anticheat.listeners.*;
import kireiko.dev.anticheat.managers.CheckManager;
import kireiko.dev.anticheat.services.AnimatedPunishService;
import kireiko.dev.anticheat.services.DatasetRecorder;
import kireiko.dev.anticheat.services.OnnxInferenceService;
import kireiko.dev.anticheat.services.PhysicsSimulationService;
import kireiko.dev.anticheat.services.SimulationFlagService;
import kireiko.dev.anticheat.utils.ConfigCache;
import kireiko.dev.millennium.ml.ClientML;
import kireiko.dev.millennium.types.EvictingList;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MX extends JavaPlugin {

    public static final String
            command = "mx",
            name = "MX",
            permissionHead = "mx.",
            permission = permissionHead + "admin";
    public static int bannedPerMinuteCount = 0;
    public static List<Integer> bannedPerMinuteList = new EvictingList<>(60);
    public static int blockedPerMinuteCount = 0;
    public static List<Integer> blockedPerMinuteList = new EvictingList<>(60);
    @Getter
    private static MX instance;

    @Override
    public void onLoad() {
        PacketEvents.getAPI().getSettings().reEncodeByDefault(true)
                .checkForUpdates(false)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        CheckManager.init();
        saveDefaultConfig();
        ConfigCache.loadConfig();
        kireiko.dev.anticheat.managers.DatasetManager.init();

        getLogger().info("Loading listeners...");
        loadListeners();
        getLogger().info("Booting timers...");
        punishTimer();
        getLogger().info("Initializing commands...");
        PluginCommand pCommand = this.getCommand(command);
        if (pCommand != null) {
            MXCommandHandler handler = new MXCommandHandler();
            pCommand.setExecutor(handler);
            pCommand.setTabCompleter(handler);
        }
        getLogger().info("Running metrics...");
        final Metrics metrics = new Metrics(this, 25612);
        metrics.addCustomChart(new Metrics.SingleLineChart("banned_players_count", () -> {
            int banCount = 0;
            for (int i : MX.bannedPerMinuteList) banCount += i;
            return banCount;
        }));
        getLogger().info("Initializing dataset recorder...");
        DatasetRecorder.init();
        getLogger().info("Loading ONNX models...");
        OnnxInferenceService.loadAll();
        getLogger().info("Launching ML (Kireiko Millennium 5)...");
        ClientML.run();
        getLogger().info("Launched!\n"
                        + "        :::   :::       :::    :::\n" +
                        "      :+:+: :+:+:      :+:    :+:\n" +
                        "    +:+ +:+:+ +:+      +:+  +:+  \n" +
                        "   +#+  +:+  +#+       +#++:+\n" +
                        "  +#+       +#+      +#+  +#+\n" +
                        " #+#       #+#     #+#    #+#\n" +
                        "###       ###     ###    ###\n" +
                        "\nCreated by pawsashatoy (Kireiko Oleksandr)\n"
                        );
    }

    private void punishTimer() {
        AnimatedPunishService.init();
        SimulationFlagService.init();
        PhysicsSimulationService.init();
        //CrasherShieldNewListener.watchdog();

        // reset vl
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            float r = ConfigCache.VL_RESET;
            bannedPerMinuteList.add(bannedPerMinuteCount);
            bannedPerMinuteCount = 0;
            blockedPerMinuteList.add(blockedPerMinuteCount);
            blockedPerMinuteCount = 0;
            for (PlayerProfile profile : PlayerContainer.getUuidPlayerProfileMap().values()) {
                profile.fade(r);
                profile.setFlagCount(0);
            }
        }, 20L, 1200L);
    }

    private void loadListeners() {
        //Bukkit.getPluginManager().registerEvents(new GhostBlockTest(), this);
        Bukkit.getPluginManager().registerEvents(new JoinQuitListener(), this);
        EntityTrackerListener.register();

        PacketEvents.getAPI().getEventManager().registerListener(new RawMovementListener(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager().registerListener(new UseEntityListener(), PacketListenerPriority.HIGHEST);
        PacketEvents.getAPI().getEventManager().registerListener(new LatencyHandler(), PacketListenerPriority.MONITOR);
        PacketEvents.getAPI().getEventManager().registerListener(new VelocityListener(), PacketListenerPriority.MONITOR);
        PacketEvents.getAPI().getEventManager().registerListener(new EntityActionListener(), PacketListenerPriority.HIGHEST);
        PacketEvents.getAPI().getEventManager().registerListener(new VehicleTeleportListener(), PacketListenerPriority.HIGHEST);
        PacketEvents.getAPI().getEventManager().registerListener(new OmniPacketListener(), PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onDisable() {
        AsyncScheduler.shutdown();
        PacketEvents.getAPI().terminate();
    }

}