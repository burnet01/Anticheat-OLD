package win.ac.x.ml.data.module;

public enum FlagType {
    NORMAL,
    UNUSUAL,
    STRANGE,
    SUSPECTED;

    public int getLevel() {
        return this.ordinal();
    }
}
