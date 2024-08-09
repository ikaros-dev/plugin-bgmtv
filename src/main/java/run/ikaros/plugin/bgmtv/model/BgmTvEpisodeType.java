package run.ikaros.plugin.bgmtv.model;

public enum BgmTvEpisodeType {
    /**
     * 正篇
     */
    POSITIVE(0),

    /**
     * 特别篇
     */
    SPECIAL(1),


    OP(2),


    ED(3),


    PV(4),

    MAD(5),

    OTHER(6);
    ;
    private final int code;

    BgmTvEpisodeType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
