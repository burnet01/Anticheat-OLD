package win.ac.x.ml.data.module;

import win.ac.x.ml.data.ResultML;
import win.ac.x.ml.logic.ModelVer;

public interface ModuleML {
    String getName();
    ModuleResultML getResult(ResultML resultML);
    int getParameterBuffer();
    ModelVer getVersion();
}
