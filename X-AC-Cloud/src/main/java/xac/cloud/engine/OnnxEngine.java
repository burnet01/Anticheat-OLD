package xac.cloud.engine;

import ai.onnxruntime.*;
import win.ac.x.ml.logic.Logger;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;

public class OnnxEngine {

    private OrtEnvironment env;
    private OrtSession.SessionOptions sessionOpts;
    private final Map<String, ModelEntry> physicsModels = new LinkedHashMap<>();
    private final Map<String, ModelEntry> baselineModels = new LinkedHashMap<>();
    private final String modelsDir;
    private boolean loaded = false;

    public OnnxEngine(String modelsDir) {
        this.modelsDir = modelsDir;
    }

    public void loadAll() {
        try {
            env = OrtEnvironment.getEnvironment();
            sessionOpts = new OrtSession.SessionOptions();
        } catch (Throwable e) {
            Logger.error("[ONNX] Failed to initialize ONNX Runtime: " + e.getMessage());
            return;
        }

        File dir = new File(modelsDir);
        if (!dir.exists()) {
            dir.mkdirs();
            Logger.warn("[ONNX] Models directory created: " + dir.getAbsolutePath());
            return;
        }

        File[] files = dir.listFiles((d, n) -> n.endsWith(".onnx"));
        if (files == null || files.length == 0) {
            Logger.warn("[ONNX] No .onnx models found in " + dir.getAbsolutePath());
            return;
        }

        for (File f : files) {
            String name = f.getName();
            String label = extractLabel(name);
            try {
                OrtSession session = env.createSession(f.getAbsolutePath(), sessionOpts);
                ModelEntry entry = new ModelEntry(session, label);

                if (name.startsWith("physics_")) {
                    physicsModels.put(name, entry);
                } else if (name.startsWith("baseline_")) {
                    baselineModels.put(name, entry);
                }
                Logger.info("[ONNX] Loaded: " + name + " (label=" + label + ")");
            } catch (OrtException e) {
                Logger.error("[ONNX] Failed to load " + name + ": " + e.getMessage());
            }
        }

        loaded = !physicsModels.isEmpty() || !baselineModels.isEmpty();
        Logger.info("[ONNX] Loaded " + physicsModels.size() + " physics model(s), "
                + baselineModels.size() + " baseline model(s)");
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isPhysicsReady() {
        return env != null && !physicsModels.isEmpty();
    }

    public boolean isBaselineReady() {
        return env != null && !baselineModels.isEmpty();
    }

    public List<ModelResult> runPhysics(float[][] sequence) {
        List<ModelResult> results = new ArrayList<>();
        for (Map.Entry<String, ModelEntry> e : physicsModels.entrySet()) {
            float prob = runModel(e.getValue().session, sequence, new long[]{1, 20, 10});
            if (prob >= 0f) {
                results.add(new ModelResult(e.getValue().label, prob));
            }
        }
        return results;
    }

    public List<ModelResult> runBaseline(float[][] sequence) {
        List<ModelResult> results = new ArrayList<>();
        for (Map.Entry<String, ModelEntry> e : baselineModels.entrySet()) {
            float prob = runModel(e.getValue().session, sequence, new long[]{1, 40, 4});
            if (prob >= 0f) {
                results.add(new ModelResult(e.getValue().label, prob));
            }
        }
        return results;
    }

    private float runModel(OrtSession session, float[][] sequence, long[] shape) {
        if (session == null || env == null) return -1f;
        try {
            int seqLen = sequence.length;
            int featDim = sequence[0].length;
            float[] flat = new float[seqLen * featDim];
            for (int t = 0; t < seqLen; t++) {
                System.arraycopy(sequence[t], 0, flat, t * featDim, featDim);
            }

            FloatBuffer fb = FloatBuffer.wrap(flat);
            OnnxTensor tensor = OnnxTensor.createTensor(env, fb, shape);

            String inputName = session.getInputNames().iterator().next();
            OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor));

            float probability = extractProbability(result);
            result.close();
            tensor.close();
            return probability;
        } catch (OrtException e) {
            return -1f;
        }
    }

    private float extractProbability(OrtSession.Result result) throws OrtException {
        OnnxTensor output = (OnnxTensor) result.get(0);
        float[][] arr = (float[][]) output.getValue();
        float raw;
        if (arr.length > 0 && arr[0].length > 0) {
            raw = arr[0][0];
        } else {
            float[] flat = output.getFloatBuffer().array();
            raw = flat.length > 0 ? flat[0] : -1f;
        }
        if (raw < 0f) return -1f;
        if (raw > 1.0f) {
            return (float) (1.0 / (1.0 + Math.exp(-raw)));
        }
        return Math.max(0f, Math.min(1f, raw));
    }

    private String extractLabel(String fileName) {
        String base = fileName.replace(".onnx", "");
        int underscore = base.indexOf('_');
        if (underscore >= 0 && underscore < base.length() - 1) {
            return base.substring(underscore + 1);
        }
        return base;
    }

    public void close() {
        for (ModelEntry e : physicsModels.values()) {
            try { e.session.close(); } catch (OrtException ignored) { }
        }
        for (ModelEntry e : baselineModels.values()) {
            try { e.session.close(); } catch (OrtException ignored) { }
        }
        physicsModels.clear();
        baselineModels.clear();
    }

    public static class ModelEntry {
        final OrtSession session;
        final String label;
        ModelEntry(OrtSession session, String label) {
            this.session = session;
            this.label = label;
        }
    }

    public static class ModelResult {
        public final String label;
        public final float probability;
        public ModelResult(String label, float probability) {
            this.label = label;
            this.probability = probability;
        }
    }
}
