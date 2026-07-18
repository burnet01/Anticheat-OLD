package xac.cloud.modules.v4_5;

import win.ac.x.ml.data.ResultML;
import win.ac.x.ml.data.module.FlagType;
import win.ac.x.ml.data.module.ModuleML;
import win.ac.x.ml.data.module.ModuleResultML;
import win.ac.x.ml.logic.ModelVer;
import win.ac.x.math.Simplification;

public class M3Module implements ModuleML {

    private static final double M = 2.0;

    @Override
    public String getName() {
        return "m3";
    }

    @Override
    public ModuleResultML getResult(ResultML resultML) {
        ResultML.CheckResultML stats = resultML.statisticsResult;
        final double UNUSUAL = stats.UNUSUAL / M;
        final double STRANGE = stats.STRANGE / M;
        final double SUSPECTED = stats.SUSPECTED / M;
        final double SUSPICIOUSLY = stats.SUSPICIOUSLY / M;

        if (UNUSUAL > 0.3 && STRANGE > 0.20 && SUSPECTED > 0.14 && SUSPICIOUSLY > 0.07)
            return new ModuleResultML(20, FlagType.SUSPECTED,
                    String.valueOf(Simplification.scaleVal(UNUSUAL, 3)));

        if ((UNUSUAL > 0.34 && STRANGE > 0.12 && SUSPECTED > 0.1 && SUSPICIOUSLY > 0) ||
                (UNUSUAL > 0.25 && STRANGE > 0.12 && SUSPECTED > 0.12) ||
                (UNUSUAL > 0.10 && STRANGE > 0.14 && SUSPECTED > 0.08 && SUSPICIOUSLY > 0))
            return new ModuleResultML(20, FlagType.STRANGE,
                    String.valueOf(Simplification.scaleVal(UNUSUAL, 3)));

        return new ModuleResultML(0, FlagType.NORMAL,
                String.valueOf(Simplification.scaleVal(UNUSUAL, 3)));
    }

    @Override
    public int getParameterBuffer() {
        return 15;
    }

    @Override
    public ModelVer getVersion() {
        return ModelVer.VERSION_4_5;
    }
}