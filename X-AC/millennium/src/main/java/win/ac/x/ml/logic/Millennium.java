package win.ac.x.ml.logic;

import win.ac.x.ml.data.ObjectML;
import win.ac.x.ml.data.ResultML;
import win.ac.x.vectors.Pair;

import java.util.List;

public interface Millennium {
    ResultML checkData(List<ObjectML> o);
    void learnByData(List<ObjectML> o, boolean isMustBeBlocked);
    void trainEpochs(List<Pair<List<ObjectML>, Boolean>> dataset, int epochs);
    void saveToFile(String fileName);
    int parameters();
}