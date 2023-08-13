package run.ikaros.plugin.bgmtv.repository;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.plugin.bgmtv.model.BgmTvSubject;
import run.ikaros.plugin.bgmtv.model.BgmTvUserInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
}