package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.FrontierDemand;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.SemanticTermSnapshot;
import org.kanger.User;
import org.kanger.enums.DataType;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IHypothesis;
import org.kanger.primitives.Hypothesis;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Full exact-target response and operation-local invocation isolation. */
public class CausalFrontierResponseTest {
    @TempDir
    Path root;

    @Test
    void freeReorderedDemandSurvivesTargetRuntimeCloseWithoutProof() throws Exception {
        ContextFixture a = context("A", "!@a @b parent(a,b) -> ancestor(b,a);");
        byte[] before = generationDigest(a);
        FrontierInvocation invocation = invocation("?$right $left ancestor(right,left);");
        FrontierAnswer answer = execute(a, invocation);
        assertEquals(FrontierAnswer.Truth.NULL, answer.getTruth());
        assertTrue(answer.getInvocation().sameAddress(invocation));
        assertEquals(a.ref, answer.getSource());
        assertEquals(Arrays.asList("right", "left"), answer.getVariableOrder());
        assertTrue(answer.getValues().isEmpty());
        FrontierDemand demand = onlyDemand(answer);
        assertEquals("ancestor", demand.getParent().getPredicateName());
        assertEquals("parent", demand.getQuery().getPredicateName());
        assertEquals(1, demand.getParentProjection().getChildPosition(0));
        assertEquals(0, demand.getParentProjection().getChildPosition(1));
        List<SemanticTermSnapshot> candidate = demand.getParentProjection()
                .project(values("John", "Mary"));
        assertEquals("Mary", candidate.get(0).materialize().toString());
        assertEquals("John", candidate.get(1).materialize().toString());
        assertThrows(UnsupportedOperationException.class, () -> answer.getUnresolvedFrontiers().clear());
        assertEquals(FrontierAggregate.Truth.UNKNOWN,
                FrontierAggregate.of(Collections.singletonList(answer)).getTruth());
        assertArrayEquals(before, generationDigest(a));
    }

    @Test
    void partiallyBoundChildRetainsTypedSemanticFixedArgument() throws Exception {
        ContextFixture a = context("A-string", "!@a @b parent(a,b) -> ancestor(b,a);");
        FrontierAnswer answer = execute(a, invocation("?$person ancestor('42',person);"));
        FrontierDemand demand = onlyDemand(answer);
        Mind donor = offlineMind();
        ITerm fixed = demand.getQuery().projectFixedArguments(donor).remove();
        assertEquals(DataType.STRING, fixed.getType());
        assertEquals("42", fixed.getValue());
        assertNotNull(demand.getParentProjection().project(values("John", "'42'")));
        assertNull(demand.getParentProjection().project(values("John", 42)));
        assertTrue(answer.getValues().isEmpty());
    }

    @Test
    void falsePassDemandKeepsActualPolarityWithinOriginalInvocation() throws Exception {
        ContextFixture a = context("A-negative", "!@x ~p(x) -> ~q(x);");
        FrontierInvocation invocation = invocation("?q(John);");
        FrontierAnswer answer = execute(a, invocation);
        assertEquals(FrontierAnswer.Truth.NULL, answer.getTruth());
        assertFalse(answer.getInvocation().getFrontier().isNegated());
        FrontierDemand demand = onlyDemand(answer);
        assertTrue(demand.getParent().isNegated());
        assertTrue(demand.getQuery().isNegated());
        assertEquals("p", demand.getQuery().getPredicateName());
        assertTrue(answer.getInvocation().sameAddress(invocation));
    }

