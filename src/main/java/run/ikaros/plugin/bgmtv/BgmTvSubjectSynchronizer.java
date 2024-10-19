package run.ikaros.plugin.bgmtv;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import run.ikaros.api.core.attachment.AttachmentOperate;
import run.ikaros.api.core.character.Character;
import run.ikaros.api.core.person.Person;
import run.ikaros.api.core.subject.Episode;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectSynchronizer;
import run.ikaros.api.core.tag.Tag;
import run.ikaros.api.store.enums.EpisodeGroup;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.api.store.enums.SubjectType;
import run.ikaros.plugin.bgmtv.model.BgmTvEpisode;
import run.ikaros.plugin.bgmtv.model.BgmTvEpisodeType;
import run.ikaros.plugin.bgmtv.model.BgmTvSubject;
import run.ikaros.plugin.bgmtv.model.BgmTvTag;
import run.ikaros.plugin.bgmtv.model.EpisodeGroupSequence;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

@Slf4j
@Extension
public class BgmTvSubjectSynchronizer implements SubjectSynchronizer {

    private final BgmTvRepository bgmTvRepository;
    private final AttachmentOperate attachmentOperate;

    private ThreadLocal<BgmTvSubject> subjectThreadLocal = new ThreadLocal<>();

    public BgmTvSubjectSynchronizer(BgmTvRepository bgmTvRepository,
                                    AttachmentOperate attachmentOperate) {
        this.bgmTvRepository = bgmTvRepository;
        this.attachmentOperate = attachmentOperate;
    }


    @Override
    public SubjectSyncPlatform getSyncPlatform() {
        return SubjectSyncPlatform.BGM_TV;
    }

    @Override
    public Subject fetchSubjectWithPlatformId(String platformId) {
        BgmTvSubject bgmTvSubject = bgmTvRepository.getSubject(Long.valueOf(platformId));
        subjectThreadLocal.set(bgmTvSubject);
        if (Objects.isNull(bgmTvSubject)) {
            return null;
        }
        return new Subject()
            .setId(Long.valueOf(String.valueOf(bgmTvSubject.getId())))
            .setType(convertType(bgmTvSubject.getType(), bgmTvSubject.getPlatform()))
            .setName(bgmTvSubject.getName())
            .setNameCn(StringUtils.isNotBlank(bgmTvSubject.getNameCn())
                ? bgmTvSubject.getNameCn() : bgmTvSubject.getName())
            .setInfobox(bgmTvSubject.getInfobox())
            .setSummary(bgmTvSubject.getSummary())
            .setNsfw(bgmTvSubject.getNsfw())
            .setAirTime(convertAirTime(
                Objects.nonNull(bgmTvSubject.getDate()) ? bgmTvSubject.getDate() : "1999-09-09"))
            .setCover(bgmTvSubject.getImages().getLarge());
    }

    @Override
    public List<Episode> fetchEpisodesWithPlatformId(String platformId) {
        BgmTvSubject bgmTvSubject = subjectThreadLocal.get();
        return bgmTvRepository.findEpisodesBySubjectId(Long.valueOf(platformId), null,
                null, null)
            .stream()
            .map(bgmTvEpisode -> bgmTvSubject.getType() == 3 ?
                convertMusicEpisode(bgmTvEpisode) : convertEpisode(bgmTvEpisode))
            .toList();
    }

    @Override
    public List<Tag> fetchTagsWithPlatformId(String platformId) {
        BgmTvSubject bgmTvSubject = subjectThreadLocal.get();
        return bgmTvSubject.getTags().stream()
            .map(BgmTvTag::getName)
            .map(name -> Tag.builder()
                .name(name)
                .build())
            .toList();
    }

    @Override
    public List<Person> fetchPersonsWithPlatformId(String platformId) {
        // TODO 条目人物相关
        return List.of();
    }

    @Override
    public List<Character> fetchCharactersWithPlatformId(String platformId) {
        // TODO 条目角色相关
        return List.of();
    }

    private Subject mergeBgmTvSubject(Subject subject, BgmTvSubject bgmTvSubject) {
        if (Objects.isNull(bgmTvSubject)) {
            return subject;
        }
        return subject
            .setType(convertType(bgmTvSubject.getType(), bgmTvSubject.getPlatform()))
            .setName(bgmTvSubject.getName())
            .setNameCn(StringUtils.isNotBlank(bgmTvSubject.getNameCn())
                ? bgmTvSubject.getNameCn() : bgmTvSubject.getName())
            .setInfobox(bgmTvSubject.getInfobox())
            .setSummary(bgmTvSubject.getSummary())
            .setNsfw(bgmTvSubject.getNsfw())
            .setAirTime(convertAirTime(bgmTvSubject.getDate()));
    }


    private Subject mergeBgmtvSubjectEpisodes(Subject subject, List<Episode> episodes) {
        if (Objects.isNull(episodes) || episodes.isEmpty()) {
            return subject;
        }

        Map<EpisodeGroupSequence, Episode> groupSeqEpMap = new HashMap<>();
        List<Episode> newEpisodes = new ArrayList<>();


        /*
        for (Episode episode : subject.getEpisodes()) {
            EpisodeGroupSequence groupSequence = EpisodeGroupSequence
                    .builder().group(episode.getGroup()).sequence(episode.getSequence()).build();
            groupSeqEpMap.put(groupSequence, episode);
        }

        for (Episode episode : episodes) {
            EpisodeGroupSequence groupSequence = EpisodeGroupSequence
                .builder().group(episode.getGroup()).sequence(episode.getSequence()).build();
            if (groupSeqEpMap.containsKey(groupSequence)) {
                Episode ep = groupSeqEpMap.get(groupSequence);
                ep.setName(episode.getName())
                    .setNameCn(episode.getNameCn())
                    .setDescription(episode.getDescription())
                    .setAirTime(episode.getAirTime())
                    .setGroup(episode.getGroup())
                    .setSequence(episode.getSequence());
                newEpisodes.add(ep);
                log.info("Merge episode name:[{}] group:[{}] and seq:[{}]",
                    ep.getName(), ep.getGroup(), ep.getSequence());
            } else {
                newEpisodes.add(episode);
                log.info("Pull episode name:[{}] group:[{}] and seq:[{}]",
                    episode.getName(), episode.getGroup(), episode.getSequence());
            }
        }*/

        return subject;
    }

    private Episode convertEpisode(BgmTvEpisode bgmTvEpisode) {
        log.debug("Pull episode:[{}] form by platform:[{}]",
            bgmTvEpisode.getName(), getSyncPlatform());
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
            bgmTvEpisode.getName(), getSyncPlatform());
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
