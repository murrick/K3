package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.7 ground truth aggregation and provisional hypothesis provenance. */
public class FrontierAggregateTest {

    @Test
    void truePlusUnknownIsTrue() {
        RevisionRef a = ref(1L);
        RevisionRef b = ref(2L);

        FrontierAggregate aggregate =
                FrontierAggregate.of(Arrays.asList(
                        answer(a, FrontierAnswer.Truth.TRUE),
                        answer(b, FrontierAnswer.Truth.NULL)));

        assertEquals(
                FrontierAggregate.Truth.TRUE,
                aggregate.getTruth());
        assertEquals(
                Collections.singleton(a),
                aggregate.getTrueSources());
        assertEquals(
                Collections.singleton(b),
                aggregate.getUnknownSources());
        assertTrue(
                aggregate.getFalseSources().isEmpty());
    }

    @Test
    void falsePlusUnknownIsFalse() {
        RevisionRef a = ref(3L);
        RevisionRef b = ref(4L);

        FrontierAggregate aggregate =
                FrontierAggregate.of(Arrays.asList(
                        answer(a, FrontierAnswer.Truth.FALSE),
                        answer(b, FrontierAnswer.Truth.NULL)));

        assertEquals(
                FrontierAggregate.Truth.FALSE,
                aggregate.getTruth());
        assertEquals(
                Collections.singleton(a),
                aggregate.getFalseSources());
        assertEquals(
                Collections.singleton(b),
                aggregate.getUnknownSources());
    }

    @Test
    void truePlusFalseIsConflict() {
        RevisionRef a = ref(5L);
        RevisionRef b = ref(6L);

        FrontierAggregate aggregate =
                FrontierAggregate.of(Arrays.asList(
                        answer(a, FrontierAnswer.Truth.TRUE),
                        answer(b, FrontierAnswer.Truth.FALSE)));

        assertEquals(
                FrontierAggregate.Truth.CONFLICT,
                aggregate.getTruth());
        assertEquals(
                Collections.singleton(a),
                aggregate.getTrueSources());
        assertEquals(
                Collections.singleton(b),
                aggregate.getFalseSources());
    }

    @Test
    void nullOnlyIsUnknown() {
        RevisionRef a = ref(7L);
        RevisionRef b = ref(8L);

        FrontierAggregate aggregate =
                FrontierAggregate.of(Arrays.asList(
                        answer(a, FrontierAnswer.Truth.NULL),
                        answer(b, FrontierAnswer.Truth.NULL)));

        assertEquals(
                FrontierAggregate.Truth.UNKNOWN,
                aggregate.getTruth());
        assertEquals(2,
                aggregate.getUnknownSources().size());
        assertTrue(
                aggregate.getTrueSources().isEmpty());
        assertTrue(
                aggregate.getFalseSources().isEmpty());
    }

    @Test
    void emptyAnswerSetIsUnknown() {
        FrontierAggregate aggregate =
                FrontierAggregate.of(
                        Collections.<FrontierAnswer>emptyList());

        assertEquals(
                FrontierAggregate.Truth.UNKNOWN,
                aggregate.getTruth());
        assertTrue(
                aggregate.getUnknownSources().isEmpty());
    }

    @Test
    void hypothesesKeepExactSourceAndDoNotAffectTruth() {
        RevisionRef a = ref(9L);
        RevisionRef b = ref(10L);

        FrontierAnswer one =
                new FrontierAnswer(
                        a,
                        FrontierAnswer.Truth.NULL,
                        Collections.<String>emptyList(),
                        Collections.<java.util.List<FrontierAnswer.ValueRef>>emptyList(),
                        Arrays.asList(
                                "!maybe(Tom);",
                                "!maybe(Tom);"));
        FrontierAnswer two =
                new FrontierAnswer(
                        b,
                        FrontierAnswer.Truth.NULL,
                        Collections.<String>emptyList(),
                        Collections.<java.util.List<FrontierAnswer.ValueRef>>emptyList(),
                        Collections.singletonList(
                                "!maybe(Tom);"));

        FrontierAggregate aggregate =
                FrontierAggregate.of(
                        Arrays.asList(one, two));

        assertEquals(
                FrontierAggregate.Truth.UNKNOWN,
                aggregate.getTruth());
        assertEquals(2,
                aggregate.getHypotheses().size());
        assertEquals(
                a,
                aggregate.getHypotheses()
                        .get(0).getSource());
        assertEquals(
                "!maybe(Tom);",
                aggregate.getHypotheses()
                        .get(0).getStatement());
        assertEquals(
                b,
                aggregate.getHypotheses()
                        .get(1).getSource());
    }

    private FrontierAnswer answer(
            RevisionRef source,
            FrontierAnswer.Truth truth) {
        return new FrontierAnswer(
                source,
                truth,
                Collections.<String>emptyList(),
                Collections.<java.util.List<FrontierAnswer.ValueRef>>emptyList(),
                Collections.<String>emptyList());
    }

    private RevisionRef ref(long revision) {
        return new RevisionRef(
                UUID.fromString(
                        "00000000-0000-0000-0000-"
                                + String.format(
                                        "%012d",
                                        revision)),
                revision);
    }
}
