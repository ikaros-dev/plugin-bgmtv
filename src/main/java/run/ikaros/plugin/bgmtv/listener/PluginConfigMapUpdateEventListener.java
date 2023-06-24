package run.ikaros.plugin.bgmtv.listener;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.plugin.event.PluginConfigMapChangeEvent;
import run.ikaros.plugin.bgmtv.DomainNotAccessException;
import run.ikaros.plugin.bgmtv.constants.BgmTvApiConst;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PluginConfigMapUpdateEventListener
    implements ApplicationListener<PluginConfigMapChangeEvent> {
    private final BgmTvRepository bgmTvRepository;

    public PluginConfigMapUpdateEventListener(BgmTvRepository bgmTvRepository) {
        this.bgmTvRepository = bgmTvRepository;
    }

    @Override
    public void onApplicationEvent(PluginConfigMapChangeEvent event) {
        ConfigMap configMap = event.getConfigMap();
        bgmTvRepository.initRestTemplate(configMap);
        String token = null;
        if (Objects.nonNull(configMap.getData()) &&
            StringUtils.isNotBlank(String.valueOf(configMap.getData().get("token")))) {
            token = String.valueOf(configMap.getData().get("token"));
        }
        bgmTvRepository.refreshHttpHeaders(token);

//        log.info("Verifying that the domain name is accessible, please wait...");
//        boolean reachable = bgmTvRepository.assertDomainReachable();
//        if (!reachable) {
//            log.warn("The operation failed because the current domain name is not accessible "
//                + "for domain: [{}].", BgmTvApiConst.BASE);
//            throw new DomainNotAccessException(
//                "Current domain can not access: " + BgmTvApiConst.BASE);
//        }
    }
}
