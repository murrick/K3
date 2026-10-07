package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.enums.DataType;

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

/** Automatic causal fixed point over exact direct targets. */
public class CausalFrontierSchedulerTest {
    @TempDir Path root;

    @Test
    void groundChainRequiresEveryAncestorNativeProof() throws Exception {
        ContextFixture x = context("X", "!anchor(X);");
        ContextFixture a = context("A", "!@x p(x) -> q(x);");
        ContextFixture b = context("B", "!@x q(x) -> r(x);");
        ContextFixture c = context("C", "!p(John);");
        byte[] before = digest(a);
        CausalFrontierScheduler.Result result = schedule(x, "?r(John);", a, b, c);
        assertEquals(FrontierAggregate.Truth.TRUE, FrontierAggregate.of(result.getAnswers()).getTruth());
        assertEquals(5, result.getNodeCount());
        assertEquals(19, result.getCalls()); // fifteen initial calls plus four native reproofs
        FrontierAnswer proven = result.getAnswers().stream().filter(v -> v.getTruth() == FrontierAnswer.Truth.TRUE).findFirst().get();
        assertEquals(b.ref, proven.getSource());
        assertTrue(proven.getRequest().getEvidence().get(0).matches(invocation("?q(John);").getFrontier()));
        assertArrayEquals(before, digest(a));
        assertEquals(FrontierAnswer.Truth.NULL, LocalFrontierExecutor.execute(b.location, b.ref, invocation("?r(John);")).getTruth());
    }

