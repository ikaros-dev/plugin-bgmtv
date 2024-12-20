package run.ikaros.plugin.bgmtv.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.collection.SubjectCollection;
import run.ikaros.api.core.collection.event.SubjectCollectEvent;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.api.core.subject.SubjectSync;
import run.ikaros.api.core.subject.SubjectSyncOperate;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.exception.NotFoundException;
import run.ikaros.api.store.enums.CollectionType;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.plugin.bgmtv.BgmTvPlugin;
import run.ikaros.plugin.bgmtv.model.BgmTVSubCollectionType;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

@Slf4j
@Component
public class SubjectCollectListener {
    private final BgmTvRepository bgmTvRepository;
    private final SubjectOperate subjectOperate;
    private final ReactiveCustomClient customClient;
    private final SubjectSyncOperate subjectSyncOperate;


    public SubjectCollectListener(BgmTvRepository bgmTvRepository, SubjectOperate subjectOperate,
                                  ReactiveCustomClient customClient,
                                  SubjectSyncOperate subjectSyncOperate) {
        this.bgmTvRepository = bgmTvRepository;
        this.subjectOperate = subjectOperate;
        this.customClient = customClient;
        this.subjectSyncOperate = subjectSyncOperate;
    }

    public Mono<Boolean> getConfigMapIsSync() {
        return getConfigMap("syncCollectionAndEpisodeFinish");
    }

    public Mono<Boolean> getConfigMapNsfwIsPrivate() {
        return getConfigMap("nsfwPrivate");
    }

    private Mono<Boolean> getConfigMap(String key) {
        return customClient.findOne(ConfigMap.class, BgmTvPlugin.NAME)
            .onErrorResume(NotFoundException.class, e -> Mono.empty())
            .map(ConfigMap::getData)
            .map(configMap ->
                StringUtils.isNotBlank(configMap.get(key))
                    && Boolean.TRUE.toString()
                    .equalsIgnoreCase(configMap.get(key)));
    }

    @EventListener(SubjectCollectEvent.class)
    public void onSubjectCollectEvent(SubjectCollectEvent event) {
        log.debug("Receive SubjectCollectEvent: {}", event);
        SubjectCollection subjectCollection = event.getSubjectCollection();
        final Long subjectId = subjectCollection.getSubjectId();
        final CollectionType collectionType = subjectCollection.getType();
        final BgmTVSubCollectionType bgmTVSubCollectionType =
            convertToBgmTvSubCollectionType(collectionType);
        getConfigMapIsSync()
            .filter(isSync -> isSync)
            .flatMap(isSync -> subjectSyncOperate.findSubjectSyncBySubjectIdAndPlatform(
                subjectId, SubjectSyncPlatform.BGM_TV
            )).map(SubjectSync::getPlatformId)

            .subscribe(bgmTvSubId -> getConfigMapNsfwIsPrivate()
                .flatMap(nsfwPrivate -> subjectOperate.findById(subjectId)
                    .map(Subject::getNsfw)
                    .map(nsfw -> nsfw && nsfwPrivate))
                .subscribe(isPrivate -> bgmTvRepository.postUserSubjectCollection(
                    bgmTvSubId, bgmTVSubCollectionType, isPrivate)));
    }

    private BgmTVSubCollectionType convertToBgmTvSubCollectionType(CollectionType collectionType) {
        final String name = collectionType.name();
        if (name.equalsIgnoreCase(BgmTVSubCollectionType.WISH.name())) {
            return BgmTVSubCollectionType.WISH;
        }
        if (name.equalsIgnoreCase(BgmTVSubCollectionType.DOING.name())) {
            return BgmTVSubCollectionType.DOING;
        }
        if (name.equalsIgnoreCase(BgmTVSubCollectionType.DONE.name())) {
            return BgmTVSubCollectionType.DONE;
        }
        if (name.equalsIgnoreCase(BgmTVSubCollectionType.SHELVE.name())) {
            return BgmTVSubCollectionType.SHELVE;
        }
        if (name.equalsIgnoreCase(BgmTVSubCollectionType.DISCARD.name())) {
            return BgmTVSubCollectionType.DISCARD;
        }
        return BgmTVSubCollectionType.WISH;
    }
}