    @Test
    void locallyProvenParentReturnsOnlyItsOwnOrderedValues() throws Exception {
        ContextFixture a = context("A-proven", "!@a @b parent(a,b) -> ancestor(b,a);",
                "!parent(John,Mary);", "!parent(Tom,Other);");
        FrontierInvocation invocation = invocation("?$right $left ancestor(right,left);");
        FrontierAnswer answer = execute(a, invocation);
        assertEquals(FrontierAnswer.Truth.TRUE, answer.getTruth());
        assertTrue(answer.getUnresolvedFrontiers().isEmpty());
        assertEquals(Arrays.asList("right", "left"), answer.getVariableOrder());
        Set<String> rows = new LinkedHashSet<String>();
        for (List<FrontierAnswer.ValueRef> row : answer.getValues()) {
            rows.add(row.get(0).getRendered() + "/" + row.get(1).getRendered());
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList("Mary/John", "Other/Tom")), rows);
        assertTrue(answer.getInvocation().sameAddress(invocation));
    }

    @Test
    void explicitNegativeProofIsResolvedAndCannotCarryDemands() throws Exception {
        ContextFixture a = context("A-false", "!~q(John);");
        FrontierInvocation invocation = invocation("?q(John);");
        FrontierAnswer answer = execute(a, invocation);
        assertEquals(FrontierAnswer.Truth.FALSE, answer.getTruth());
        assertTrue(answer.getUnresolvedFrontiers().isEmpty());
        ContextFixture blocked = context("A-blocked", "!@x p(x) -> q(x);");
        FrontierAnswer unresolved = execute(blocked, invocation);
        assertThrows(IllegalArgumentException.class, () -> new FrontierAnswer(
                a.ref, invocation, FrontierAnswer.Truth.TRUE, answer.getVariableOrder(),
                answer.getValues(), answer.getHypotheses(), unresolved.getUnresolvedFrontiers()));
    }

    @Test
    void exactOldRevisionStillReturnsDemandAfterCurrentBecomesProvable() throws Exception {
        ContextFixture old = context("A-pinned", "!@x p(x) -> q(x);");
        byte[] before = generationDigest(old);
        ContextFixture current = append(old, "!p(John);");
        assertTrue(current.ref.getRevision() > old.ref.getRevision());
        FrontierInvocation invocation = invocation("?q(John);");
        FrontierAnswer oldAnswer = execute(old, invocation);
        FrontierAnswer newAnswer = execute(current, invocation);
        assertEquals(FrontierAnswer.Truth.NULL, oldAnswer.getTruth());
        assertEquals("p", onlyDemand(oldAnswer).getQuery().getPredicateName());
        assertEquals(old.ref, oldAnswer.getSource());
        assertEquals(FrontierAnswer.Truth.TRUE, newAnswer.getTruth());
        assertTrue(newAnswer.getUnresolvedFrontiers().isEmpty());
        assertEquals(current.ref, newAnswer.getSource());
        assertArrayEquals(before, generationDigest(old));
        assertEquals(current.ref, currentRef(old.location));
    }

    @Test
    void childTruthAndRepeatedQueryTruthCannotAggregateIntoParentInvocation() throws Exception {
        ContextFixture b = context("B", "!@x q(x) -> r(x);");
        ContextFixture c = context("C", "!q(John);");
        FrontierInvocation parent = invocation("?r(John);");
        FrontierAnswer blocked = execute(b, parent);
        FrontierInvocation child = FrontierInvocation.create(onlyDemand(blocked).getQuery());
        FrontierAnswer provenChild = execute(c, child);
        assertEquals(FrontierAnswer.Truth.TRUE, provenChild.getTruth());
        assertFalse(parent.sameAddress(child));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierAggregate.of(Arrays.asList(blocked, provenChild)));
        FrontierInvocation repeated = FrontierInvocation.create(child.getFrontier());
        assertNotEquals(child.getId(), repeated.getId());
        assertThrows(IllegalArgumentException.class, () -> FrontierAggregate.of(
                Arrays.asList(provenChild, execute(c, repeated))));
        assertEquals(FrontierAnswer.Truth.NULL, execute(b, parent).getTruth());
    }

    @Test
    void mixedInvocationLiftFailsBeforeCreatingAnyForeignTerms() throws Exception {
        ContextFixture x = context("X-lift", "!resident(Rick,Vienna);");
        ContextFixture a = context("A-lift", "!q(ForeignDonor);");
        ConnectionVector vector = ConnectionVector.empty().with(
                ConnectionManager.qualifyConnect(x.location, a.location));
        FrontierInvocation one = invocation("?$value q(value);");
        FrontierInvocation two = FrontierInvocation.create(one.getFrontier());
        FrontierAnswer first = execute(a, one);
        FrontierAnswer second = execute(a, two);
        Mind target = offlineMind();
        int terms = target.getTerms().size();
        try (OperationSnapshot operation = OperationSnapshot.open(x.location, vector)) {
            assertThrows(IllegalArgumentException.class, () -> FrontierLiftSession.liftInto(
                    target, operation, Arrays.asList(first, second)));
        }
        assertEquals(terms, target.getTerms().size());
        assertNull(target.getTerms().find("ForeignDonor"));
        assertTrue(target.getValues().isEmpty());
        try (OperationSnapshot operation = OperationSnapshot.open(x.location, vector)) {
            FrontierLiftSession.LiftResult lifted = FrontierLiftSession.liftInto(
                    target, operation, Collections.singletonList(first));
            assertTrue(one.sameAddress(lifted.getInvocation()));
            assertEquals(1, lifted.getTuples().size());
            try (FrontierLiftSession session = FrontierLiftSession.open(
                    operation, Collections.singletonList(first))) {
                assertTrue(one.sameAddress(session.getInvocation()));
            }
        }
    }

    @Test
    void fanOutSharesOneInvocationAndRetainsIndependentExactTargetDemands() throws Exception {
        ContextFixture x = context("X-fanout", "!resident(Rick,Vienna);");
        ContextFixture a = context("A-fanout", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-fanout", "!@x s(x) -> q(x);");
        ConnectionVector vector = ConnectionVector.empty()
                .with(ConnectionManager.qualifyConnect(x.location, a.location))
                .with(ConnectionManager.qualifyConnect(x.location, b.location));
        FrontierInvocation invocation = invocation("?$value q(value);");
        try (OperationSnapshot operation = OperationSnapshot.open(x.location, vector)) {
            List<FrontierAnswer> answers = FrontierFanOut.execute(operation, invocation);
            assertEquals(2, answers.size());
            Set<RevisionRef> sources = new LinkedHashSet<RevisionRef>();
            Set<String> children = new LinkedHashSet<String>();
            for (FrontierAnswer answer : answers) {
                assertTrue(invocation.sameAddress(answer.getInvocation()));
                assertEquals(FrontierAnswer.Truth.NULL, answer.getTruth());
                sources.add(answer.getSource());
                children.add(onlyDemand(answer).getQuery().getPredicateName());
            }
            assertEquals(new LinkedHashSet<RevisionRef>(Arrays.asList(a.ref, b.ref)), sources);
            assertEquals(new LinkedHashSet<String>(Arrays.asList("p", "s")), children);
            FrontierAggregate aggregate = FrontierAggregate.of(answers);
            assertEquals(FrontierAggregate.Truth.UNKNOWN, aggregate.getTruth());
            assertTrue(invocation.sameAddress(aggregate.getInvocation()));
        }
    }

    @Test
    void targetWithPublishedConnectionReturnsDemandWithoutFollowingIt() throws Exception {
        ContextFixture a = context("A-local", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-local", "!p(John);");
        ConnectionManager.connect(a.location, c.location);
        ContextFixture connected = new ContextFixture(a.name, a.location, currentRef(a.location));
        byte[] before = generationDigest(connected);
        FrontierAnswer answer = execute(connected, invocation("?q(John);"));
        assertEquals(FrontierAnswer.Truth.NULL, answer.getTruth());
        assertEquals("p", onlyDemand(answer).getQuery().getPredicateName());
        assertArrayEquals(before, generationDigest(connected));
        assertEquals(connected.ref, currentRef(a.location));
    }

    @Test
    void unresolvedResponsePreservesNativeHypothesesSeparatelyFromDemand() throws Exception {
        ContextFixture a = context("A-hypotheses", "!@x p(x) -> q(x);");
        FrontierInvocation invocation = invocation("?q(John);");
        List<String> nativeHypotheses = new ArrayList<String>();
        try (SnapshotMindRuntime runtime = SnapshotMindRuntime.open(a.location, a.ref, "native-control")) {
            Mind work = Mind.ephemeralChild(runtime.getMind());
            try {
                assertNull(work.queryCanonical(invocation.getFrontier().getQuerySource(),
                        invocation.getFrontier().projectFixedArguments(work), false));
                for (IHypothesis hypothesis : work.getHypothesis()) {
                    nativeHypotheses.add(((Hypothesis) hypothesis).toString(work));
                }
            } finally {
                runtime.getMind().discardEphemeral(work);
            }
        }
        FrontierAnswer answer = execute(a, invocation);
        assertEquals(nativeHypotheses, answer.getHypotheses());
        assertFalse(nativeHypotheses.isEmpty(), "native control must exercise the hypothesis surface");
        assertEquals("p", onlyDemand(answer).getQuery().getPredicateName());
        assertEquals(FrontierAggregate.Truth.UNKNOWN,
                FrontierAggregate.of(Collections.singletonList(answer)).getTruth());
        assertTrue(answer.getValues().isEmpty());
    }

    private FrontierDemand onlyDemand(FrontierAnswer answer) {
        assertEquals(1, answer.getUnresolvedFrontiers().size());
        return answer.getUnresolvedFrontiers().get(0);
    }

    private FrontierAnswer execute(ContextFixture target, FrontierInvocation invocation) throws Exception {
        return LocalFrontierExecutor.execute(target.location, target.ref, invocation);
    }

    private FrontierInvocation invocation(String query) throws Exception {
        Mind mind = offlineMind();
        assertNull(mind.queryCanonical(query, null, false));
        assertEquals(1, mind.getFrontierDomains().size());
        return FrontierInvocation.create(mind.getFrontierDomains().get(0));
    }

    private Mind offlineMind() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        return mind;
    }

    private List<SemanticTermSnapshot> values(Object... values) throws Exception {
        Mind mind = offlineMind();
        mind.getTerms().add("Padding");
        List<SemanticTermSnapshot> result = new ArrayList<SemanticTermSnapshot>();
        for (Object value : values) {
            result.add(SemanticTermSnapshot.capture(mind.getTerms().add(value)));
        }
        return result;
    }

    private ContextFixture context(String name, String... statements) throws Exception {
        Path directory = root.resolve(name + "-db");
        Files.createDirectories(directory);
        return write(name, directory.resolve(name), statements);
    }

    private ContextFixture append(ContextFixture fixture, String... statements) throws Exception {
        return write(fixture.name, fixture.location, statements);
    }

    private ContextFixture write(String name, Path location, String... statements) throws Exception {
        User user = new User();
        user.setDatabaseDir(location.getParent().toString() + File.separator);
        DB data = new DB();
        data.init(user);
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);
        try {
            for (String statement : statements) {
                assertEquals(Boolean.TRUE, mind.query(statement, null, false), statement);
            }
            return new ContextFixture(name, location,
                    new RevisionRef(data.getContextId(), data.getRevision()));
        } finally {
            user.setCurrentMind(mind.closeStorage());
        }
    }

    private RevisionRef currentRef(Path location) throws Exception {
        try (ContextSnapshot snapshot = ContextSnapshot.open(location)) {
            return new RevisionRef(snapshot.getContextId(), snapshot.getRevision());
        }
    }

    private byte[] generationDigest(ContextFixture fixture) throws Exception {
        Path generation = ContextStore.generationPath(fixture.location, fixture.ref.getRevision());
        List<Path> files;
        try (Stream<Path> stream = Files.walk(generation)) {
            files = stream.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path file : files) {
            digest.update(Files.readAllBytes(file));
        }
        return digest.digest();
    }

    private static final class ContextFixture {
        final String name;
        final Path location;
        final RevisionRef ref;

        ContextFixture(String name, Path location, RevisionRef ref) {
            this.name = name;
            this.location = location;
            this.ref = ref;
        }
    }
}
