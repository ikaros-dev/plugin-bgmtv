package run.ikaros.plugin.bgmtv.model;

import run.ikaros.api.store.enums.EpisodeGroup;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class EpisodeGroupSequence {
    private EpisodeGroup group;
    private Integer sequence;
}
