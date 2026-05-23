package run.ikaros.plugin.bgmtv;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.meta.DelegateMetaService;
import run.ikaros.api.core.meta.MetaInfoExtensionPoint;
import run.ikaros.api.core.subject.Episode;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.infra.utils.UuidV7Utils;
import run.ikaros.api.store.enums.EpisodeGroup;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.api.store.enums.SubjectType;
import run.ikaros.plugin.bgmtv.model.BgmTvEpisode;
import run.ikaros.plugin.bgmtv.model.BgmTvEpisodeType;
import run.ikaros.plugin.bgmtv.model.BgmTvSubject;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;
import run.ikaros.plugin.bgmtv.utils.AssertUtils;

@Slf4j
@Extension
public class BgmTvMetaService implements MetaInfoExtensionPoint {

    private final BgmTvRepository bgmTvRepository;

    public BgmTvMetaService(BgmTvRepository bgmTvRepository) {
        this.bgmTvRepository = bgmTvRepository;
    }

    @Override
    public SubjectSyncPlatform getPlatform() {
        return SubjectSyncPlatform.BGM_TV;
    }

    @Override
    public Flux<Subject> searchSubjects(String keyword) {
        List<BgmTvSubject> bgmTvSubjects = bgmTvRepository.searchSubjectWithOldApi(keyword, 2);
        if (bgmTvSubjects != null && !bgmTvSubjects.isEmpty()) {
            return Flux.fromIterable(bgmTvSubjects)
                .map(this::convertSubject);
        }
        return Flux.empty();
    }

    @Override
    public Mono<Subject> getSubjectByPlatformId(String platformId) {
        BgmTvSubject bgmTvSubject = bgmTvRepository.getSubject(Long.parseLong(platformId));
        if (bgmTvSubject != null) {
            return Mono.just(convertSubject(bgmTvSubject));
        }
        return Mono.empty();
    }

    @Override
    public Flux<Episode> getEpisodesByPlatformId(String platformId) {
        List<BgmTvEpisode> episodes = bgmTvRepository.findEpisodesBySubjectId(
            Long.parseLong(platformId),
            BgmTvEpisodeType.POSITIVE,
            null,
            null);
        if (episodes != null && !episodes.isEmpty()) {
            return Flux.fromIterable(episodes)
                .map(this::convertEpisode);
        }
        return Flux.empty();
    }

    @Override
    public Flux<String> getTagsByPlatformId(String platformId) {
        throw new UnsupportedOperationException();
    }

    private Subject convertSubject(BgmTvSubject bgmTvSubject) {
        return new Subject()
            .setId(UuidV7Utils.generateUuid())
            .setType(convertType(bgmTvSubject.getType(), bgmTvSubject.getPlatform()))
            .setName(bgmTvSubject.getName())
            .setNameCn(StringUtils.isNotBlank(bgmTvSubject.getNameCn())
                ? bgmTvSubject.getNameCn() : bgmTvSubject.getName())
            .setInfobox(bgmTvSubject.getInfobox());
    }

    private Episode convertEpisode(BgmTvEpisode bgmTvEpisode) {
        log.debug("Pull episode:[{}] form by platform:[{}]",
            bgmTvEpisode.getName(), getPlatform());
        return new Episode()
            .setName(bgmTvEpisode.getName())
            .setNameCn(bgmTvEpisode.getNameCn())
            .setDescription(bgmTvEpisode.getDesc())
            .setAirTime(convertAirTime(bgmTvEpisode.getAirDate()))
            .setGroup(convertEpisodeType(bgmTvEpisode.getType()))
            .setSequence((Objects.nonNull(bgmTvEpisode.getSort())
                ? bgmTvEpisode.getSort().floatValue() : bgmTvEpisode.getEp()));
    }

    private Episode convertMusicEpisode(BgmTvEpisode bgmTvEpisode) {
        log.debug("Pull episode:[{}] form by platform:[{}]",
            bgmTvEpisode.getName(), getPlatform());
        float originalSeq = Objects.nonNull(bgmTvEpisode.getSort())
            ? bgmTvEpisode.getSort().floatValue() : bgmTvEpisode.getEp();
        originalSeq = Float.parseFloat(bgmTvEpisode.getDisc() + originalSeq);
        return new Episode()
            .setName(bgmTvEpisode.getName())
            .setNameCn(bgmTvEpisode.getNameCn())
            .setDescription(bgmTvEpisode.getDesc())
            .setAirTime(convertAirTime(bgmTvEpisode.getAirDate()))
            .setGroup(convertEpisodeType(bgmTvEpisode.getType()))
            .setSequence(originalSeq);
    }

    private LocalDateTime convertAirTime(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
            .toFormatter();
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(date, formatter);
        } catch (Exception e) {
            log.warn("convert air time fail:", e);
            dateTime = null;
        }
        return dateTime;
    }

    private SubjectType convertType(Integer type, String platform) {
        if (Objects.isNull(type)) {
            return SubjectType.OTHER;
        }
        switch (type) {
            case 1 -> {
                return (StringUtils.isNotBlank(platform) && "小说".equalsIgnoreCase(platform))
                    ? SubjectType.NOVEL : SubjectType.COMIC;
            }
            case 2 -> {
                return SubjectType.ANIME;
            }
            case 3 -> {
                return SubjectType.MUSIC;
            }
            case 4 -> {
                return SubjectType.GAME;
            }
            case 6 -> {
                return SubjectType.REAL;
            }
            default -> {
                return SubjectType.OTHER;
            }
        }
    }


    private EpisodeGroup convertEpisodeType(BgmTvEpisodeType type) {
        if (Objects.isNull(type)) {
            return EpisodeGroup.OTHER;
        }

        switch (type) {
            case POSITIVE -> {
                return EpisodeGroup.MAIN;
            }
            case SPECIAL, MAD -> {
                return EpisodeGroup.SPECIAL_PROMOTION;
            }
            case OP -> {
                return EpisodeGroup.OPENING_SONG;
            }
            case ED -> {
                return EpisodeGroup.ENDING_SONG;
            }
            case PV -> {
                return EpisodeGroup.PROMOTION_VIDEO;
            }
            default -> {
                return EpisodeGroup.OTHER;
            }
        }
    }
}
