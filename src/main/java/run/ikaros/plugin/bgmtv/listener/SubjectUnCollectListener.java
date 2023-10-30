package run.ikaros.plugin.bgmtv.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import run.ikaros.api.core.collection.event.SubjectUnCollectEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SubjectUnCollectListener implements ApplicationListener<SubjectUnCollectEvent> {
    @Override
    public void onApplicationEvent(SubjectUnCollectEvent event) {
        // log.debug("Receive SubjectUnCollectEvent subject collection[{}].", event.getSubjectCollection());
        // log.warn("Not support uncollect subject collection by api for bgm.tv");
    }
}
