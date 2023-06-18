package run.ikaros.plugin.bgmtv;

import org.pf4j.Extension;
import org.springframework.util.Assert;
import run.ikaros.api.core.subject.*;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.api.store.enums.SubjectType;
import run.ikaros.plugin.bgmtv.model.*;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepositoryImpl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class BgmTvSubjectSynchronizer implements SubjectSynchronizer {

    private BgmTvRepository bgmTvRepository = new BgmTvRepositoryImpl();

    @Override
    public SubjectSyncPlatform getSyncPlatform() {
        return SubjectSyncPlatform.BGM_TV;
    }

    @Override
    public Subject pull(String id) {
        Assert.hasText(id, "bgmtv id must has text.");
        bgmTvRepository.refreshHttpHeaders(null);

        Subject subject =
            convert(Objects.requireNonNull(bgmTvRepository.getSubject(Long.valueOf(id))));
        log.info("Pull subject:[{}] by platform:[{}] and id:[{}]",
            subject.getName(), getSyncPlatform().name(), id);

        List<Episode> episodes =
            bgmTvRepository.findEpisodesBySubjectId(Long.valueOf(id), BgmTvEpisodeType.POSITIVE,
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
            .setSequence(bgmTvEpisode.getEp());
    }

    private Subject convert(BgmTvSubject bgmTvSubject) {
        return new Subject()
            .setId(Long.valueOf(String.valueOf(bgmTvSubject.getId())))
            .setType(convertType(bgmTvSubject.getType()))
            .setName(bgmTvSubject.getName())
            .setNameCn(bgmTvSubject.getNameCn())
            .setInfobox(bgmTvSubject.getInfobox())
            .setSummary(bgmTvSubject.getSummary())
            .setNsfw(bgmTvSubject.getNsfw())
            .setAirTime(convertAirTime(bgmTvSubject.getDate()))
            .setImage(convertImage(bgmTvSubject.getImages()));
    }

    //private String convertInfoBox(List<BgmTvInfo> infobox) {
    //    StringBuilder sb = new StringBuilder();
    //    for (BgmTvInfo bgmTvInfo : infobox) {
    //        sb.append(bgmTvInfo.getKey())
    //            .append(":")
    //            .append(bgmTvInfo.getValue())
    //            .append("\n");
    //    }
    //    return sb.toString();
    //}

    private SubjectImage convertImage(BgmTvImages images) {
        return new SubjectImage()
            .setLarge(images.getLarge())
            .setCommon(images.getCommon())
            .setMedium(images.getMedium())
            .setSmall(images.getSmall())
            .setGrid(images.getGrid());
    }

    private LocalDateTime convertAirTime(String date) {
        final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
            .toFormatter();
        return LocalDateTime.parse(date, formatter);
    }

    private SubjectType convertType(BgmTvSubjectType type) {
        switch (type) {
            case BOOK -> {
                return SubjectType.NOVEL;
            }
            case ANIME -> {
                return SubjectType.ANIME;
            }
            case MUSIC -> {
                return SubjectType.MUSIC;
            }
            case GAME -> {
                return SubjectType.GAME;
            }
            case REAL -> {
                return SubjectType.REAL;
            }
            default -> {
                return SubjectType.OTHER;
            }
        }
    }


}