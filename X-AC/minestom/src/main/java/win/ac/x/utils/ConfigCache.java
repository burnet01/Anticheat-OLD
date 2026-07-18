package win.ac.x.utils;

import win.ac.x.XMinestom;

import java.io.File;

public final class ConfigCache {

    public static double VL_LIMIT;
    public static float VL_RESET;
    public static String ALERT_MSG;
    public static String UNUSUAL;
    public static String SUSPECTED;
    public static String BC_MSG;
    public static boolean INTERACT_SPELL;
    public static boolean IGNORE_CINEMATIC;
    public static boolean LOG_IN_FILES;
    public static boolean ROTATIONS_CONTAINER;
    public static boolean PREVENT_GHOST_BLOCK_ABUSE;

    public static void loadConfig() {
        File configFile = new File(XMinestom.getDataFolder().toFile(), "config.yml");
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        java.util.Map<String, Object> config = new java.util.LinkedHashMap<>();

        if (configFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                config = yaml.load(fis);
                if (config == null) config = new java.util.LinkedHashMap<>();
            } catch (Exception ignored) {
            }
        }

        if (!configFile.exists()) {
            config.put("vlLimit", 100);
            config.put("vlReset", 15.0);
            config.put("alertMsg", "&9&l[MX] &e%player% &8>>&c %check% &7(&c%component%&7) &8%info% &f[%vl%/%vlLimit%]");
            config.put("unusual", "&9&l[MX] &e%player% &8>>&6 Playing suspiciously");
            config.put("suspected", "&9&l[MX] &e%player% &8>>&4 Looks like a cheater!");
            config.put("bcMsg", "&c&l[MX]&f %message%");
            config.put("punishEffect", false);
            config.put("interactSpell", false);
            config.put("ignoreCinematic", false);
            config.put("logInFiles", true);
            config.put("rotationsContainer", true);
            config.put("preventGhostBlockAbuse", false);

            try (java.io.FileWriter fw = new java.io.FileWriter(configFile)) {
                yaml.dump(config, fw);
            } catch (Exception ignored) {
            }
        }

        VL_LIMIT = getDouble(config, "vlLimit", 100);
        VL_RESET = (float) getDouble(config, "vlReset", 15);
        ALERT_MSG = getString(config, "alertMsg", "&9&l[MX] &e%player% &8>>&c %check% &7(&c%component%&7) &8%info% &f[%vl%/%vlLimit%]");
        UNUSUAL = getString(config, "unusual", "&9&l[MX] &e%player% &8>>&6 Playing suspiciously");
        SUSPECTED = getString(config, "suspected", "&9&l[MX] &e%player% &8>>&4 Looks like a cheater!");
        BC_MSG = getString(config, "bcMsg", "&c&l[MX]&f %message%");
        INTERACT_SPELL = getBoolean(config, "interactSpell", false);
        IGNORE_CINEMATIC = getBoolean(config, "ignoreCinematic", false);
        LOG_IN_FILES = getBoolean(config, "logInFiles", true);
        ROTATIONS_CONTAINER = getBoolean(config, "rotationsContainer", true);
        PREVENT_GHOST_BLOCK_ABUSE = getBoolean(config, "preventGhostBlockAbuse", false);
    }

    private static double getDouble(java.util.Map<String, Object> config, String key, double def) {
        Object v = config.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }

    private static int getInt(java.util.Map<String, Object> config, String key, int def) {
        Object v = config.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    private static String getString(java.util.Map<String, Object> config, String key, String def) {
        Object v = config.get(key);
        return v instanceof String ? (String) v : def;
    }

    private static boolean getBoolean(java.util.Map<String, Object> config, String key, boolean def) {
        Object v = config.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }
}