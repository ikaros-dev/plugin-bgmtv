package run.ikaros.plugin.bgmtv.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 条目
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BgmTvSubject {
    private Integer id;
    private BgmTvSubjectType type;
    private String name;
    @JsonProperty("name_cn")
    private String nameCn;
    private String summary;
    /**
     * YYYY-MM-DD
     */
    private String date;
    private String platform;
    private String url;
    private String infobox;
    private Boolean nsfw;
    private BgmTvImages images;
    private List<BgmTvTag> tags;
}
