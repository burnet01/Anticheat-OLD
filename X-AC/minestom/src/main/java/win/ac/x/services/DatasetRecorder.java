package win.ac.x.services;

import win.ac.x.XMinestom;
import win.ac.x.core.AsyncScheduler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public final class DatasetRecorder {

    private static final File DATASET_DIR = XMinestom.getDataFolder().resolve("dataset").toFile();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyyMMdd_HHmmss");
    public static final Map<UUID, String> RECORDING = new ConcurrentHashMap<>();
    public static final Map<UUID, String> RECORDING_LETTER = new ConcurrentHashMap<>();
    private static final Map<UUID, RecordingSession> SESSIONS = new ConcurrentHashMap<>();

    public static void init() {
        if (!DATASET_DIR.exists()) {
            DATASET_DIR.mkdirs();
        }
    }

    public static void startRecording(UUID uuid, String label) {
        startRecording(uuid, label, null);
    }

    public static void startRecording(UUID uuid, String label, String letter) {
        RECORDING.put(uuid, label);
        if (letter != null && !letter.isEmpty()) {
            RECORDING_LETTER.put(uuid, letter);
        } else if (!label.equals("legit")) {
            RECORDING_LETTER.put(uuid, autoAssignLetter(label));
        } else {
            RECORDING_LETTER.put(uuid, "");
        }
        SESSIONS.put(uuid, new RecordingSession());
    }

    public static void stopRecording(UUID uuid) {
        saveSession(uuid);
        RECORDING.remove(uuid);
        RECORDING_LETTER.remove(uuid);
    }

    public static boolean isRecording(UUID uuid) {
        return RECORDING.containsKey(uuid);
    }

    public static void feedYawDelta(UUID uuid, double delta) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.yawDeltas.add(delta);
    }

    public static void feedPitchDelta(UUID uuid, double delta) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.pitchDeltas.add(delta);
    }

    public static void feedHorizontalSpeed(UUID uuid, double speed) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.horizontalSpeeds.add(speed);
    }

    public static void feedVerticalSpeed(UUID uuid, double speed) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.verticalSpeeds.add(speed);
    }

    public static void feedDrift(UUID uuid, double drift) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.driftValues.add(drift);
    }

    public static void feedClickInterval(UUID uuid, double interval) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.clickIntervals.add(interval);
    }

    public static void feedSprintState(UUID uuid, boolean sprinting) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.sprintStates.add(sprinting ? 1.0 : 0.0);
    }

    public static void feedExpectedMaxH(UUID uuid, double emaxh) {
        RecordingSession s = SESSIONS.get(uuid);
        if (s != null) s.expectedMaxH.add(emaxh);
    }

    public static void saveSession(UUID uuid) {
        RecordingSession s = SESSIONS.remove(uuid);
        if (s == null) return;
        String label = RECORDING.get(uuid);
        if (label == null) label = "legit";
        String letter = RECORDING_LETTER.get(uuid);
        if (letter == null) letter = "";
        final String lbl = label;
        final String ltr = letter;
        AsyncScheduler.run(() -> writeSession(uuid, s, lbl, ltr));
    }

    private static void writeSession(UUID uuid, RecordingSession s, String label, String letter) {
        try {
            String ltrPart = (letter != null && !letter.isEmpty()) ? ("_" + letter) : "";
            String prefix = "session_" + label + ltrPart + "_";
            String name = prefix + DATE_FMT.format(new Date()) + "_"
                    + uuid.toString().substring(0, 8) + ".json.gz";

            File targetDir;
            if (letter != null && !letter.isEmpty()) {
                targetDir = new File(DATASET_DIR, label + File.separator + letter);
            } else {
                targetDir = new File(DATASET_DIR, label);
            }
            if (!targetDir.exists()) targetDir.mkdirs();
            File out = new File(targetDir, name);
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(out)), StandardCharsets.UTF_8))) {
                w.write("{");
                w.write("\"ts\":" + System.currentTimeMillis());
                w.write(",\"uuid\":\"" + uuid + "\"");
                w.write(",\"label\":\"" + esc(label) + "\"");
                if (letter != null && !letter.isEmpty()) {
                    w.write(",\"letter\":\"" + esc(letter) + "\"");
                }
                w.write(",\"yaw_count\":" + s.yawDeltas.size());
                w.write(",\"pitch_count\":" + s.pitchDeltas.size());
                w.write(",\"hspeed_count\":" + s.horizontalSpeeds.size());
                w.write(",\"vspeed_count\":" + s.verticalSpeeds.size());
                w.write(",\"drift_count\":" + s.driftValues.size());
                w.write(",\"click_count\":" + s.clickIntervals.size());
                w.write(",\"sprint_count\":" + s.sprintStates.size());
                w.write(",\"emaxh_count\":" + s.expectedMaxH.size());
                w.write(",\"yaws\":" + listToJson(s.yawDeltas));
                w.write(",\"pitches\":" + listToJson(s.pitchDeltas));
                w.write(",\"hspeeds\":" + listToJson(s.horizontalSpeeds));
                w.write(",\"vspeeds\":" + listToJson(s.verticalSpeeds));
                w.write(",\"drifts\":" + listToJson(s.driftValues));
                w.write(",\"clicks\":" + listToJson(s.clickIntervals));
                w.write(",\"sprints\":" + listToJson(s.sprintStates));
                w.write(",\"emaxhs\":" + listToJson(s.expectedMaxH));
                w.write("}");
            }
        } catch (Exception ignored) {
        }
    }

    public static void record(FlagSnapshot snapshot) {
        if (snapshot == null || snapshot.playerUuid == null) return;
        String label = RECORDING.get(snapshot.playerUuid);
        final String lbl = (label != null) ? label : null;
        AsyncScheduler.run(() -> writeSnapshot(snapshot, lbl));
    }

    public static int getFileCount() {
        return collectAllGzFiles().size() + collectAllDatFiles().size();
    }

    private static List<File> collectAllGzFiles() {
        List<File> result = new ArrayList<>();
        collectFilesRecursive(DATASET_DIR, ".json.gz", result);
        return result;
    }

    private static List<File> collectAllDatFiles() {
        List<File> result = new ArrayList<>();
        collectFilesRecursive(DATASET_DIR, ".dat", result);
        return result;
    }

    private static void collectFilesRecursive(File dir, String suffix, List<File> out) {
        if (dir == null || !dir.exists()) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) {
                collectFilesRecursive(f, suffix, out);
            } else if (f.getName().endsWith(suffix)) {
                out.add(f);
            }
        }
    }

    public static String listFiles() {
        List<File> gz = collectAllGzFiles();
        List<File> dat = collectAllDatFiles();

        StringBuilder sb = new StringBuilder();
        sb.append("§eTotal files: §f").append(gz.size() + dat.size())
          .append(" §7(§b.json.gz: ").append(gz.size())
          .append(" §7| §6.dat: ").append(dat.size()).append("§7)");

        if (!gz.isEmpty()) {
            sb.append("\n§b.json.gz:");
            for (File f : gz) {
                String rel = DATASET_DIR.toPath().relativize(f.getParentFile().toPath()).toString();
                String dir = rel.isEmpty() ? "" : " §7[" + rel + "]";
                sb.append("\n §7  - §f").append(f.getName())
                  .append(dir)
                  .append(" §8(").append(f.length()).append(" bytes)");
            }
        }
        if (!dat.isEmpty()) {
            sb.append("\n§6.dat:");
            for (File f : dat) {
                sb.append("\n §7  - §f").append(f.getName())
                  .append(" §8(").append(f.length()).append(" bytes)");
            }
        }

        return sb.toString();
    }

    private static void writeSnapshot(FlagSnapshot s, String label) {
        try {
            String prefix = (label != null) ? ("snap_" + label + "_") : "auto_";
            String name = prefix + "snap_" + DATE_FMT.format(new Date(s.timestamp)) + "_"
                    + s.playerUuid.toString().substring(0, 8) + "_"
                    + s.checkName.toLowerCase() + ".json.gz";
            File out = new File(DATASET_DIR, name);
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(out)), StandardCharsets.UTF_8))) {
                w.write("{");
                w.write("\"ts\":" + s.timestamp);
                w.write(",\"uuid\":\"" + s.playerUuid + "\"");
                w.write(",\"check\":\"" + esc(s.checkName) + "\"");
                w.write(",\"comp\":\"" + esc(s.component) + "\"");
                w.write(",\"info\":\"" + esc(s.info) + "\"");
                if (label != null) {
                    w.write(",\"label\":\"" + esc(label) + "\"");
                }
                if (s.driftWindow != null) {
                    w.write(",\"dwin\":" + arrayToJson(s.driftWindow));
                    w.write(",\"dmax\":" + round(s.driftMax, 6));
                    w.write(",\"davg\":" + round(s.driftAvg, 6));
                    w.write(",\"dstd\":" + round(s.driftStd, 6));
                }
                if (s.horizontalSpeed > 0 || s.verticalSpeed > 0) {
                    w.write(",\"hsp\":" + round(s.horizontalSpeed, 6));
                    w.write(",\"vsp\":" + round(s.verticalSpeed, 6));
                    w.write(",\"emaxh\":" + round(s.expectedMaxHorizontal, 6));
                    w.write(",\"air\":" + s.airTicks);
                }
                if (s.yawZScore > 0 || s.pitchZScore > 0 || s.speedZScore > 0 || s.clickZScore > 0) {
                    w.write(",\"yz\":" + round(s.yawZScore, 4));
                    w.write(",\"pz\":" + round(s.pitchZScore, 4));
                    w.write(",\"sz\":" + round(s.speedZScore, 4));
                    w.write(",\"cz\":" + round(s.clickZScore, 4));
                    w.write(",\"ymean\":" + round(s.yawMean, 6));
                    w.write(",\"ystd\":" + round(s.yawStd, 6));
                    w.write(",\"pmean\":" + round(s.pitchMean, 6));
                    w.write(",\"pstd\":" + round(s.pitchStd, 6));
                    w.write(",\"smean\":" + round(s.speedMean, 6));
                    w.write(",\"sstd\":" + round(s.speedStd, 6));
                    w.write(",\"cmean\":" + round(s.clickMean, 4));
                    w.write(",\"cstd\":" + round(s.clickStd, 4));
                }
                if (s.recentYawDeltas != null) {
                    w.write(",\"rawd\":" + doubleArrayToJson(s.recentYawDeltas));
                }
                if (s.recentPitchDeltas != null) {
                    w.write(",\"rawp\":" + doubleArrayToJson(s.recentPitchDeltas));
                }
                if (s.recentSpeeds != null) {
                    w.write(",\"raws\":" + doubleArrayToJson(s.recentSpeeds));
                }
                w.write("}");
            }
        } catch (Exception ignored) {
        }
    }

    private static String arrayToJson(double[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(round(arr[i], 6));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String listToJson(List<Double> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(round(list.get(i), 6));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String doubleArrayToJson(double[] arr) {
        return arrayToJson(arr);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static double round(double v, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(v * scale) / scale;
    }

    private static String autoAssignLetter(String label) {
        java.util.Set<String> used = new java.util.HashSet<>();
        File labelDir = new File(DATASET_DIR, label);
        if (labelDir.exists() && labelDir.isDirectory()) {
            File[] subdirs = labelDir.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File d : subdirs) {
                    used.add(d.getName());
                }
            }
        }
        int n = 0;
        while (true) {
            String letter = toLetter(n);
            if (!used.contains(letter)) return letter;
            n++;
        }
    }

    private static String toLetter(int n) {
        StringBuilder sb = new StringBuilder();
        n++;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + (n % 26)));
            n /= 26;
        }
        return sb.toString();
    }

    public static class RecordingSession {
        final List<Double> yawDeltas = new ArrayList<>();
        final List<Double> pitchDeltas = new ArrayList<>();
        final List<Double> horizontalSpeeds = new ArrayList<>();
        final List<Double> verticalSpeeds = new ArrayList<>();
        final List<Double> driftValues = new ArrayList<>();
        final List<Double> clickIntervals = new ArrayList<>();
        final List<Double> sprintStates = new ArrayList<>();
        final List<Double> expectedMaxH = new ArrayList<>();
    }
}