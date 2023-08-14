package run.ikaros.plugin.bgmtv.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import run.ikaros.plugin.bgmtv.utils.JsonUtils;

import java.util.Map;

public class BgmTvSubjectJsonTest {

    @Test
    void convert() {
        BgmTvSubject bgmTvSubject = new BgmTvSubject();
        bgmTvSubject.setType(6);
        String json = JsonUtils.obj2Json(bgmTvSubject);
        Assertions.assertThat(json).isNotBlank();

        String tianDao = """
            {
              "date": null,
              "platform": "华语剧",
              "images": {
                "small": "https://lain.bgm.tv/r/200/pic/cover/l/a8/d3/128230_y55bq.jpg",
                "grid": "https://lain.bgm.tv/r/100/pic/cover/l/a8/d3/128230_y55bq.jpg",
                "large": "https://lain.bgm.tv/pic/cover/l/a8/d3/128230_y55bq.jpg",
                "medium": "https://lain.bgm.tv/r/800/pic/cover/l/a8/d3/128230_y55bq.jpg",
                "common": "https://lain.bgm.tv/r/400/pic/cover/l/a8/d3/128230_y55bq.jpg"
              },
              "summary": "　　年轻的女警官芮小丹（左小青 饰）通过朋友结识了商界怪才丁元英（王志文 饰），并受托在古城照料丁元英的生活。丁元英异于常人的性格和让人瞠目结舌的才华深深吸引着芮小丹。 \\r\\n　　借由对音乐的共同热爱和制备音箱的契机，芮小丹和丁元英建立了恋爱关系，并结识了几个古城的音乐发烧友。发烧友看中丁元英的奇才，想要利用他的才华帮助自己的农村老家脱贫。而小丹也希望丁元英用自己的智慧在这个极度贫困的农村写一个神话，作为送给她的礼物。 \\r\\n　　丁元英答应了小丹的请求，带领几个发烧友重出江湖，给音响市场带来了巨变的同时，也生动的演绎了所谓天道的“道法自然，如来”。可是他的礼物完成时，那个收礼的人却已不在身边，他也有自己难以出离的天道。",
              "name": "天道",
              "name_cn": "天道",
              "tags": [],
              "infobox": [
               
              ],
              "rating": {
                "rank": 0,
                "total": 15,
                "count": {
                  
                },
                "score": 8.7
              },
              "total_episodes": 24,
              "collection": {
                "on_hold": 2,
                "dropped": 0,
                "wish": 9,
                "collect": 18,
                "doing": 4
              },
              "id": 128230,
              "eps": 24,
              "volumes": 0,
              "locked": false,
              "nsfw": false,
              "type": 6
            }
            """;

        Map map = JsonUtils.json2obj(tianDao, Map.class);
        Object infobox = map.remove("infobox");
        bgmTvSubject = JsonUtils.json2obj(JsonUtils.obj2Json(map), BgmTvSubject.class);
        Assertions.assertThat(bgmTvSubject).isNotNull();
        Assertions.assertThat(bgmTvSubject.getType()).isEqualTo(6);

    }
}
