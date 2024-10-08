package run.ikaros.plugin.bgmtv.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 剧集信息
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class BgmTvEpisode {
    private Integer id;
    private BgmTvEpisodeType type;
    private String name;
    @JsonProperty("name_cn")
    private String nameCn;
    private Double sort;
    private Float ep;
    @JsonProperty("airdate")
    private String airDate;
    private Integer comment;
    /**
     * 维基人填写的原始时长
     */
    private String duration;
    /**
     * 简介
     */
    private String desc;
    /**
     * 音乐曲目的碟片数
     */
    private String disc;
    @JsonProperty("duration_seconds")
    private Integer durationSeconds;


    public Integer getId() {
        return id;
    }

    public BgmTvEpisode setId(Integer id) {
        this.id = id;
        return this;
    }

    public BgmTvEpisodeType getType() {
        return type;
    }

    public BgmTvEpisode setType(BgmTvEpisodeType type) {
        this.type = type;
        return this;
    }

    public String getName() {
        return name;
    }

    public BgmTvEpisode setName(String name) {
        this.name = name;
        return this;
    }

    public String getNameCn() {
        return nameCn;
    }

    public BgmTvEpisode setNameCn(String nameCn) {
        this.nameCn = nameCn;
        return this;
    }

    public Double getSort() {
        return sort;
    }

    public BgmTvEpisode setSort(Double sort) {
        this.sort = sort;
        return this;
    }

    public Float getEp() {
        return ep;
    }

    public BgmTvEpisode setEp(Float ep) {
        this.ep = ep;
        return this;
    }

    public String getAirDate() {
        return airDate;
    }

    public BgmTvEpisode setAirDate(String airDate) {
        this.airDate = airDate;
        return this;
    }

    public Integer getComment() {
        return comment;
    }

    public BgmTvEpisode setComment(Integer comment) {
        this.comment = comment;
        return this;
    }

    public String getDuration() {
        return duration;
    }

    public BgmTvEpisode setDuration(String duration) {
        this.duration = duration;
        return this;
    }

    public String getDesc() {
        return desc;
    }

    public BgmTvEpisode setDesc(String desc) {
        this.desc = desc;
        return this;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public BgmTvEpisode setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
        return this;
    }

    public String getDisc() {
        return disc;
    }

    public BgmTvEpisode setDisc(String disc) {
        this.disc = disc;
        return this;
    }
}
