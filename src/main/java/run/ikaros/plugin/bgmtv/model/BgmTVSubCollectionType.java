package run.ikaros.plugin.bgmtv.model;

public enum BgmTVSubCollectionType {
    /**
     * Wist watch.
     */
    WISH(1),
    /**
     * Watching.
     */
    DOING(3),
    /**
     * Watch done.
     */
    DONE(2),
    /**
     * No time to watch it.
     */
    SHELVE(4),
    /**
     * Discard it.
     */
    DISCARD(5);
    private final int code;

    BgmTVSubCollectionType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
