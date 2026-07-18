package win.ac.x;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.data.PunishmentHandler;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXCommandHandler;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.listeners.*;
import win.ac.x.managers.CheckManager;
import win.ac.x.services.AnimatedPunishService;
import win.ac.x.services.DatasetRecorder;
import win.ac.x.services.OnnxInferenceService;
import win.ac.x.services.PhysicsSimulationService;
import win.ac.x.services.SimulationFlagService;
import win.ac.x.utils.ConfigCache;
import win.ac.x.ml.ClientML;
import win.ac.x.types.EvictingList;
import lombok.Getter;
import lombok.Setter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public final class XMinestom {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMinestom.class);

    public static final String
            command = "mx",
            name = "MX";
    private static PunishmentHandler punishmentHandler = (profile, check, component, info, vl) ->
            LOGGER.info("Punish: {} {} {} (vl={})", check, component, info, vl);
    public static int bannedPerMinuteCount = 0;
    public static List<Integer> bannedPerMinuteList = new EvictingList<>(60);
    public static int blockedPerMinuteCount = 0;
    public static List<Integer> blockedPerMinuteList = new EvictingList<>(60);

    @Getter @Setter
    private static Path dataFolder = Path.of("plugins", "MX-Minestom");

    public static void setPunishmentHandler(PunishmentHandler handler) {
        punishmentHandler = handler;
    }

    public static PunishmentHandler getPunishmentHandler() {
        return punishmentHandler;
    }

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        CheckManager.init();
        ConfigCache.loadConfig();
        win.ac.x.managers.DatasetManager.init();

        LOGGER.info("Loading listeners...");
        loadListeners();
        LOGGER.info("Booting timers...");
        punishTimer();
        LOGGER.info("Initializing commands...");
        MinecraftServer.getCommandManager().register(new MXCommandHandler());
        LOGGER.info("Initializing dataset recorder...");
        DatasetRecorder.init();
        LOGGER.info("Loading ONNX models...");
        OnnxInferenceService.loadAll();
        LOGGER.info("Launching ML (Kireiko Millennium 5)...");
        ClientML.run();
        LOGGER.info("Launched!\n"
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

    private static void punishTimer() {
        AnimatedPunishService.init();
        SimulationFlagService.init();
        PhysicsSimulationService.init();

        MinecraftServer.getSchedulerManager().submitTask(() -> {
            float r = ConfigCache.VL_RESET;
            bannedPerMinuteList.add(bannedPerMinuteCount);
            bannedPerMinuteCount = 0;
            blockedPerMinuteList.add(blockedPerMinuteCount);
            blockedPerMinuteCount = 0;
            for (PlayerProfile profile : PlayerContainer.getUuidPlayerProfileMap().values()) {
                profile.fade(r);
                profile.setFlagCount(0);
            }
            return TaskSchedule.tick(1200);
        });
    }

    private static void loadListeners() {
        var handler = MinecraftServer.getGlobalEventHandler();

        handler.addListener(AsyncPlayerConfigurationEvent.class, (event) -> {
            PlayerContainer.init(event.getPlayer());
        });

        handler.addListener(PlayerDisconnectEvent.class, (event) -> {
            PlayerContainer.unload(event.getPlayer());
        });

        handler.addListener(PlayerPacketEvent.class, (java.util.function.Consumer<PlayerPacketEvent>) event -> {
            RawMovementListener.onPacketReceive(event);
        });
        handler.addListener(PlayerPacketEvent.class, new UseEntityListener());
        handler.addListener(PlayerPacketEvent.class, new EntityActionListener());
        handler.addListener(PlayerPacketEvent.class, new VehicleTeleportListener());
        handler.addListener(PlayerPacketEvent.class, new OmniPacketListener());
        handler.addListener(PlayerPacketEvent.class, new InventoryListener());
        handler.addListener(PlayerPacketEvent.class, (java.util.function.Consumer<PlayerPacketEvent>) event -> {
            LatencyHandler.onPacketReceive(event);
        });

        handler.addListener(PlayerPacketOutEvent.class, new VelocityListener());
        handler.addListener(PlayerPacketOutEvent.class, (java.util.function.Consumer<PlayerPacketOutEvent>) event -> {
            LatencyHandler.onPacketSend(event);
        });
        handler.addListener(PlayerPacketOutEvent.class, (java.util.function.Consumer<PlayerPacketOutEvent>) event -> {
            RawMovementListener.onPacketSend(event);
        });

        EntityTrackerListener.register();
    }

    public static void shutdown() {
        AsyncScheduler.shutdown();
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}