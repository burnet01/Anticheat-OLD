package win.ac.x.utils;

import win.ac.x.X;

public final class ConfigCache {

    public static double VL_LIMIT;
    public static float VL_RESET;
    public static String ALERT_MSG;
    public static String UNUSUAL;
    public static String SUSPECTED;
    public static String BAN_COMMAND;
    public static String BYPASS;
    public static String BC_MSG;
    public static boolean PUNISH_EFFECT;
    public static boolean INTERACT_SPELL;
    public static boolean IGNORE_CINEMATIC;
    public static boolean LOG_IN_FILES;
    public static boolean ROTATIONS_CONTAINER;
    public static boolean PREVENT_GHOST_BLOCK_ABUSE;
    public static boolean BYPASS_CREATIVE;
    public static int PREVENTION;


    public static void loadConfig() {
        VL_LIMIT = X.getInstance().getConfig().getDouble("vlLimit", 100);
        VL_RESET = (float) X.getInstance().getConfig().getDouble("vlReset", 15);
        PREVENTION = X.getInstance().getConfig().getInt("prevention", 2);
        ALERT_MSG = X.getInstance().getConfig().getString("alertMsg", "&9&l[MX] &e%player% &8>>&c %check% &7(&c%component%&7) &8%info% &f[%vl%/%vlLimit%]");
        UNUSUAL = X.getInstance().getConfig().getString("unusual", "&9&l[MX] &e%player% &8>>&6 Playing suspiciously");
        SUSPECTED = X.getInstance().getConfig().getString("suspected", "&9&l[MX] &e%player% &8>>&4 Looks like a cheater!");
        BAN_COMMAND = X.getInstance().getConfig().getString("banCommand", "ban %player% 1d Unfair advantage");
        BYPASS = X.getInstance().getConfig().getString("bypass", "mx.bypass");
        BC_MSG = X.getInstance().getConfig().getString("bcMsg", "&c&l[MX]&f %message%");
        PUNISH_EFFECT = X.getInstance().getConfig().getBoolean("punishEffect", false);
        INTERACT_SPELL = X.getInstance().getConfig().getBoolean("interactSpell", false);
        IGNORE_CINEMATIC = X.getInstance().getConfig().getBoolean("ignoreCinematic", false);
        LOG_IN_FILES = X.getInstance().getConfig().getBoolean("logInFiles", true);
        ROTATIONS_CONTAINER = X.getInstance().getConfig().getBoolean("rotationsContainer", true);
        PREVENT_GHOST_BLOCK_ABUSE = X.getInstance().getConfig().getBoolean("preventGhostBlockAbuse", false);
        BYPASS_CREATIVE = X.getInstance().getConfig().getBoolean("bypassCreative", true);
    }
}
