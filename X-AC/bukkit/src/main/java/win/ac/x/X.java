package win.ac.x;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import win.ac.x.api.data.Metrics;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXCommandHandler;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.listeners.*;
import win.ac.x.managers.CheckManager;
import win.ac.x.services.AnimatedPunishService;
import win.ac.x.services.CloudClientService;
import win.ac.x.services.DatasetRecorder;
import win.ac.x.services.PhysicsSimulationService;
import win.ac.x.services.SimulationFlagService;
import win.ac.x.services.VerdictDispatcher;
import win.ac.x.utils.ConfigCache;
import win.ac.x.types.EvictingList;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class X extends JavaPlugin {

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
    private static X instance;

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
        win.ac.x.managers.DatasetManager.init();

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
            for (int i : X.bannedPerMinuteList) banCount += i;
            return banCount;
        }));
        getLogger().info("Initializing dataset recorder...");
        DatasetRecorder.init();
        getLogger().info("Connecting to X-AC Cloud... (all ML runs remotely)");
        CloudClientService.init(
                getConfig().getString("cloud.host", "127.0.0.1"),
                getConfig().getInt("cloud.port", 50051),
                getConfig().getString("cloud.server-id", "default")
        );
        VerdictDispatcher.init();
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