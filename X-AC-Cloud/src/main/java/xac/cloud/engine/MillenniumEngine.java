package xac.cloud.engine;

import win.ac.x.ml.data.ObjectML;
import win.ac.x.ml.data.ResultML;
import win.ac.x.ml.data.module.FlagType;
import win.ac.x.ml.data.module.ModuleML;
import win.ac.x.ml.data.module.ModuleResultML;
import win.ac.x.ml.logic.Logger;
import win.ac.x.ml.logic.Millennium;
import win.ac.x.vectors.Vec2f;
import xac.cloud.ModelLoader;

import java.util.*;

public class MillenniumEngine {

    private final List<ModuleML> modules;
    private final int tableSize;
    private boolean loaded = false;

    public MillenniumEngine(int tableSize) {
        this.modules = ModelLoader.createModuleList();
        this.tableSize = tableSize;
    }

    public void loadModels(String modelsDir) {
        ModelLoader.loadAllModels(modelsDir, modules, tableSize);
        loaded = ModelLoader.cacheSize() > 0;
        if (!loaded) {
            Logger.warn("No models loaded. Millennium engine will return NORMAL for all checks.");
        } else {
            Logger.info("Millennium engine ready with " + ModelLoader.cacheSize() + " models.");
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public MillenniumResult runLegacyCheck(List<Vec2f> rotations) {
        if (!loaded || rotations.isEmpty()) {
            return new MillenniumResult(FlagType.NORMAL, 0, "no_models", Collections.emptySet());
        }

        List<ObjectML> objectMLStack = buildObjectMLStack(rotations);

        ModuleResultML finalModuleResult = new ModuleResultML(0, FlagType.NORMAL, null);
        Set<String> modelsThatFlagged = new HashSet<>();

        for (int i = 0; i < 7 && i < modules.size(); i++) {
            Millennium model = ModelLoader.getModel(i);
            if (model == null) continue;
            ResultML resultML = model.checkData(objectMLStack);
            ModuleML module = modules.get(i);
            ModuleResultML moduleResult = module.getResult(resultML);

            if (moduleResult.getType() != FlagType.NORMAL) {
                modelsThatFlagged.add(module.getName());
            }

            if (finalModuleResult.getInfo() == null) {
                finalModuleResult = moduleResult;
            } else {
                int finalLevel = finalModuleResult.getType().getLevel();
                int tempLevel = moduleResult.getType().getLevel();
                if (finalLevel < tempLevel ||
                        (finalLevel == tempLevel && finalModuleResult.getPriority() < moduleResult.getPriority())) {
                    finalModuleResult = moduleResult;
                }
            }
        }

        return new MillenniumResult(
                finalModuleResult.getType(),
                finalModuleResult.getPriority(),
                finalModuleResult.getInfo(),
                modelsThatFlagged
        );
    }

    public MillenniumResult runRNNCheck(List<Vec2f> rotations) {
        if (!loaded || rotations.isEmpty()) {
            return new MillenniumResult(FlagType.NORMAL, 0, "no_models", Collections.emptySet());
        }

        List<ObjectML> objectMLStack = buildObjectMLStack(rotations);
        ModuleResultML finalModuleResult = new ModuleResultML(0, FlagType.NORMAL, null);
        Set<String> modelsThatFlagged = new HashSet<>();

        int rnnIndex = 7;
        if (modules.size() > rnnIndex && ModelLoader.getModel(rnnIndex) != null) {
            ResultML resultML = ModelLoader.getModel(rnnIndex).checkData(objectMLStack);
            ModuleML module = modules.get(rnnIndex);
            ModuleResultML moduleResult = module.getResult(resultML);

            if (moduleResult.getType() != FlagType.NORMAL) {
                modelsThatFlagged.add(module.getName());
            }
            finalModuleResult = moduleResult;
        }

        return new MillenniumResult(
                finalModuleResult.getType(),
                finalModuleResult.getPriority(),
                finalModuleResult.getInfo(),
                modelsThatFlagged
        );
    }

    private List<ObjectML> buildObjectMLStack(List<Vec2f> rotations) {
        List<ObjectML> stack = new ArrayList<>();
        ObjectML yaw = new ObjectML(new ArrayList<>());
        ObjectML pitch = new ObjectML(new ArrayList<>());
        for (Vec2f rot : rotations) {
            yaw.getValues().add((double) rot.getX());
            pitch.getValues().add((double) rot.getY());
        }
        stack.add(yaw);
        stack.add(pitch);
        return stack;
    }

    public int getModuleCount() {
        return modules.size();
    }

    public static class MillenniumResult {
        private final FlagType type;
        private final int priority;
        private final String info;
        private final Set<String> modelsThatFlagged;

        public MillenniumResult(FlagType type, int priority, String info, Set<String> modelsThatFlagged) {
            this.type = type;
            this.priority = priority;
            this.info = info;
            this.modelsThatFlagged = modelsThatFlagged;
        }

        public FlagType getType() { return type; }
        public int getPriority() { return priority; }
        public String getInfo() { return info; }
        public Set<String> getModelsThatFlagged() { return modelsThatFlagged; }
        public boolean isFlagged() { return type != FlagType.NORMAL; }

        public String getVerdictString() {
            switch (type) {
                case SUSPECTED: return "SUSPECTED";
                case STRANGE: return "STRANGE";
                case UNUSUAL: return "UNUSUAL";
                default: return "CLEAN";
            }
        }

        public float getScore() {
            switch (type) {
                case SUSPECTED: return 0.9f;
                case STRANGE: return 0.7f;
                case UNUSUAL: return 0.5f;
                default: return 0.0f;
            }
        }
    }
}