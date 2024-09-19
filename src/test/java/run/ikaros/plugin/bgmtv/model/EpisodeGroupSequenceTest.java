package run.ikaros.plugin.bgmtv.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import run.ikaros.api.store.enums.EpisodeGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EpisodeGroupSequenceTest {

    @Test
    void testEquals() {
        EpisodeGroup group = EpisodeGroup.MAIN;
        Float seq = new Random().nextFloat();

        EpisodeGroupSequence groupSequence = EpisodeGroupSequence
            .builder().group(group).sequence(seq).build();

        EpisodeGroupSequence newGroupSeq = EpisodeGroupSequence
            .builder().group(group).sequence(seq).build();

        Assertions.assertThat(groupSequence).isEqualTo(newGroupSeq);


        Map<EpisodeGroupSequence, Float> map = new HashMap<>();
        map.put(groupSequence, seq);

        Assertions.assertThat(map.containsKey(newGroupSeq)).isTrue();
    }
}