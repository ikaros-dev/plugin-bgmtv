package run.ikaros.plugin.bgmtv.listener;

import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.collection.event.EpisodeCollectionFinishChangeEvent;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.core.subject.Episode;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.api.core.subject.SubjectSync;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.exception.NotFoundException;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.plugin.bgmtv.BgmTvPlugin;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EpisodeCollectionFinishChangeListener {
    private final SubjectOperate subjectOperate;
    private final BgmTvRepository bgmTvRepository;
    private final ReactiveCustomClient customClient;

    private Map<Long, List<Long>> finishEpisodeIdMap = new HashMap<>();
    private Map<Long, List<Long>> notFinishEpisodeIdMap = new HashMap<>();
    private boolean loop = true;
    private LocalDateTime lastPushTime = LocalDateTime.now();

    public EpisodeCollectionFinishChangeListener(SubjectOperate subjectOperate,
                                                 BgmTvRepository bgmTvRepository,
                                                 ReactiveCustomClient customClient) {
        this.subjectOperate = subjectOperate;
        this.bgmTvRepository = bgmTvRepository;
        this.customClient = customClient;
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
        if (finish) {
            if (finishEpisodeIdMap.containsKey(subjectId)) {
                List<Long> episodes = finishEpisodeIdMap.get(subjectId);
                if (episodes == null) {
                    episodes = new ArrayList<>();
                }
                episodes.add(episodeId);
            } else {
                List<Long> episodes = new ArrayList<>();
                episodes.add(episodeId);
                finishEpisodeIdMap.put(subjectId, episodes);
            }
        } else {
            if (notFinishEpisodeIdMap.containsKey(subjectId)) {
                List<Long> episodes = notFinishEpisodeIdMap.get(subjectId);
                if (episodes == null) {
                    episodes = new ArrayList<>();
                }
                episodes.add(episodeId);
            } else {
                List<Long> episodes = new ArrayList<>();
                episodes.add(episodeId);
                notFinishEpisodeIdMap.put(subjectId, episodes);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        LocalDateTime now = LocalDateTime.now();
        while (loop && now.isAfter(lastPushTime.minusMinutes(5L))) {
            lastPushTime = LocalDateTime.now();
            pushEpisodeIds(finishEpisodeIdMap, true);
            pushEpisodeIds(notFinishEpisodeIdMap, false);
        }
    }

    private void pushEpisodeIds(Map<Long, List<Long>> episodeIdMap, boolean finish) {
        getConfigMapIsSync()
            .filter(isSync -> isSync)
            .flatMapMany(isSync -> Flux.fromStream(episodeIdMap.keySet().stream()))
            .filter(subjectId -> !episodeIdMap.get(subjectId).isEmpty())
            .subscribe(subjectId -> getSubjectEpsSeqArr(episodeIdMap.get(subjectId), subjectId)
                .subscribe(epSeqList ->
                    getConfigMapNsfwIsPrivate()
                        .flatMap(nsfwPrivate -> subjectOperate.findById(subjectId)
                            .map(Subject::getNsfw)
                            .map(nsfw -> nsfw && nsfwPrivate))
                        .subscribe(isPrivate ->
                            doPatchBgmTvCollectionSubEps(subjectId, finish, isPrivate, epSeqList)
                        )));
    }

    private void doPatchBgmTvCollectionSubEps(Long subjectId, boolean finish,
                                              boolean isPrivate, List<Integer> epSeqList) {
        getBgmTvSubId(subjectId).subscribe(bgmTvSubId -> {
            bgmTvRepository.patchSubjectEpisodeFinish(bgmTvSubId, finish, isPrivate, epSeqList);
            log.info("Mark finish={} for subjectId={} and episode seq={}",
                finish, subjectId, epSeqList);
        });
    }

    private Mono<List<Integer>> getSubjectEpsSeqArr(List<Long> episodeIds, Long subjectId) {
        return subjectOperate.findById(subjectId)
            .flatMapMany(subject -> Flux.fromStream(subject.getEpisodes().stream()))
            .filter(episode -> episodeIds.contains(episode.getId()))
            .map(Episode::getSequence)
            .collectList();
    }

    private Mono<String> getBgmTvSubId(Long subjectId) {
        return subjectOperate.findById(subjectId)
            .flatMapMany(subject -> Flux.fromStream(subject.getSyncs().stream()))
            .filter(subjectSync -> SubjectSyncPlatform.BGM_TV.equals(subjectSync.getPlatform()))
            .collectList()
            .map(subjectSyncs -> subjectSyncs.get(0))
            .map(SubjectSync::getPlatformId);
    }

    @PreDestroy
    public void release() {
        loop = false;
    }
}
