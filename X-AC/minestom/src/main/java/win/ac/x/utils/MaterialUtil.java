package win.ac.x.utils;

public final class MaterialUtil {

    public static String getMaterial(String modernName, String legacyName) {
        try {
            return modernName;
        } catch (IllegalArgumentException a) {
            return legacyName;
        }
    }
}