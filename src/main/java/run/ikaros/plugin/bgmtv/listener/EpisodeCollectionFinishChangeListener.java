package run.ikaros.plugin.bgmtv.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.collection.SubjectCollection;
import run.ikaros.api.core.collection.SubjectCollectionOperate;
import run.ikaros.api.core.collection.event.EpisodeCollectionFinishChangeEvent;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.core.subject.Episode;
import run.ikaros.api.core.subject.EpisodeOperate;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.api.core.subject.SubjectSync;
import run.ikaros.api.core.subject.SubjectSyncOperate;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.exception.NotFoundException;
import run.ikaros.api.store.enums.CollectionType;
import run.ikaros.api.store.enums.EpisodeGroup;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.plugin.bgmtv.BgmTvPlugin;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

@Slf4j
@Component
public class EpisodeCollectionFinishChangeListener {
    private final SubjectOperate subjectOperate;
    private final BgmTvRepository bgmTvRepository;
    private final ReactiveCustomClient customClient;
    private final SubjectCollectionOperate subjectCollectionOperate;
    private final EpisodeOperate episodeOperate;
    private final SubjectSyncOperate subjectSyncOperate;

    public EpisodeCollectionFinishChangeListener(SubjectOperate subjectOperate,
                                                 BgmTvRepository bgmTvRepository,
                                                 ReactiveCustomClient customClient,
                                                 SubjectCollectionOperate subjectCollectionOperate,
                                                 EpisodeOperate episodeOperate,
                                                 SubjectSyncOperate subjectSyncOperate) {
        this.subjectOperate = subjectOperate;
        this.bgmTvRepository = bgmTvRepository;
        this.customClient = customClient;
        this.subjectCollectionOperate = subjectCollectionOperate;
        this.episodeOperate = episodeOperate;
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

    @EventListener(EpisodeCollectionFinishChangeEvent.class)
    public synchronized void onApplicationReadyEvent(EpisodeCollectionFinishChangeEvent event) {
        log.debug("Receive EpisodeCollectionFinishChangeEvent: {}", event);
        final long episodeId = event.getEpisodeId();
        final boolean finish = event.isFinish();
        final long subjectId = event.getSubjectId();
        final long userId = event.getUserId();

        getConfigMapIsSync()
            .filter(isSync -> isSync)
            .flatMap(isSync -> getDoingBgmDoTvSubId(subjectId, userId))
            .subscribe(bgmTvSub -> getSubjectEpsSeq(episodeId, subjectId)
                .subscribe(seq -> getConfigMapNsfwIsPrivate()
                    .flatMap(nsfwPrivate -> subjectOperate.findById(subjectId)
                        .map(Subject::getNsfw)
                        .map(nsfw -> nsfw && nsfwPrivate))
                    .subscribe(
                        isPrivate ->
                            bgmTvRepository.putUserEpisodeCollection(bgmTvSub, seq,
                                finish, isPrivate))));
    }


    private Mono<Float> getSubjectEpsSeq(Long episodeId, Long subjectId) {
        return episodeOperate.findAllBySubjectId(subjectId)
            .filter(episode -> EpisodeGroup.MAIN.equals(episode.getGroup()))
            .filter(episode -> episodeId.equals(episode.getId()))
            .map(Episode::getSequence)
            .collectList()
            .filter(sequences -> !sequences.isEmpty())
            .map(sequences -> sequences.get(0));
    }

    private Mono<String> getDoingBgmDoTvSubId(Long subjectId, Long userId) {
        return subjectCollectionOperate.findCollection(userId, subjectId)
            .map(SubjectCollection::getType)
            .filter(CollectionType.DOING::equals)
            .flatMap(doing -> subjectSyncOperate.findSubjectSyncBySubjectIdAndPlatform(
                subjectId, SubjectSyncPlatform.BGM_TV
            ))
            .map(SubjectSync::getPlatformId);
    }

}
