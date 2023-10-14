package run.ikaros.plugin.bgmtv;

import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import run.ikaros.api.constant.AppConst;
import run.ikaros.api.core.file.File;
import run.ikaros.api.core.file.FileOperate;
import run.ikaros.api.core.file.FolderOperate;
import run.ikaros.api.core.subject.*;
import run.ikaros.api.infra.utils.FileUtils;
import run.ikaros.api.store.enums.EpisodeGroup;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.api.store.enums.SubjectType;
import run.ikaros.plugin.bgmtv.model.*;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static run.ikaros.api.constant.FileConst.DEFAULT_FOLDER_ROOT_ID;
import static run.ikaros.plugin.bgmtv.model.BgmTvEpisodeType.POSITIVE;

@Slf4j
@Extension
public class BgmTvSubjectSynchronizer implements SubjectSynchronizer {

    private final BgmTvRepository bgmTvRepository;
    private final FileOperate fileOperate;
    private final FolderOperate folderOperate;

    public BgmTvSubjectSynchronizer(BgmTvRepository bgmTvRepository, FileOperate fileOperate,
                                    FolderOperate folderOperate) {
        this.bgmTvRepository = bgmTvRepository;
        this.fileOperate = fileOperate;
        this.folderOperate = folderOperate;
    }

    @Override
    public SubjectSyncPlatform getSyncPlatform() {
        return SubjectSyncPlatform.BGM_TV;
    }


    @Override
    public synchronized Mono<Subject> pull(String id) {
        Assert.hasText(id, "bgmtv id must has text.");

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
                .map(this::convertEpisode)
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
            return fileOperate.upload(coverFileName,
                    Mono.just(dataBufferFactory.wrap(bytes)).flux())
                .flatMap(
                    file -> folderOperate.findByParentIdAndName(DEFAULT_FOLDER_ROOT_ID, "cover")
                        .switchIfEmpty(folderOperate.create(DEFAULT_FOLDER_ROOT_ID, "cover"))
                        .flatMap(folder -> fileOperate.updateFolder(file.getId(), folder.getId())))
                .map(File::getUrl)
                .map(subject::setCover);
        }
        return Mono.just(subject);
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

        return Mono.just(subject);
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
                ? bgmTvEpisode.getSort().intValue() : bgmTvEpisode.getEp()));
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
            case SPECIAL -> {
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
