package run.ikaros.plugin.bgmtv.repository;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import run.ikaros.plugin.bgmtv.model.BgmTvSubject;

import static org.junit.jupiter.api.Assertions.*;

class BgmTvRepositoryImplTest {

    BgmTvRepositoryImpl bgmTvRepository = new BgmTvRepositoryImpl(null);

    @Test
    void getSubject() {
        bgmTvRepository.initRestTemplate(null);
        bgmTvRepository.refreshHttpHeaders(null);
        BgmTvSubject subject = bgmTvRepository.getSubject(2716L);
        Assertions.assertThat(subject).isNotNull();
    }
}