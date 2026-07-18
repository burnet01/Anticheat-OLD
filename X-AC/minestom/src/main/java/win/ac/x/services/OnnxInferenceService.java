package win.ac.x.services;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import win.ac.x.XMinestom;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class OnnxInferenceService {

    private static final File MODELS_DIR = XMinestom.getDataFolder().resolve("models").toFile();

    private static OrtEnvironment env;
    private static OrtSession.SessionOptions sessionOpts;
    private static final Map<String, ModelEntry> PHYSICS_MODELS = new LinkedHashMap<>();
    private static final Map<String, ModelEntry> BASELINE_MODELS = new LinkedHashMap<>();

    static {
        try {
            env = OrtEnvironment.getEnvironment();
            sessionOpts = new OrtSession.SessionOptions();
        } catch (Throwable e) {
            XMinestom.getLogger().warn("[ONNX] Failed to initialize ONNX Runtime");
            env = null;
            sessionOpts = null;
        }
    }

    public static void loadAll() {
        if (env == null) return;
        if (!MODELS_DIR.exists()) {
            MODELS_DIR.mkdirs();
            XMinestom.getLogger().warn("[ONNX] Models directory created. Place .onnx files in: " + MODELS_DIR.getAbsolutePath());
            return;
        }

        PHYSICS_MODELS.clear();
        BASELINE_MODELS.clear();

        File[] files = MODELS_DIR.listFiles((d, n) -> n.endsWith(".onnx"));
        if (files == null || files.length == 0) {
            XMinestom.getLogger().warn("[ONNX] No .onnx models found in " + MODELS_DIR.getAbsolutePath());
            return;
        }

        for (File f : files) {
            String name = f.getName();
            String label = extractLabel(name);
            try {
                OrtSession session = env.createSession(f.getAbsolutePath(), sessionOpts);
                ModelEntry entry = new ModelEntry(session, label);

                if (name.startsWith("physics_")) {
                    PHYSICS_MODELS.put(name, entry);
                } else if (name.startsWith("baseline_")) {
                    BASELINE_MODELS.put(name, entry);
                }
            } catch (OrtException e) {
                XMinestom.getLogger().warn("[ONNX] Failed to load " + name);
            }
        }

        XMinestom.getLogger().info("[ONNX] Loaded " + PHYSICS_MODELS.size() + " physics model(s), "
            + BASELINE_MODELS.size() + " baseline model(s)");
    }

    public static boolean isPhysicsReady() {
        return env != null && !PHYSICS_MODELS.isEmpty();
    }

    public static boolean isBaselineReady() {
        return env != null && !BASELINE_MODELS.isEmpty();
    }

    public static List<ModelResult> runPhysics(float[][] sequence) {
        List<ModelResult> results = new ArrayList<>();
        for (Map.Entry<String, ModelEntry> e : PHYSICS_MODELS.entrySet()) {
            float prob = runModel(e.getValue().session, sequence, new long[]{1, 20, 10});
            if (prob >= 0f) {
                results.add(new ModelResult(e.getValue().label, prob));
            }
        }
        return results;
    }

    public static List<ModelResult> runBaseline(float[][] sequence) {
        List<ModelResult> results = new ArrayList<>();
        for (Map.Entry<String, ModelEntry> e : BASELINE_MODELS.entrySet()) {
            float prob = runModel(e.getValue().session, sequence, new long[]{1, 40, 4});
            if (prob >= 0f) {
                results.add(new ModelResult(e.getValue().label, prob));
            }
        }
        return results;
    }

    private static float runModel(OrtSession session, float[][] sequence, long[] shape) {
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

    private static float extractProbability(OrtSession.Result result) throws OrtException {
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

    private static String extractLabel(String fileName) {
        String base = fileName.replace(".onnx", "");
        int underscore = base.indexOf('_');
        if (underscore >= 0 && underscore < base.length() - 1) {
            return base.substring(underscore + 1);
        }
        return base;
    }

    public static void close() {
        for (ModelEntry e : PHYSICS_MODELS.values()) {
            try { e.session.close(); } catch (OrtException ignored) { }
        }
        for (ModelEntry e : BASELINE_MODELS.values()) {
            try { e.session.close(); } catch (OrtException ignored) { }
        }
        PHYSICS_MODELS.clear();
        BASELINE_MODELS.clear();
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