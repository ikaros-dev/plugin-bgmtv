package run.ikaros.plugin.bgmtv.model;

public enum BgmTvEpisodeCollectionType {
    NOT(0),
    WISH(1),
    DONE(2),
    DISCARD(3);

    private final int code;

    BgmTvEpisodeCollectionType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
