package xac.cloud.engine;

import xac.cloud.proto.CheckVerdict;

import java.util.ArrayList;
import java.util.List;

public class VerdictAggregator {

    private final double onnxThreshold;

    public VerdictAggregator(double onnxThreshold) {
        this.onnxThreshold = onnxThreshold;
    }

    public CheckVerdict buildMillenniumVerdict(String checkType, MillenniumEngine.MillenniumResult result) {
        return CheckVerdict.newBuilder()
                .setCheckType(checkType)
                .setVerdict(result.getVerdictString())
                .setScore(result.getScore())
                .setDetails(result.getInfo() + " models=" + result.getModelsThatFlagged())
                .build();
    }

    public List<CheckVerdict> buildOnnxVerdicts(String checkType, List<OnnxEngine.ModelResult> results) {
        List<CheckVerdict> verdicts = new ArrayList<>();
        for (OnnxEngine.ModelResult r : results) {
            String verdict;
            if (r.probability >= onnxThreshold) {
                if (r.probability >= 0.95) verdict = "SUSPECTED";
                else if (r.probability >= 0.90) verdict = "STRANGE";
                else verdict = "UNUSUAL";
            } else {
                verdict = "CLEAN";
            }
            verdicts.add(CheckVerdict.newBuilder()
                    .setCheckType(checkType + "/" + r.label)
                    .setVerdict(verdict)
                    .setScore(r.probability)
                    .setDetails("probability=" + String.format("%.2f%%", r.probability * 100))
                    .build());
        }
        return verdicts;
    }

    public CheckVerdict buildCleanVerdict(String checkType, String reason) {
        return CheckVerdict.newBuilder()
                .setCheckType(checkType)
                .setVerdict("CLEAN")
                .setScore(0f)
                .setDetails(reason)
                .build();
    }
}