package run.ikaros.plugin.bgmtv.repository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.plugin.bgmtv.model.BgmTVSubCollectionType;
import run.ikaros.plugin.bgmtv.model.BgmTvSubject;
import run.ikaros.plugin.bgmtv.model.BgmTvUserInfo;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BgmTvRepositoryImplTest {

    BgmTvRepositoryImpl bgmTvRepository = new BgmTvRepositoryImpl(null);

    @Test
    @Disabled
    void getSubject() {
        bgmTvRepository.initRestTemplate(null);
        bgmTvRepository.refreshHttpHeaders(null);
        BgmTvSubject subject = bgmTvRepository.getSubject(2716L);
        assertThat(subject).isNotNull();
    }

    @Test
    @Disabled
    void getMe() {
        ConfigMap configMap = new ConfigMap();
        configMap.setName("PluginBgmTv");
        configMap.putDataItem("enableProxy", "false");
        bgmTvRepository.initRestTemplate(configMap);
        bgmTvRepository.refreshHttpHeaders(System.getenv("IKAROS_TEST_TOKEN"));
        BgmTvUserInfo userInfo = bgmTvRepository.getMe();
        assertThat(userInfo).isNotNull();
        BgmTvSubject subject = bgmTvRepository.getSubject(74446L);
        assertThat(subject).isNotNull();
    }

    @Test
    @Disabled
    void postUserSubjectCollection() {
        final long subjectId = 405198;
        bgmTvRepository.initRestTemplate(null);
        bgmTvRepository.refreshHttpHeaders(System.getenv("IKAROS_TEST_TOKEN"));
        bgmTvRepository.postUserSubjectCollection(String.valueOf(subjectId),
            BgmTVSubCollectionType.DONE, true);
    }

    // @Test
    // @Disabled
    // void patchSubjectEpisodeFinish() {
    //     final long subjectId = 107671;
    //     bgmTvRepository.initRestTemplate(null);
    //     bgmTvRepository.refreshHttpHeaders(System.getenv("IKAROS_TEST_TOKEN"));
    //
    //     List<Integer> sortIds = new ArrayList<>();
    //     sortIds.add(1);
    //     sortIds.add(2);
    //     sortIds.add(3);
    //     sortIds.add(4);
    //
    //     bgmTvRepository.patchSubjectEpisodeFinish(String.valueOf(subjectId), true, false, sortIds);
    //
    // }


    @Test
    @Disabled
    void putUserEpisodeCollection() {
        final long subjectId = 373787;
        bgmTvRepository.initRestTemplate(null);
        bgmTvRepository.refreshHttpHeaders(System.getenv("IKAROS_TEST_TOKEN"));

        bgmTvRepository.putUserEpisodeCollection(String.valueOf(subjectId), 1, true, false);
    }
}