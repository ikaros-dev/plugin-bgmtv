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
    /**
     * <table>
     *     <thead>
     *         <tr>
     *             <th>code</th>
     *             <th>type</th>
     *             <th>description</th>
     *         </tr>
     *     </thead>
     *     <tbody>
     *         <tr>
     *             <td>5</td>
     *             <td>NONE</td>
     *             <td>没有</td>
     *         </tr>
     *         <tr>
     *             <td>1</td>
     *             <td>BOOK</td>
     *             <td>书籍</td>
     *         </tr>
     *         <tr>
     *             <td>2</td>
     *             <td>ANIME</td>
     *             <td>动画</td>
     *         </tr>
     *         <tr>
     *             <td>3</td>
     *             <td>MUSIC</td>
     *             <td>音乐</td>
     *         </tr>
     *         <tr>
     *             <td>4</td>
     *             <td>GAME</td>
     *             <td>游戏</td>
     *         </tr>
     *         <tr>
     *             <td>6</td>
     *             <td>REAL</td>
     *             <td>三次元</td>
     *         </tr>
     *     </tbody>
     * </table>
     */
    private Integer type;
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
