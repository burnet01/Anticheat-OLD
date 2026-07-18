package win.ac.x.ml;

import win.ac.x.ml.data.module.ModuleML;
import win.ac.x.ml.logic.Logger;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;

@UtilityClass
public class ClientML {

    public static final boolean DEV_MODE = false;

    public static final String CLIENT_NAME = "quark-e-4.0-100k-mini";
    private static final int TABLE_SIZE = 2;

    public static final List<ModuleML> MODEL_LIST = Collections.emptyList();

    public void run() {
        Logger.info(CLIENT_NAME + " loaded! (Minestom mode)");
        Logger.info("ML check modules not loaded (library mode)");
    }

    public static boolean isRunning() {
        return false;
    }

    public static void forceTrain() {
        Logger.info("Training not supported in Minestom library mode");
    }
}