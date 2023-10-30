package run.ikaros.plugin.bgmtv;


import org.pf4j.PluginWrapper;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.stereotype.Component;
import run.ikaros.api.plugin.BasePlugin;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableRetry
public class BgmTvPlugin extends BasePlugin {

    public static final String NAME = "PluginBgmTv";

    public BgmTvPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("plugin [PluginBgmTv] start success");
    }

    @Override
    public void stop() {
        log.info("plugin [PluginBgmTv] stop success");
    }

    @Override
    public void delete() {
        log.info("plugin [PluginBgmTv] delete success");
    }
}