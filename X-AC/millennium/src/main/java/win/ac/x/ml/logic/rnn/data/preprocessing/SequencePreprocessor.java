package win.ac.x.ml.logic.rnn.data.preprocessing;

import win.ac.x.ml.logic.rnn.data.SequenceData;

public interface SequencePreprocessor {
    SequenceData prepare(double[][] rawVecs);
}