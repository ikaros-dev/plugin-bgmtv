package run.ikaros.plugin.bgmtv.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import run.ikaros.api.core.collection.event.EpisodeCollectionFinishChangeEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EpisodeCollectionFinishChangeListener
    implements ApplicationListener<EpisodeCollectionFinishChangeEvent> {
    @Override
    public void onApplicationEvent(EpisodeCollectionFinishChangeEvent event) {
        log.debug("Receive EpisodeCollectionFinishChangeEvent: {}", event);
    }
}
