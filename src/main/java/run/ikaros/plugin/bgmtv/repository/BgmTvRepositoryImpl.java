package run.ikaros.plugin.bgmtv.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.exception.NotFoundException;
import run.ikaros.plugin.bgmtv.BgmTvPlugin;
import run.ikaros.plugin.bgmtv.constants.BgmTvApiConst;
import run.ikaros.plugin.bgmtv.model.*;
import run.ikaros.plugin.bgmtv.utils.BeanUtils;
import run.ikaros.plugin.bgmtv.utils.JsonUtils;
import run.ikaros.plugin.bgmtv.utils.RestTemplateUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

import static run.ikaros.plugin.bgmtv.constants.BgmTvConst.REST_TEMPLATE_USER_AGENT;
import static run.ikaros.plugin.bgmtv.constants.BgmTvConst.TOKEN_PREFIX;
import static run.ikaros.plugin.bgmtv.model.BgmTVSubCollectionType.DOING;

@Slf4j
@Component
public class BgmTvRepositoryImpl
    implements BgmTvRepository, InitializingBean {
    private RestTemplate restTemplate;
    private final ReactiveCustomClient reactiveCustomClient;
    private final HttpHeaders headers = new HttpHeaders();

    public BgmTvRepositoryImpl(ReactiveCustomClient reactiveCustomClient) {
        this.reactiveCustomClient = reactiveCustomClient;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        reactiveCustomClient.findOne(ConfigMap.class, BgmTvPlugin.NAME)
            .onErrorResume(NotFoundException.class, e -> Mono.empty())
            .subscribe(configMap -> {
                log.info("init rest temp when app ready and config exits, configmap: {}",
                    configMap);

                initRestTemplate(configMap);

                String token = null;
                if (Objects.nonNull(configMap.getData())) {
                    token = String.valueOf(configMap.getData().get("token"));
                }
                refreshHttpHeaders(token);

//                log.info("Verifying that the domain name is accessible, please wait...");
//                boolean reachable = assertDomainReachable();
//                if (!reachable) {
//                    log.warn("The operation failed because the current domain name is not accessible "
//                        + "for domain: [{}].", BgmTvApiConst.BASE);
//                    throw new DomainNotAccessException(
//                        "Current domain can not access: " + BgmTvApiConst.BASE);
//                }
            });
    }

    public void initRestTemplate(ConfigMap configMap) {
        log.info("init rest template by config map : {}", configMap);
        if (configMap == null || configMap.getData() == null) {
            restTemplate = RestTemplateUtils.buildRestTemplate(3000, 3000);
            log.info("config rest template by no proxy.");
            return;
        }
        Map<String, String> map = configMap.getData();
        String enableProxy = String.valueOf(map.get("enableProxy"));
        if (StringUtils.isBlank(enableProxy) ||
            !Boolean.parseBoolean(enableProxy)) {
            restTemplate = RestTemplateUtils.buildRestTemplate(3000, 3000);
            log.info("config rest template by no proxy.");
            return;
        }
        String proxyType = String.valueOf(map.get("proxyType"));
        String host = String.valueOf(map.get("host"));
        String port = String.valueOf(map.get("port"));
        if (StringUtils.isNotBlank(enableProxy)
            && Boolean.parseBoolean(enableProxy)
            && StringUtils.isNotBlank(proxyType)
            && StringUtils.isNotBlank(host)
            && StringUtils.isNotBlank(port)) {
            InetSocketAddress inetSocketAddress =
                new InetSocketAddress(host, Integer.parseInt(port));
            switch (proxyType) {
                case "http" -> {
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, inetSocketAddress);
                    restTemplate =
                        RestTemplateUtils.buildProxyRestTemplate(proxy, 3000, 3000);
                    log.info("config rest template by [{}://{}:{}]", proxyType, host, port);
                }
                case "socks" -> {
                    Proxy proxy = new Proxy(Proxy.Type.SOCKS, inetSocketAddress);
                    restTemplate =
                        RestTemplateUtils.buildProxyRestTemplate(proxy, 3000, 3000);
                    log.info("config rest template by [{}://{}:{}]", proxyType, host, port);
                }
                default -> {
                    restTemplate = RestTemplateUtils.buildRestTemplate(3000, 3000);
                    log.info("config rest template by no proxy.");
                }
            }
        }
    }


    @Override
    public boolean assertDomainReachable() {
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
        log.info("refresh rest template headers...");
        headers.clear();
        headers.set(HttpHeaders.USER_AGENT, REST_TEMPLATE_USER_AGENT);
        headers.set(HttpHeaders.COOKIE, "chii_searchDateLine=0");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(accessToken)) {
            log.info("update http head access token");
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
            log.debug("Pull [{}] result is [{}].", url, result);
            if (StringUtils.isBlank(result)) {
                return null;
            }
            Map map = JsonUtils.json2obj(result, Map.class);
            Object infobox = map.remove("infobox");
            log.debug("Pull [{}] result infobox is [{}].", url, infobox);

            BgmTvSubject bgmTvSubject =
                JsonUtils.json2obj(JsonUtils.obj2Json(map), BgmTvSubject.class);

            if (Objects.nonNull(infobox)) {
                bgmTvSubject.setInfobox(convertInfoBox(JsonUtils.obj2Json(infobox)));
            }
            return bgmTvSubject;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("subject not found for subjectId={}", subjectId);
                return null;
            }
            throw exception;
        } catch (JsonProcessingException e) {
            log.error("convert infobox exception for subjectId={}", subjectId, e);
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
                                                      @Nullable Integer type) {
        Assert.hasText(keyword, "'keyword' must has text");
        // https://api.bgm.tv/search/subject/air?type=2&responseGroup=large
        UriComponentsBuilder uriComponentsBuilder =
            UriComponentsBuilder.fromHttpUrl(BgmTvApiConst.OLD_SEARCH_SUBJECT + "/" + keyword)
                .queryParam("responseGroup", "large");

        if (type != null) {
            uriComponentsBuilder.queryParam("type", type);
        }

        String url = BgmTvApiConst.OLD_SEARCH_SUBJECT + "/" + keyword + "?responseGroup=large";
        if (type != null) {
            url = url + "&type=" + type;
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
                                                      @Nullable BgmTvEpisodeType episodeType,
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
                .queryParam("limit", limit)
                .queryParam("offset", offset);

        if (Objects.nonNull(episodeType)) {
            uriComponentsBuilder.queryParam("type", episodeType.getCode());
        }


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
        List<String> authList = headers.get(HttpHeaders.AUTHORIZATION);
        if (authList == null || authList.isEmpty()) {
            return null;
        }
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

    @Override
    public void postUserSubjectCollection(String bgmTvSubId,
                                          BgmTVSubCollectionType bgmTVSubCollectionType,
                                          Boolean isPrivate) {
        Assert.hasText(bgmTvSubId, "'bgmTvSubId' must has text.");
        Assert.notNull(bgmTVSubCollectionType, "'bgmTVSubCollectionType' must not null.");
        final long subjectId = Long.parseLong(bgmTvSubId);
        final int collectionType = bgmTVSubCollectionType.getCode();
        final boolean collectionIsPrivate = Optional.ofNullable(isPrivate).orElse(false);

        BgmTvUserInfo me = getMe();
        if (Objects.isNull(me)) {
            return;
        }
        final String username = me.getUsername();

        try {

            // https://api.bgm.tv/v0/users/-/collections/{subjectId}
            final String url = BgmTvApiConst.USER_COLLECTIONS_SUBJECT + '/' + subjectId;

            Map<String, Object> body = new HashMap<>();
            body.put("type", collectionType);
            body.put("private", collectionIsPrivate);

            HttpEntity<String> request = new HttpEntity<>(JsonUtils.obj2Json(body), headers);

            restTemplate
                .exchange(url, HttpMethod.POST,
                    request, Map.class);
            log.info("Mark subject[{}] collection is [{}] with private[{}] for bgmtv user[{}}.",
                subjectId, bgmTVSubCollectionType.name(), collectionIsPrivate, username);
        } catch (HttpClientErrorException exception) {
            log.error("Post user subject collection stage fail", exception);
        }
    }

    @Override
    public void putUserEpisodeCollection(String bgmTvSubId, int sort, boolean isFinish,
                                         boolean isPrivate) {
        Assert.hasText(bgmTvSubId, "'bgmTvSubId' must has text.");
        Assert.isTrue(sort > 0, "'sort' must > 0.");

        Long subjectId = Long.parseLong(bgmTvSubId);
        // 先获取条目的所有剧集
        List<BgmTvEpisode> bgmTvEpisodes =
            findEpisodesBySubjectId(subjectId, BgmTvEpisodeType.POSITIVE, 0, 100);

        // 根据序号匹配过滤
        Optional<Integer> epIdOp = bgmTvEpisodes.stream()
            .filter(bgmTvEpisode -> sort == bgmTvEpisode.getSort().intValue())
            .map(BgmTvEpisode::getId)
            .findFirst();

        if (epIdOp.isEmpty()) {
            return;
        }


        Integer episodeId = epIdOp.get();
        // 更新剧集状态
        try {

            // https://api.bgm.tv/v0/users/-/collections/-/episodes/{episodeId}
            final String url =
                BgmTvApiConst.USER_COLLECTIONS_SUBJECT + "/-/episodes" + '/' + episodeId;

            Map<String, Object> body = new HashMap<>();
            // 2: 看过
            // 0: 未收藏
            body.put("type", isFinish ? 2 : 0);

            HttpEntity<String> request = new HttpEntity<>(JsonUtils.obj2Json(body), headers);

            restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
            log.info("Mark episode[{}] isFinish[{}] isPrivate[{}] for subject[{}] episode seq[{}].",
                episodeId, isFinish, isPrivate, subjectId, sort);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                Map map = exception.getResponseBodyAs(Map.class);
                Object description = map.get("description");
                if (description instanceof String
                    && "you need to add subject to your collection first".equalsIgnoreCase(
                    (String) description)) {
                    // 收藏条目，更新状态为在看
                    postUserSubjectCollection(bgmTvSubId, DOING, isPrivate);
                    putUserEpisodeCollection(bgmTvSubId, sort, isFinish, isPrivate);
                }
            } else {
                log.error("Put user episode collection fail, "
                        + "episode[{}] isFinish[{}] isPrivate[{}] for subject[{}] episode seq[{}].",
                    episodeId, isFinish, isPrivate, subjectId, sort, exception);
            }
        }

    }

    // @Override
    // public void patchSubjectEpisodeFinish(String bgmTvSubId, boolean isFinish,
    //                                       boolean isPrivate, List<Integer> bgmTvEpSorts) {
    //     Assert.hasText(bgmTvSubId, "'bgmTvSubId' must has text.");
    //
    //     if (bgmTvEpSorts == null || bgmTvEpSorts.isEmpty()) {
    //         log.warn("Skip patch, 'bgmTvEpSorts' is null nor empty: {}", bgmTvEpSorts);
    //         return;
    //     }
    //     Long subjectId = Long.parseLong(bgmTvSubId);
    //
    //     // 先获取条目的所有剧集
    //     List<BgmTvEpisode> bgmTvEpisodes =
    //         findEpisodesBySubjectId(subjectId, BgmTvEpisodeType.POSITIVE, 0, 100);
    //
    //     // 根据序号匹配过滤
    //     List<Integer> epIds = bgmTvEpisodes.stream()
    //         .filter(bgmTvEpisode -> bgmTvEpSorts.contains(bgmTvEpisode.getSort().intValue()))
    //         .map(BgmTvEpisode::getId)
    //         .toList();
    //
    //     // 收藏条目，更新状态为在看
    //     postUserSubjectCollection(String.valueOf(subjectId), BgmTVSubCollectionType.DOING, isPrivate);
    //
    //     // 更新所有的剧集状态
    //     try {
    //
    //         // https://api.bgm.tv/v0/users/-/collections/{subjectId}/episodes
    //         final String url =
    //             BgmTvApiConst.USER_COLLECTIONS_SUBJECT + '/' + subjectId + "/episodes";
    //
    //         Map<String, Object> body = new HashMap<>();
    //         // 2: 看过
    //         // 0: 未收藏
    //         body.put("type", isFinish ? 2 : 0);
    //         body.put("episode_id", epIds);
    //
    //         HttpEntity<String> request = new HttpEntity<>(JsonUtils.obj2Json(body), headers);
    //
    //         RestTemplate template =
    //             RestTemplateUtils.buildHttpComponentRestTemplate(null, null);
    //         template.exchange(url, HttpMethod.PATCH, request, Map.class);
    //         log.info("Mark subject[{}] isFinish=[{}] for episodes=[{}].",
    //             subjectId, isFinish, epIds);
    //     } catch (HttpClientErrorException exception) {
    //         log.error("Patch user subject collection episodes stage fail", exception);
    //     }
    //
    // }


    //@Override
    //public void afterPropertiesSet() throws Exception {
    // OptionEntity tokenOptionEntity =
    //     optionService.findOptionValueByCategoryAndKey(OptionCategory.BGMTV,
    //         OptionBgmTv.ACCESS_TOKEN.name());
    // if (tokenOptionEntity == null || StringUtils.isBlank(tokenOptionEntity.getValue())) {
    //     log.warn("current not set bgmtv access token");
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
