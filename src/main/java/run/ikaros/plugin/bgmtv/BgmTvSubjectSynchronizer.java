package run.ikaros.plugin.bgmtv;

import org.pf4j.Extension;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.subject.*;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.api.store.enums.SubjectType;
import run.ikaros.plugin.bgmtv.constants.BgmTvApiConst;
import run.ikaros.plugin.bgmtv.model.*;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepository;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepositoryImpl;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
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

        log.info("Verifying that the domain name is accessible, please wait...");
        boolean reachable = bgmTvRepository.assertDomainReachable();
        if (!reachable) {
            log.warn("The operation failed because the current domain name is not accessible "
                + "for domain: [{}].", BgmTvApiConst.BASE);
            throw new DomainNotAccessException(
                "Current domain can not access: " + BgmTvApiConst.BASE);
        }

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
            .setCover(bgmTvSubject.getImages().getLarge());
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
