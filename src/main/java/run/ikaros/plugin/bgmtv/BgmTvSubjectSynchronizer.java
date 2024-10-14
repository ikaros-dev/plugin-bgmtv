package run.ikaros.plugin.bgmtv;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.attachment.Attachment;
import run.ikaros.api.core.attachment.AttachmentConst;
import run.ikaros.api.core.attachment.AttachmentOperate;
import run.ikaros.api.core.attachment.AttachmentUploadCondition;
import run.ikaros.api.core.subject.Episode;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectSync;
import run.ikaros.api.core.subject.SubjectSynchronizer;
import run.ikaros.api.core.tag.Tag;
import run.ikaros.api.core.tag.TagOperate;
import run.ikaros.api.infra.utils.FileUtils;
import run.ikaros.api.store.enums.EpisodeGroup;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.api.store.enums.SubjectType;
import run.ikaros.api.store.enums.TagType;
import run.ikaros.plugin.bgmtv.model.*;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Extension
public class BgmTvSubjectSynchronizer implements SubjectSynchronizer {

    private final BgmTvRepository bgmTvRepository;
    private final AttachmentOperate attachmentOperate;
    private final TagOperate tagOperate;

    public BgmTvSubjectSynchronizer(BgmTvRepository bgmTvRepository,
                                    AttachmentOperate attachmentOperate, TagOperate tagOperate) {
        this.bgmTvRepository = bgmTvRepository;
        this.attachmentOperate = attachmentOperate;
        this.tagOperate = tagOperate;
    }

    @Override
    public SubjectSyncPlatform getSyncPlatform() {
        return SubjectSyncPlatform.BGM_TV;
    }


    @Override
    public synchronized Mono<Subject> pull(String id) {
        Assert.hasText(id, "bgmtv id must has text.");

        if (id.startsWith("https://")) {
            id = id.replace("https://bgm.tv/subject/", "");
        }

        BgmTvSubject bgmTvSubject = bgmTvRepository.getSubject(Long.valueOf(id));
        if (bgmTvSubject == null || bgmTvSubject.getId() == null) {
            log.warn("Pull subject is null, skip operate.");
            return Mono.empty();
        }

        Subject subject =
                convert(Objects.requireNonNull(bgmTvSubject));
        if (Objects.isNull(subject)) {
            log.warn("Pull subject is null, skip operate.");
            return Mono.empty();
        }

        log.info("Pull subject:[{}] by platform:[{}] and id:[{}]",
                subject.getName(), getSyncPlatform().name(), id);

        List<Episode> episodes =
                bgmTvRepository.findEpisodesBySubjectId(Long.valueOf(id), null,
                                null, null)
                        .stream()
                        .map(bgmTvEpisode -> SubjectType.MUSIC.equals(subject.getType()) ?
                                convertMusicEpisode(bgmTvEpisode) : convertEpisode(bgmTvEpisode))
                        .toList();
        log.info("Pull episode count:[{}] by platform:[{}] and id:[{}]",
                episodes.size(), getSyncPlatform().name(), id);
        subject.setEpisodes(episodes);
        subject.setTotalEpisodes((long) episodes.size());
        subject.setSyncs(List.of(
                new SubjectSync()
                        .setSyncTime(LocalDateTime.now())
                        .setPlatform(getSyncPlatform())
                        .setPlatformId(id)));

        // save bgmtv tags
        Set<String> bgmTvSubTagNames = bgmTvSubject.getTags().stream()
                .map(BgmTvTag::getName).collect(Collectors.toSet());
        Mono<List<Tag>> tagsMono = Flux.fromStream(bgmTvSubTagNames.parallelStream())
                .map(tagName -> Tag.builder()
                        .createTime(LocalDateTime.now())
                        .type(TagType.SUBJECT)
                        .masterId(subject.getId())
                        .name(tagName)
                        .build())
                .flatMap(tagOperate::create)
                .collectList();

        // download cover image and update url
        if (StringUtils.isNotBlank(subject.getCover())
                && subject.getCover().startsWith("http")) {
            String coverUrl = subject.getCover();
            String coverFileName = StringUtils.isNotBlank(subject.getNameCn())
                    ? subject.getNameCn() : subject.getName();
            coverFileName =
                    System.currentTimeMillis() + "-" + coverFileName
                            + "." + FileUtils.parseFilePostfix(FileUtils.parseFileName(coverUrl));
            byte[] bytes = bgmTvRepository.downloadCover(coverUrl);
            DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
            return tagsMono.then(attachmentOperate.upload(AttachmentUploadCondition.builder()
                            .parentId(AttachmentConst.COVER_DIRECTORY_ID)
                            .name(coverFileName).dataBufferFlux(Mono.just(dataBufferFactory.wrap(bytes)).flux())
                            .build()))
                    .map(Attachment::getUrl)
                    .map(subject::setCover);
        }
        return tagsMono.then(Mono.just(subject));
    }

