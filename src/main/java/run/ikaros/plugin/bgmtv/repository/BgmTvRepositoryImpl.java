package run.ikaros.plugin.bgmtv.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import run.ikaros.plugin.bgmtv.constants.BgmTvApiConst;
import run.ikaros.plugin.bgmtv.model.*;
import run.ikaros.plugin.bgmtv.utils.BeanUtils;
import run.ikaros.plugin.bgmtv.utils.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static run.ikaros.plugin.bgmtv.constants.BgmTvConst.REST_TEMPLATE_USER_AGENT;
import static run.ikaros.plugin.bgmtv.constants.BgmTvConst.TOKEN_PREFIX;

public class BgmTvRepositoryImpl implements BgmTvRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(BgmTvRepositoryImpl.class);
    private RestTemplate restTemplate = getRestTemplate();

    private static RestTemplate getRestTemplate() {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(3000);//获取链接超时时间
        httpRequestFactory.setConnectTimeout(3000);//指客户端和服务器建立连接的timeout
        return new RestTemplate(httpRequestFactory);
    }

    private final HttpHeaders headers = new HttpHeaders();

    @Override
    public boolean assertDomainReachable() {
        refreshHttpHeaders(null);
        try {
            restTemplate
                .exchange(BgmTvApiConst.BASE, HttpMethod.GET,
                    new HttpEntity<>(null, headers), Map.class);
            return true;
        } catch (HttpClientErrorException exception) {
            return exception.getStatusCode() == HttpStatus.NOT_FOUND;
        }
    }

    public void setRestTemplate(
        @Nonnull RestTemplate restTemplate) {
        Assert.notNull(restTemplate, "'restTemplate' must not null.");
        this.restTemplate = restTemplate;
    }

    /**
     * 需要设置bgmTv API 要求的 User Agent
     *
     * @see <a href="https://github.com/bangumi/api/blob/master/docs-raw/user%20agent.md">bgmtv api user aget</a>
     */
    @Override
    public void refreshHttpHeaders(@Nullable String accessToken) {
        headers.clear();
        headers.set(HttpHeaders.USER_AGENT, REST_TEMPLATE_USER_AGENT);
        headers.set(HttpHeaders.COOKIE, "chii_searchDateLine=0");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        if (StringUtils.isNotBlank(accessToken)) {
            LOGGER.debug("update http head access token");
            headers.set(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + accessToken);
        }
    }


    @Nullable
    @Override
    public BgmTvSubject getSubject(@Nonnull Long subjectId) {
        Assert.isTrue(subjectId > 0, "'subjectId' must be positive");
        // https://api.bgm.tv/v0/subjects/373267
        final String url = BgmTvApiConst.SUBJECTS + "/" + subjectId;
        try {
            String result = restTemplate
                .exchange(url, HttpMethod.GET,
                    new HttpEntity<>(null, headers), String.class)
                .getBody();
            Map map = JsonUtils.json2obj(result, Map.class);
            Object infobox = map.remove("infobox");
            BgmTvSubject bgmTvSubject =
                JsonUtils.json2obj(JsonUtils.obj2Json(map), BgmTvSubject.class);

            bgmTvSubject.setInfobox(convertInfoBox(JsonUtils.obj2Json(infobox)));
            return bgmTvSubject;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.warn("subject not found for subjectId={}", subjectId);
                return null;
            }
            throw exception;
        } catch (JsonProcessingException e) {
            LOGGER.error("convert infobox exception for subjectId={}", subjectId, e);
            throw new RuntimeException(e);
        }
    }

    private String convertInfoBox(String originalStr) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(originalStr);

        StringBuilder result = new StringBuilder();
        for (JsonNode node : jsonNode) {
            String key = node.get("key").asText();
            JsonNode valueNode = node.get("value");
            String value;

            if (valueNode.isArray()) {
                StringBuilder valueBuilder = new StringBuilder();
                for (JsonNode subNode : valueNode) {
                    if (subNode.has("v")) {
                        valueBuilder.append(subNode.get("v").asText()).append(" ");
                    }
                }
                value = valueBuilder.toString().trim();
            } else {
                value = valueNode.asText();
            }

            String line = key + ": " + value;
            result.append(line).append(System.lineSeparator());
        }
        return result.toString();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BgmTvPagingData<BgmTvSubject> searchSubjectWithNextApi(@Nonnull String keyword,
                                                                  @Nullable Integer offset,
                                                                  @Nullable Integer limit) {
        Assert.hasText(keyword, "'keyword' must has text");
        if (offset == null || offset < 0) {
            offset = BgmTvApiConst.DEFAULT_OFFSET;
        }
        if (limit == null || limit < 0) {
            limit = BgmTvApiConst.DEFAULT_LIMIT;
        }

        // https://api.bgm.tv/v0/search/subjects?limit=50&offset=1
        UriComponentsBuilder uriComponentsBuilder =
            UriComponentsBuilder.fromHttpUrl(BgmTvApiConst.NEXT_SEARCH_SUBJECTS)
                .queryParam("limit", limit)
                .queryParam("offset", offset);

        BgmTvSearchRequest bgmTvSearchRequest = new BgmTvSearchRequest()
            .setKeyword(keyword);

        HttpEntity<BgmTvSearchRequest> httpEntity = new HttpEntity<>(bgmTvSearchRequest, headers);

        ResponseEntity<BgmTvPagingData> responseEntity =
            restTemplate.exchange(uriComponentsBuilder.toUriString(), HttpMethod.POST, httpEntity,
                BgmTvPagingData.class);
        BgmTvPagingData body = responseEntity.getBody();
        Assert.notNull(body, "response body");

        BgmTvPagingData<BgmTvSubject> bgmTvPagingData = new BgmTvPagingData<>();
        BeanUtils.copyProperties(body, bgmTvPagingData, Set.of("data"));

        BgmTvSubject[] bgmTvSubjects =
            JsonUtils.obj2Arr(body.getData(), new TypeReference<>() {
            });
        bgmTvPagingData.setData(List.of(bgmTvSubjects));
        return bgmTvPagingData;
    }

    @Override
    public List<BgmTvSubject> searchSubjectWithOldApi(@Nonnull String keyword,
                                                      @Nullable BgmTvSubjectType type) {
        Assert.hasText(keyword, "'keyword' must has text");
        // https://api.bgm.tv/search/subject/air?type=2&responseGroup=large
        UriComponentsBuilder uriComponentsBuilder =
            UriComponentsBuilder.fromHttpUrl(BgmTvApiConst.OLD_SEARCH_SUBJECT + "/" + keyword)
                .queryParam("responseGroup", "large");

        if (type != null) {
            uriComponentsBuilder.queryParam("type", type.getCode());
        }

        String url = BgmTvApiConst.OLD_SEARCH_SUBJECT + "/" + keyword + "?responseGroup=large";
        if (type != null) {
            url = url + "&type=" + type.getCode();
        }

        ResponseEntity<Map> responseEntity =
            restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(null, headers), Map.class);
        Map body = responseEntity.getBody();
        Assert.notNull(body, "'request body' must not null.");
        if (body.get("code") != null) {
            return List.of();
        }
        Integer results = (Integer) body.get("results");
        if (results <= 0) {
            return List.of();
        }

        Object list = body.get("list");
        BgmTvSubject[] bgmTvSubjects = JsonUtils.obj2Arr(list, new TypeReference<BgmTvSubject[]>() {
        });

        return List.of(bgmTvSubjects);
    }

    @Override
    public byte[] downloadCover(@Nonnull String url) {
        Assert.hasText(url, "'url' must has text");
        ResponseEntity<byte[]> responseEntity =
            restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
        return responseEntity.getBody();
    }

    @Override
    public List<BgmTvEpisode> findEpisodesBySubjectId(@Nonnull Long subjectId,
                                                      @Nonnull BgmTvEpisodeType episodeType,
                                                      @Nullable Integer offset,
                                                      @Nullable Integer limit) {
        Assert.isTrue(subjectId > 0, "'subjectId' must be positive");
        if (offset == null) {
            offset = BgmTvApiConst.DEFAULT_OFFSET;
        }
        if (limit == null) {
            limit = BgmTvApiConst.DEFAULT_LIMIT;
        }
        // https://api.bgm.tv/v0/episodes?subject_id=373267&type=0&limit=100&offset=0
        UriComponentsBuilder uriComponentsBuilder =
            UriComponentsBuilder.fromHttpUrl(BgmTvApiConst.EPISODES)
                .queryParam("subject_id", subjectId)
                .queryParam("type", episodeType.getCode())
                .queryParam("limit", limit)
                .queryParam("offset", offset);


        ResponseEntity<BgmTvPagingData> responseEntity = restTemplate
            .exchange(uriComponentsBuilder.toUriString(), HttpMethod.GET,
                new HttpEntity<>(null, headers),
                BgmTvPagingData.class);

        BgmTvPagingData body = responseEntity.getBody();
        BgmTvEpisode[] bgmTvEpisodes = JsonUtils.obj2Arr(body.getData(), new TypeReference<>() {
        });

        return List.of(bgmTvEpisodes);
    }

    @Override
    public BgmTvUserInfo getMe() {
        ResponseEntity<BgmTvUserInfo> responseEntity =
            restTemplate.exchange(BgmTvApiConst.ME, HttpMethod.GET, new HttpEntity<>(null, headers),
                BgmTvUserInfo.class);
        if (responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return null;
        }
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        }
        return null;
    }


    //@Override
    //public void afterPropertiesSet() throws Exception {
    // OptionEntity tokenOptionEntity =
    //     optionService.findOptionValueByCategoryAndKey(OptionCategory.BGMTV,
    //         OptionBgmTv.ACCESS_TOKEN.name());
    // if (tokenOptionEntity == null || StringUtils.isBlank(tokenOptionEntity.getValue())) {
    //     LOGGER.warn("current not set bgmtv access token");
    // } else {
    //     refreshHttpHeaders(tokenOptionEntity.getValue());
    // }

    // OptionEntity enableProxyOption =
    //     optionService.findOptionValueByCategoryAndKey(OptionCategory.BGMTV,
    //         OptionBgmTv.ENABLE_PROXY.name());
    // if (enableProxyOption != null
    //     && Boolean.TRUE.toString().equalsIgnoreCase(enableProxyOption.getValue())) {
    //     OptionNetworkDTO optionNetworkDTO = optionService.getOptionNetworkDTO();
    //     setRestTemplate(
    //         RestTemplateUtils.buildHttpProxyRestTemplate(
    //             optionNetworkDTO.getProxyHttpHost(),
    //             optionNetworkDTO.getProxyHttpPort(),
    //             optionNetworkDTO.getReadTimeout(),
    //             optionNetworkDTO.getConnectTimeout()
    //         ));
    // }
    //}
}
