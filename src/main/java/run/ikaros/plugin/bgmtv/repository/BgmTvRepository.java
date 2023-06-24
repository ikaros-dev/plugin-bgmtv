package run.ikaros.plugin.bgmtv.repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.plugin.bgmtv.model.*;

import java.util.List;


/**
 * @see run.ikaros.plugin.bgmtv.constants.BgmTvApiConst
 */
public interface BgmTvRepository {

    boolean assertDomainReachable();

    void setRestTemplate(@Nonnull RestTemplate restTemplate);

    void initRestTemplate(ConfigMap configMap);

    void refreshHttpHeaders(@Nullable String accessToken);

    @Nullable
    @Retryable
    BgmTvSubject getSubject(@Nonnull Long subjectId);

    /**
     * 还无法使用
     */
    @Retryable
    @Deprecated
    BgmTvPagingData<BgmTvSubject> searchSubjectWithNextApi(@Nonnull String keyword,
                                                           @Nullable Integer offset,
                                                           @Nullable Integer limit);

    /**
     * 还无法使用
     */
    @Retryable
    @Deprecated
    default BgmTvPagingData<BgmTvSubject> searchSubjectWithNextApi(@Nonnull String keyword) {
        Assert.hasText(keyword, "'keyword' must has text.");
        return searchSubjectWithNextApi(keyword, null, null);
    }


    @Retryable
    List<BgmTvSubject> searchSubjectWithOldApi(@Nonnull String keyword,
                                               @Nullable BgmTvSubjectType type);

    @Retryable
    default List<BgmTvSubject> searchSubjectWithOldApi(@Nonnull String keyword) {
        Assert.hasText(keyword, "'keyword' must has text.");
        return searchSubjectWithOldApi(keyword, null);
    }

    @Retryable
    byte[] downloadCover(@Nonnull String url);

    @Retryable
    List<BgmTvEpisode> findEpisodesBySubjectId(@Nonnull Long subjectId,
                                               @Nullable BgmTvEpisodeType episodeType,
                                               @Nullable Integer offset,
                                               @Nullable Integer limit);

    @Retryable
    BgmTvUserInfo getMe();
}