    @Override
    public Mono<Subject> merge(Subject subject, String platformId) {
        Assert.notNull(subject, "subject must not null.");
        Assert.hasText(platformId, "bgmtv id must has text.");

        // search bgmtv subject info
        BgmTvSubject bgmTvSubject = bgmTvRepository.getSubject(Long.valueOf(platformId));
        if (bgmTvSubject == null || bgmTvSubject.getId() == null) {
            log.warn("Pull subject is null, skip operate.");
            return Mono.empty();
        }

        // merge bgmtv subject
        subject = mergeBgmTvSubject(subject, bgmTvSubject);
        log.info("Merge subject:[{}] by platform:[{}] and id:[{}]",
                Objects.requireNonNull(subject).getName(), getSyncPlatform().name(), platformId);

        // merge bgmtv subject episodes
        List<Episode> episodes =
                bgmTvRepository.findEpisodesBySubjectId(Long.valueOf(platformId), null,
                                null, null)
                        .stream()
                        .map(this::convertEpisode)
                        .toList();

        subject = mergeBgmtvSubjectEpisodes(subject, episodes);

        // save sync relation when not exists
        List<SubjectSync> syncs = subject.getSyncs();
        if (syncs == null) {
            syncs = new ArrayList<>();
        }
        syncs.add(SubjectSync.builder()
                .subjectId(subject.getId())
                .platform(SubjectSyncPlatform.BGM_TV)
                .syncTime(LocalDateTime.now())
                .platformId(platformId).build());
        subject.setSyncs(syncs);

        DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();


        // merge tags
        return tagOperate.findAll(TagType.SUBJECT, subject.getId(), null)
                .map(Tag::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet())
                .flatMapMany(existsTags ->
                        Flux.fromStream(bgmTvSubject.getTags().parallelStream())
                        .map(BgmTvTag::getName)
                        .filter(bgmTvTagName -> !existsTags.contains(bgmTvTagName))
                )
                .map(name -> Tag.builder()
                        .type(TagType.SUBJECT)
                        .masterId(Long.valueOf(bgmTvSubject.getId()))
                        .name(name)
                        .createTime(LocalDateTime.now())
                        .build())
                .flatMap(tagOperate::create)
                .collectList()
                // merge cover
                .then(Mono.just(subject))
                .filter(sub -> StringUtils.isBlank(sub.getCover()))
                .filter(cover -> StringUtils.isNotBlank(bgmTvSubject.getImages().getLarge()))
                .map(cover -> bgmTvSubject.getImages().getLarge())
                .map(bgmTvRepository::downloadCover)
                .map(bytes -> AttachmentUploadCondition.builder()
                        .parentId(AttachmentConst.COVER_DIRECTORY_ID)
                        .name(
                                System.currentTimeMillis()
                                        + "-" + (StringUtils.isNotBlank(bgmTvSubject.getNameCn())
                                        ? bgmTvSubject.getNameCn() : bgmTvSubject.getName())
                                        + "." + FileUtils.parseFilePostfix(FileUtils.parseFileName(bgmTvSubject.getImages().getLarge()))
                        )
                        .dataBufferFlux(Mono.just(dataBufferFactory.wrap(bytes)).flux())
                        .build())
                .flatMap(attachmentOperate::upload)
                .map(Attachment::getUrl)
                .map(subject::setCover)

                .then(Mono.just(subject));
    }

    @Override
    public Mono<Subject> pullSelfAndRelations(String s) {
        // TODO pull self and all relations
        return Mono.empty();
    }

    private Subject mergeBgmTvSubject(Subject subject, BgmTvSubject bgmTvSubject) {
        if (Objects.isNull(bgmTvSubject)) {
            return subject;
        }
        return subject
                .setType(convertType(bgmTvSubject.getType(), bgmTvSubject.getPlatform()))
                .setName(bgmTvSubject.getName())
                .setNameCn(bgmTvSubject.getNameCn())
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
        }

        return subject.setEpisodes(newEpisodes);
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


    private Subject convert(BgmTvSubject bgmTvSubject) {
        if (Objects.isNull(bgmTvSubject)) {
            return null;
        }
        return new Subject()
                .setId(Long.valueOf(String.valueOf(bgmTvSubject.getId())))
                .setType(convertType(bgmTvSubject.getType(), bgmTvSubject.getPlatform()))
                .setName(bgmTvSubject.getName())
                .setNameCn(bgmTvSubject.getNameCn())
                .setInfobox(bgmTvSubject.getInfobox())
                .setSummary(bgmTvSubject.getSummary())
                .setNsfw(bgmTvSubject.getNsfw())
                .setAirTime(convertAirTime(
                        Objects.nonNull(bgmTvSubject.getDate()) ? bgmTvSubject.getDate() : "1999-09-09"))
                .setCover(bgmTvSubject.getImages().getLarge());
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
