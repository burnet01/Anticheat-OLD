package xac.cloud;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import xac.cloud.config.CloudConfig;
import xac.cloud.engine.MillenniumEngine;
import xac.cloud.engine.OnnxEngine;
import win.ac.x.ml.logic.Logger;
import xac.cloud.service.MLInferenceServiceImpl;
import xac.cloud.session.PlayerSessionManager;

import java.io.File;
import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class XacCloudServer {

    private Server server;
    private final CloudConfig config;
    private final PlayerSessionManager sessionManager;
    private final MillenniumEngine millenniumEngine;
    private final OnnxEngine onnxEngine;

    public XacCloudServer(CloudConfig config) {
        this.config = config;
        this.sessionManager = new PlayerSessionManager(
                config.getAimRotationBuffer(),
                config.getRnnRotationBuffer()
        );
        this.millenniumEngine = new MillenniumEngine(2);
        this.onnxEngine = new OnnxEngine(config.getModelsDirectory());
    }

    public void start() throws Exception {
        Logger.info("===================================");
        Logger.info("  X-AC Cloud Inference Service");
        Logger.info("===================================");

        copyDefaultModels();

        Logger.info("Loading Millennium 5 ML engine...");
        millenniumEngine.loadModels(config.getModelsDirectory());
        Logger.info("Millennium engine: " + millenniumEngine.getModuleCount() + " modules");

        Logger.info("Loading ONNX models...");
        onnxEngine.loadAll();

        Logger.info("Starting gRPC server on port " + config.getPort() + "...");
        server = NettyServerBuilder.forPort(config.getPort())
                .maxConcurrentCallsPerConnection(config.getMaxConcurrentStreams())
                .maxInboundMessageSize(64 * 1024) // 64KB
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .permitKeepAliveTime(5, TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(true)
                .addService(new MLInferenceServiceImpl(
                        sessionManager, millenniumEngine, onnxEngine, config
                ))
                .build()
                .start();

        Logger.info("Server started on port " + config.getPort());
        Logger.info("Ready to accept connections.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down...");
            XacCloudServer.this.stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        onnxEngine.close();
        sessionManager.cleanup();
        Logger.info("Server stopped.");
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private void copyDefaultModels() {
        File modelDir = new File(config.getModelsDirectory());
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        String[] defaultModels = {
                "m1.dat", "m2.dat", "m3.dat", "m4.dat", "m5.dat",
                "m_huge1.dat", "m_huge2.dat", "m1-rnn.dat"
        };

        for (String modelName : defaultModels) {
            File target = new File(modelDir, modelName);
            if (!target.exists()) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("models/" + modelName)) {
                    if (is != null) {
                        Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        Logger.info("Copied default model: " + modelName);
                    }
                } catch (Exception e) {
                    Logger.warn("Default model not found in resources: " + modelName);
                }
            }
        }
    }

    public static void main(String[] args) {
        String configPath = "config/config.json";
        if (args.length > 0) {
            configPath = args[0];
        }

        CloudConfig config = CloudConfig.load(configPath);
        XacCloudServer server = new XacCloudServer(config);

        try {
            server.start();
            server.blockUntilShutdown();
        } catch (Exception e) {
            Logger.error("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}