    @Test
    void independentJohnMaryValuesReachRootOnlyAfterParentProof() throws Exception {
        ContextFixture x = context("X-values", "!anchor(X);");
        ContextFixture a = context("A-values", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-values", "!@x q(x) -> r(x);");
        ContextFixture c = context("C-values", "!p(John);", "!p(Mary);");
        CausalFrontierScheduler.Result result = schedule(x, "?$root r(root);", a, b, c);
        assertEquals(3, result.getNodeCount());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("John", "Mary")), trueRows(result));
        for (FrontierAnswer answer : result.getAnswers()) {
            assertTrue(answer.getInvocation().sameAddress(result.getAnswers().get(0).getInvocation()));
            assertEquals(Collections.singletonList("root"), answer.getVariableOrder());
        }
    }

    @Test
    void laterChildTupleReopensAlreadyTrueAncestors() throws Exception {
        ContextFixture x = context("X-late", "!anchor(X);");
        ContextFixture a = context("A-late", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-late", "!@x q(x) -> r(x);");
        ContextFixture c = context("C-late", "!p(John);");
        ContextFixture d = context("D-late", "!@x s(x) -> p(x);");
        ContextFixture e = context("E-late", "!s(Mary);");
        CausalFrontierScheduler.Result result = schedule(x, "?$root r(root);", a, b, c, d, e);
        assertEquals(4, result.getNodeCount());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("John", "Mary")), trueRows(result));
        assertTrue(result.getCalls() > 22);
    }

    @Test
    void reorderedTwoAncestorChainUsesSemanticPositions() throws Exception {
        ContextFixture x = context("X-order", "!anchor(X);");
        ContextFixture a = context("A-order", "!@a @b pair(a,b) -> middle(b,a);");
        ContextFixture b = context("B-order", "!@a @b middle(a,b) -> result(b,a);");
        ContextFixture c = context("C-order", "!pair(John,Mary);", "!pair(Tom,Other);");
        CausalFrontierScheduler.Result result = schedule(x, "?$left $right result(left,right);", b, c, a);
        assertEquals(new LinkedHashSet<String>(Arrays.asList("John/Mary", "Tom/Other")), trueRows(result));
    }

    @Test
    void unseededCycleTerminatesWithoutInventingFacts() throws Exception {
        ContextFixture x = context("X-cycle", "!anchor(X);");
        ContextFixture a = context("A-cycle", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-cycle", "!@x q(x) -> p(x);");
        CausalFrontierScheduler.Result result = schedule(x, "?$value q(value);", a, b);
        assertEquals(2, result.getNodeCount());
        assertEquals(4, result.getCalls());
        assertEquals(FrontierAggregate.Truth.UNKNOWN, FrontierAggregate.of(result.getAnswers()).getTruth());
        assertTrue(trueRows(result).isEmpty());
    }

    @Test
    void seededCycleReachesFiniteNativeFixedPoint() throws Exception {
        ContextFixture x = context("X-seed", "!anchor(X);");
        ContextFixture a = context("A-seed", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-seed", "!@x q(x) -> p(x);");
        ContextFixture c = context("C-seed", "!p(John);");
        CausalFrontierScheduler.Result result = schedule(x, "?$value q(value);", a, b, c);
        assertEquals(2, result.getNodeCount());
        assertEquals(Collections.singleton("John"), trueRows(result));
        assertTrue(result.getCalls() < 10);
    }

    @Test
    void conflictingChildNeverProvesParent() throws Exception {
        ContextFixture x = context("X-conflict", "!anchor(X);");
        ContextFixture a = context("A-conflict", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-conflict", "!p(John);");
        ContextFixture d = context("D-conflict", "!~p(John);");
        CausalFrontierScheduler.Result result = schedule(x, "?q(John);", a, c, d);
        assertEquals(FrontierAggregate.Truth.UNKNOWN, FrontierAggregate.of(result.getAnswers()).getTruth());
        assertEquals(9, result.getCalls());
        assertTrue(trueRows(result).isEmpty());
    }

    @Test
    void lateConflictWithdrawsEarlierParentEvidence() throws Exception {
        ContextFixture x = context("X-withdraw", "!anchor(X);");
        ContextFixture a = context("A-withdraw", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-withdraw", "!p(John);");
        ContextFixture d = context("D-withdraw", "!@x s(x) -> ~p(x);");
        ContextFixture e = context("E-withdraw", "!s(John);");
        CausalFrontierScheduler.Result result = schedule(x, "?q(John);", a, c, d, e);
        assertEquals(FrontierAggregate.Truth.UNKNOWN, FrontierAggregate.of(result.getAnswers()).getTruth());
        assertTrue(trueRows(result).isEmpty());
        for (FrontierAnswer answer : result.getAnswers()) assertTrue(answer.getRequest().getEvidence().isEmpty());
    }

    @Test
    void negativeChainReturnsNativeFalseForOriginalPositiveQuery() throws Exception {
        ContextFixture x = context("X-negative", "!anchor(X);");
        ContextFixture a = context("A-negative", "!@x ~p(x) -> ~q(x);");
        ContextFixture c = context("C-negative", "!~p(John);");
        CausalFrontierScheduler.Result result = schedule(x, "?q(John);", a, c);
        assertEquals(FrontierAggregate.Truth.FALSE, FrontierAggregate.of(result.getAnswers()).getTruth());
        assertTrue(trueRows(result).isEmpty());
    }

    @Test
    void ordinaryContinuationConsumesOnlyProvenRootAnswer() throws Exception {
        ContextFixture x = context("X-normal", "!anchor(X);");
        ContextFixture a = context("A-normal", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-normal", "!@x q(x) -> r(x);");
        ContextFixture c = context("C-normal", "!p(John);");
        FrontierContinuationEngine.Result result = FrontierContinuationEngine.execute(x.location, vector(x,a,b,c), "?r(John);");
        assertTrue(result.isResolved());
        assertTrue(result.getProvisionalHypotheses().isEmpty());
        assertEquals(1, result.getEvidenceCount());
        assertEquals("r(John)", result.getEvidenceInjections().get(0).getStatement().replace("?", "").replace(";", ""));
    }

    @Test
    void ordinaryMindQueryReturnsIndependentJohnMaryRootValues() throws Exception {
        ContextFixture x = context("X-live", "!anchor(X);");
        ContextFixture a = context("A-live", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-live", "!@x q(x) -> r(x);");
        ContextFixture c = context("C-live", "!p(John);", "!p(Mary);");
        liveQuery(x, "?$answer r(answer);", new LinkedHashSet<String>(Arrays.asList("John", "Mary")), a, b, c);
    }

    @Test
    void childFactMayComeFromLocalXWithoutReplayingItsUserLayer() throws Exception {
        ContextFixture x = context("X-local", "!p(John);", "!p(Mary);");
        ContextFixture a = context("A-local", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-local", "!@x q(x) -> r(x);");
        liveQuery(x, "?$answer r(answer);", new LinkedHashSet<String>(Arrays.asList("John", "Mary")), a, b);
    }

    @Test
    void unrelatedBlockedChildConflictCannotOverrideIndependentRootProof() throws Exception {
        ContextFixture x = context("X-alternative", "!anchor(X);");
        ContextFixture a = context("A-alternative", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-alternative", "!p(John);");
        ContextFixture d = context("D-alternative", "!~p(John);");
        ContextFixture e = context("E-alternative", "!q(John);");
        FrontierContinuationEngine.Result result = FrontierContinuationEngine.execute(x.location, vector(x,a,c,d,e), "?q(John);");
        assertTrue(result.isResolved());
        assertFalse(result.hasConflict()); // truth is addressed to q; the blocked p branch contributes no q proof
    }

    @Test
    void causalChildConflictRemainsVisibleWithoutDonors() throws Exception {
        ContextFixture x = context("X-diagnostic", "!anchor(X);");
        ContextFixture a = context("A-diagnostic", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-diagnostic", "!p(John);");
        ContextFixture d = context("D-diagnostic", "!~p(John);");
        FrontierContinuationEngine.Result result = FrontierContinuationEngine.execute(x.location, vector(x,a,c,d), "?q(John);");
        assertFalse(result.isResolved());
        assertTrue(result.hasConflict());
        assertTrue(result.getProvisionalHypotheses().isEmpty());
        assertEquals(0, result.getEvidenceCount());
    }

    @Test
    void operationKeepsExactOldDonorPinAcrossCurrentAdvance() throws Exception {
        ContextFixture x = context("X-pin", "!anchor(X);");
        ContextFixture a = context("A-pin", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-pin", "!p(John);");
        ConnectionVector pinned = vector(x,a,c);
        try (OperationSnapshot operation = OperationSnapshot.open(x.location, pinned)) {
            append(c, "!p(Mary);");
            CausalFrontierScheduler.Result result = CausalFrontierScheduler.execute(operation, invocation("?$value q(value);").getFrontier());
            assertEquals(Collections.singleton("John"), trueRows(result));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList("John", "Mary")), trueRows(schedule(x, "?$value q(value);", a, c)));
    }

    @Test
    void schedulerUsesOnlyXsDirectTargetsEvenWhenTargetHasPublishedConnection() throws Exception {
        ContextFixture x = context("X-direct", "!anchor(X);");
        ContextFixture a = context("A-direct", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-direct", "!p(John);");
        ConnectionManager.connect(a.location, c.location);
        ContextFixture connected = new ContextFixture(a.name, a.location, currentRef(a.location));
        byte[] before = digest(connected);
        assertEquals(FrontierAggregate.Truth.UNKNOWN, FrontierAggregate.of(schedule(x, "?q(John);", connected).getAnswers()).getTruth());
        assertEquals(FrontierAggregate.Truth.TRUE, FrontierAggregate.of(schedule(x, "?q(John);", connected, c).getAnswers()).getTruth());
        assertArrayEquals(before, digest(connected));
        assertEquals(connected.ref, currentRef(a.location));
    }

    @Test
    void repeatedRootArgumentsFilterIndependentUnequalChildTuple() throws Exception {
        ContextFixture x = context("X-repeat", "!anchor(X);");
        ContextFixture a = context("A-repeat", "!@a @b pair(a,b) -> middle(b,a);");
        ContextFixture b = context("B-repeat", "!@a @b middle(a,b) -> result(b,a);");
        ContextFixture c = context("C-repeat", "!pair(John,Mary);", "!pair(Tom,Tom);");
        assertEquals(Collections.singleton("Tom"), trueRows(schedule(x, "?$value result(value,value);", a,b,c)));
    }

    @Test
    void typedFixedStringAndHashCollisionStayIndependentAcrossChain() throws Exception {
        ContextFixture x = context("X-typed", "!anchor(X);");
        ContextFixture a = context("A-typed", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-typed", "!@x q(x) -> r(x);");
        ContextFixture c = context("C-typed", "!p('42');", "!p(Aa);", "!p(BB);");
        assertEquals(FrontierAggregate.Truth.TRUE, FrontierAggregate.of(schedule(x, "?r('42');", a,b,c).getAnswers()).getTruth());
        assertEquals(FrontierAggregate.Truth.UNKNOWN, FrontierAggregate.of(schedule(x, "?r(42);", a,b,c).getAnswers()).getTruth());
        Set<String> values = trueRows(schedule(x, "?$value r(value);", a,b,c));
        assertEquals(new LinkedHashSet<String>(Arrays.asList("42", "Aa", "BB")), values);
    }

    private void liveQuery(ContextFixture x, String query, Set<String> expected, ContextFixture... targets) throws Exception {
        User user = new User();
        user.setDatabaseDir(x.location.getParent().toString() + File.separator);
        DB data = new DB();
        data.init(user);
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(x.name);
        user.setCurrentMind(mind);
        try {
            for (ContextFixture target : targets) data.connectContext(target.location.toString());
            assertEquals(Boolean.TRUE, mind.query(query, null, false));
            Set<String> actual = new LinkedHashSet<String>();
            mind.getValues().forEach(row -> actual.add(row.get("answer").toString()));
            assertEquals(expected, actual);
            assertEquals(x.ref, new RevisionRef(data.getContextId(), data.getRevision()));
            for (ContextFixture target : targets) data.disconnectContext(target.ref.getContextId());
            assertNull(mind.query(query, null, false), "root results must not persist beyond the operation");
        } finally {
            user.setCurrentMind(mind.closeStorage());
        }
    }

    private Set<String> trueRows(CausalFrontierScheduler.Result result) {
        Set<String> values = new LinkedHashSet<String>();
        for (FrontierAnswer answer : result.getAnswers()) if (answer.getTruth() == FrontierAnswer.Truth.TRUE) values.addAll(rows(answer));
        return values;
    }

    private ConnectionVector vector(ContextFixture x, ContextFixture... targets) throws Exception {
        ConnectionVector vector = ConnectionVector.empty();
        for (ContextFixture target : targets) vector = vector.with(ConnectionManager.qualifyConnect(x.location, target.location));
        return vector;
    }

    private CausalFrontierScheduler.Result schedule(ContextFixture x, String query, ContextFixture... targets) throws Exception {
        try (OperationSnapshot operation = OperationSnapshot.open(x.location, vector(x, targets))) {
            return CausalFrontierScheduler.execute(operation, invocation(query).getFrontier());
        }
    }

    private FrontierInvocation invocation(String query) throws Exception {
        Mind mind = mind();
        assertNull(mind.queryCanonical(query, null, false));
        assertEquals(1, mind.getFrontierDomains().size());
        return FrontierInvocation.create(mind.getFrontierDomains().get(0));
    }

    private Mind mind() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        return mind;
    }

    private FrontierAnswer execute(ContextFixture fixture, FrontierInvocation invocation) throws Exception {
        return LocalFrontierExecutor.execute(fixture.location, fixture.ref, invocation);
    }

    private FrontierAnswer execute(ContextFixture fixture, FrontierRequest request) throws Exception {
        return LocalFrontierExecutor.execute(fixture.location, fixture.ref, request);
    }

    private Set<String> rows(FrontierAnswer answer) {
        Set<String> result = new LinkedHashSet<String>();
        for (List<FrontierAnswer.ValueRef> row : answer.getValues()) {
            result.add(row.stream().map(FrontierAnswer.ValueRef::getRendered).collect(Collectors.joining("/")));
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
            return new ContextFixture(name, location, new RevisionRef(data.getContextId(), data.getRevision()));
        } finally {
            user.setCurrentMind(mind.closeStorage());
        }
    }

    private RevisionRef currentRef(Path location) throws Exception {
        try (ContextSnapshot snapshot = ContextSnapshot.open(location)) {
            return new RevisionRef(snapshot.getContextId(), snapshot.getRevision());
        }
    }

    private byte[] digest(ContextFixture fixture) throws Exception {
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
