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

    public static BgmTVSubCollectionType codeOf(int code) {
        return switch (code) {
            case 1 -> BgmTVSubCollectionType.WISH;
            case 3 -> BgmTVSubCollectionType.DOING;
            case 2 -> BgmTVSubCollectionType.DONE;
            case 4 -> BgmTVSubCollectionType.SHELVE;
            case 5 -> BgmTVSubCollectionType.DISCARD;
            default -> BgmTVSubCollectionType.WISH;
        };
    }
}
