package xac.cloud.modules.v5;

import win.ac.x.ml.data.ResultML;
import win.ac.x.ml.data.module.FlagType;
import win.ac.x.ml.data.module.ModuleML;
import win.ac.x.ml.data.module.ModuleResultML;
import win.ac.x.ml.logic.ModelVer;

public class RNN1Module implements ModuleML {

    @Override
    public String getName() {
        return "m1-rnn";
    }

    @Override
    public ModuleResultML getResult(ResultML resultML) {
        double p = resultML.statisticsResult.UNUSUAL;
        if (p > 0.93) return new ModuleResultML(20, FlagType.SUSPECTED, "Insane Probability " + f(p));
        if (p > 0.85) return new ModuleResultML(12, FlagType.SUSPECTED, "Suspicious Probability " + f(p));
        if (p > 0.78) return new ModuleResultML(8, FlagType.STRANGE, "Strange Behavior " + f(p));
        if (p > 0.70) return new ModuleResultML(4, FlagType.UNUSUAL, "Unusual patterns " + f(p));
        return new ModuleResultML(0, FlagType.NORMAL, f(p));
    }

    private String f(double v) {
        return String.format("%.1f%%", v * 100);
    }

    @Override
    public int getParameterBuffer() {
        return 32;
    }

    @Override
    public ModelVer getVersion() {
        return ModelVer.VERSION_5;
    }
}