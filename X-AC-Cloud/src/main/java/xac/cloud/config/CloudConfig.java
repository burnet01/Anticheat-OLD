package xac.cloud.config;

import com.google.gson.*;
import win.ac.x.ml.logic.Logger;

import java.io.*;
import java.nio.file.*;

public class CloudConfig {

    private int port = 50051;
    private int maxPlayersPerServer = 2000;
    private int aimRotationBuffer = 600;
    private int rnnRotationBuffer = 150;
    private int onnxBaselineBuffer = 40;
    private int onnxPhysicsBuffer = 20;
    private int maxConcurrentStreams = 10000;
    private double aimUnusualThreshold = 0.3;
    private double aimStrangeThreshold = 0.5;
    private double aimSuspectedThreshold = 0.7;
    private double onnxThreshold = 0.85;
    private int inferenceThreads = 4;
    private String modelsDirectory = "models";
    private boolean loadModelsFromResources = false;

    public static CloudConfig load(String path) {
        CloudConfig config = new CloudConfig();
        File file = new File(path);
        if (!file.exists()) {
            config.save(path);
            Logger.info("Created default config: " + path);
            return config;
        }
        try {
            String json = Files.readString(file.toPath());
            Gson gson = new Gson();
            CloudConfig loaded = gson.fromJson(json, CloudConfig.class);
            if (loaded != null) config = loaded;
            Logger.info("Config loaded: " + path);
        } catch (Exception e) {
            Logger.error("Failed to load config: " + e.getMessage());
        }
        return config;
    }

    public void save(String path) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(this);
            Files.writeString(Path.of(path), json);
        } catch (IOException e) {
            Logger.error("Failed to save config: " + e.getMessage());
        }
    }

    public int getPort() { return port; }
    public int getMaxPlayersPerServer() { return maxPlayersPerServer; }
    public int getAimRotationBuffer() { return aimRotationBuffer; }
    public int getRnnRotationBuffer() { return rnnRotationBuffer; }
    public int getOnnxBaselineBuffer() { return onnxBaselineBuffer; }
    public int getOnnxPhysicsBuffer() { return onnxPhysicsBuffer; }
    public int getMaxConcurrentStreams() { return maxConcurrentStreams; }
    public double getAimUnusualThreshold() { return aimUnusualThreshold; }
    public double getAimStrangeThreshold() { return aimStrangeThreshold; }
    public double getAimSuspectedThreshold() { return aimSuspectedThreshold; }
    public double getOnnxThreshold() { return onnxThreshold; }
    public int getInferenceThreads() { return inferenceThreads; }
    public String getModelsDirectory() { return modelsDirectory; }
    public boolean isLoadModelsFromResources() { return loadModelsFromResources; }
}
