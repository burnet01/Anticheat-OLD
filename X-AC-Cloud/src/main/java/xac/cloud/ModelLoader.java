package xac.cloud;

import win.ac.x.ml.data.module.ModuleML;
import win.ac.x.ml.logic.Logger;
import win.ac.x.ml.logic.Millennium;
import win.ac.x.ml.logic.ModelML;
import win.ac.x.ml.logic.ModelVer;
import win.ac.x.ml.logic.RNNModelML;
import xac.cloud.modules.v4_5.*;
import xac.cloud.modules.v5.RNN1Module;

import java.io.*;
import java.util.*;

public class ModelLoader {

    private static final Map<Integer, Millennium> CACHE = new LinkedHashMap<>();

    public static List<ModuleML> createModuleList() {
        return Arrays.asList(
                new M1Module(), new M2Module(), new M3Module(),
                new M4Module(), new M5Module(), new MHuge1Module(),
                new MHuge2Module(), new RNN1Module()
        );
    }

    public static Millennium getModel(int id) {
        return CACHE.get(id);
    }

    public static void removeModel(int id) {
        CACHE.remove(id);
    }

    public static void loadAllModels(String modelsDir, List<ModuleML> modules, int tableSize) {
        long totalWeights = 0;
        File dir = new File(modelsDir);
        if (!dir.exists()) dir.mkdirs();
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), modelsDir);
        }

        for (int i = 0; i < modules.size(); i++) {
            ModuleML module = modules.get(i);
            String name = module.getName() + ".dat";
            File file = new File(dir, name);
            Millennium model = null;

            if (module.getVersion() == ModelVer.VERSION_5) {
                RNNModelML m = new RNNModelML(16, 48);
                if (file.exists()) {
                    try (InputStream in = new FileInputStream(file)) {
                        m.load(in);
                        Logger.info("Model loaded: " + file.getAbsolutePath());
                        model = m;
                    } catch (Exception e) {
                        Logger.error("Bad v5 model file " + name + ": " + e.getMessage());
                    }
                } else {
                    Logger.warn("Model " + name + " not found at " + file.getAbsolutePath());
                }
            } else {
                if (file.exists()) {
                    try (ObjectInputStream ois = createObjectInputStream(new FileInputStream(file))) {
                        model = (Millennium) ois.readObject();
                        Logger.info("Model loaded: " + file.getAbsolutePath());
                    } catch (Exception e) {
                        Logger.error("Failed to deserialize " + name + ": " + e.getMessage());
                    }
                }
                if (model == null) {
                    Logger.warn("Creating fresh model for " + name + " (no pretrained weights)");
                    model = new ModelML(tableSize, module.getParameterBuffer());
                }
            }

            if (model != null) {
                CACHE.put(i, model);
                totalWeights += model.parameters();
            }
        }
        Logger.info("All models loaded! Total weights: " + String.format("%,d", totalWeights));
    }

    private static ObjectInputStream createObjectInputStream(InputStream in) throws IOException {
        return new ObjectInputStream(in) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                String name = desc.getName();
                if (name.equals("kireiko.dev.millennium.ml.logic.ModelML")) {
                    return ModelML.class;
                }
                if (name.equals("kireiko.dev.millennium.ml.data.DataML")) {
                    return win.ac.x.ml.data.DataML.class;
                }
                if (name.equals("kireiko.dev.millennium.ml.data.ObjectML")) {
                    return win.ac.x.ml.data.ObjectML.class;
                }
                if (name.equals("kireiko.dev.millennium.ml.data.ResultML")) {
                    return win.ac.x.ml.data.ResultML.class;
                }
                if (name.equals("kireiko.dev.millennium.ml.data.statistic.StatisticML")) {
                    return win.ac.x.ml.data.statistic.StatisticML.class;
                }
                if (name.equals("kireiko.dev.millennium.ml.data.statistic.StatisticPattern")) {
                    return win.ac.x.ml.data.statistic.StatisticPattern.class;
                }
                return super.resolveClass(desc);
            }
        };
    }

    public static int cacheSize() {
        return CACHE.size();
    }
}