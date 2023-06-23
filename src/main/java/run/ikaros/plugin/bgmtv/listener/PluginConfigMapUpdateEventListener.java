package run.ikaros.plugin.bgmtv.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.plugin.event.PluginConfigMapChangeEvent;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

import java.util.Objects;

@Component
public class PluginConfigMapUpdateEventListener implements ApplicationListener<PluginConfigMapChangeEvent> {
    private final BgmTvRepository bgmTvRepository;

    public PluginConfigMapUpdateEventListener(BgmTvRepository bgmTvRepository) {
        this.bgmTvRepository = bgmTvRepository;
    }

    @Override
    public void onApplicationEvent(PluginConfigMapChangeEvent event) {
        ConfigMap configMap = event.getConfigMap();
        bgmTvRepository.initRestTemplate(configMap);
        String token = null;
        if(Objects.nonNull(configMap.getData())) {
            token = configMap.getData().get("token");
        }
        bgmTvRepository.refreshHttpHeaders(token);
    }
}
