package win.ac.x.managers;

import win.ac.x.X;
import win.ac.x.ml.data.ObjectML;
import win.ac.x.vectors.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatasetManager {

    private static final File FOLDER = new File(X.getInstance().getDataFolder(), "dataset");

    public static void init() {
        if (!FOLDER.exists()) {
            FOLDER.mkdirs();
        }
    }

    public static void saveSample(List<ObjectML> data, boolean isCheater) {
        String prefix = isCheater ? "cheat_" : "legit_";
        File file = new File(FOLDER, prefix + UUID.randomUUID().toString() + ".dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(data);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Pair<List<ObjectML>, Boolean>> loadDataset() {
        List<Pair<List<ObjectML>, Boolean>> dataset = new ArrayList<>();
        File[] files = FOLDER.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return dataset;

        for (File file : files) {
            boolean isCheater = file.getName().startsWith("cheat_");
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                List<ObjectML> data = (List<ObjectML>) ois.readObject();
                dataset.add(new Pair<>(data, isCheater));
            } catch (Exception ignored) {
            }
        }
        return dataset;
    }

    public static int getCount() {
        File[] files = FOLDER.listFiles((dir, name) -> name.endsWith(".dat"));
        return files == null ? 0 : files.length;
    }
}