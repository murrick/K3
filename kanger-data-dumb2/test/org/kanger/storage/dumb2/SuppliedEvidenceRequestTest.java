package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
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

/** Minimal proven facts, native parent reproof and exact semantic execution state. */
public class SuppliedEvidenceRequestTest {
    @TempDir
    Path root;

    @Test
    void groundEvidenceReprovesParentOnlyInsideEphemeralTarget() throws Exception {
        ContextFixture a = context("A", "!@x p(x) -> q(x);");
        ContextFixture c = context("C", "!p(John);");
        byte[] before = digest(a);
        FrontierInvocation parent = invocation("?q(John);");
        FrontierAnswer blocked = execute(a, parent);
        FrontierAnswer child = execute(c, child(blocked));
        FrontierRequest request = FrontierRequest.continueWith(blocked, facts(child));
        assertEquals(1, request.getEvidence().size());
        assertNotEquals(blocked.getExecutionState(), request.executionState(a.ref));
        FrontierAnswer proven = execute(a, request);
        assertEquals(FrontierAnswer.Truth.TRUE, proven.getTruth());
        assertTrue(proven.getInvocation().sameAddress(parent));
        assertEquals(request.executionState(a.ref), proven.getExecutionState());
        assertTrue(proven.getUnresolvedFrontiers().isEmpty());
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, parent).getTruth());
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, invocation("?p(John);")).getTruth());
        assertArrayEquals(before, digest(a));
        assertEquals(a.ref, currentRef(a.location));
    }

    @Test
    void freeReorderedValuesEachPassThroughNativeParentProof() throws Exception {
        ContextFixture a = context("A-reorder", "!@a @b parent(a,b) -> ancestor(b,a);");
        ContextFixture c = context("C-reorder", "!padding(Noise);",
                "!parent(John,Mary);", "!parent(Tom,Other);");
        FrontierAnswer blocked = execute(a, invocation("?$right $left ancestor(right,left);"));
        FrontierAnswer donor = execute(c, child(blocked));
        assertTrue(blocked.getValues().isEmpty());
        FrontierRequest request = FrontierRequest.continueWith(blocked, facts(donor));
        assertEquals(2, request.getEvidence().size());
        FrontierAnswer proven = execute(a, request);
        assertEquals(FrontierAnswer.Truth.TRUE, proven.getTruth());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("Mary/John", "Other/Tom")), rows(proven));
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, blocked.getInvocation()).getTruth());
    }

    @Test
    void repeatedParentConstraintFiltersUnequalChildArguments() throws Exception {
        ContextFixture a = context("A-repeat", "!@a @b parent(a,b) -> ancestor(b,a);");
        ContextFixture c = context("C-repeat", "!parent(John,Mary);", "!parent(Tom,Tom);");
        FrontierAnswer blocked = execute(a, invocation("?$person ancestor(person,person);"));
        FrontierAnswer all = execute(c, invocation("?$a $b parent(a,b);"));
        FrontierRequest request = FrontierRequest.continueWith(blocked, facts(all));
        assertEquals(1, request.getEvidence().size());
        FrontierAnswer proven = execute(a, request);
        assertEquals(Collections.singleton("Tom"), rows(proven));
    }

    @Test
    void fixedAmbiguousStringSelectsOnlyTypedChildFact() throws Exception {
        ContextFixture a = context("A-string", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-string", "!p('42');", "!p(42);");
        FrontierAnswer blocked = execute(a, invocation("?q('42');"));
        FrontierAnswer donor = execute(c, invocation("?$value p(value);"));
        FrontierRequest request = FrontierRequest.continueWith(blocked, facts(donor));
        assertEquals(1, request.getEvidence().size());
        assertEquals(DataType.STRING, request.getEvidence().get(0).getArguments().get(0).getType());
        assertEquals(FrontierAnswer.Truth.TRUE, execute(a, request).getTruth());
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, invocation("?q(42);")).getTruth());
    }

    @Test
    void negativeChildFactReprovesOriginalFalsePass() throws Exception {
        ContextFixture a = context("A-negative", "!@x ~p(x) -> ~q(x);");
        ContextFixture c = context("C-negative", "!~p(John);");
        FrontierAnswer blocked = execute(a, invocation("?q(John);"));
        FrontierAnswer donor = execute(c, child(blocked));
        assertEquals(FrontierAnswer.Truth.TRUE, donor.getTruth());
        FrontierRequest request = FrontierRequest.continueWith(blocked, facts(donor));
        assertEquals(1, request.getEvidence().size());
        assertEquals(FrontierAnswer.Truth.FALSE, execute(a, request).getTruth());
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, blocked.getInvocation()).getTruth());
    }

    @Test
    void unknownHypothesesFalseAndConflictAreNeverSuppliedFacts() throws Exception {
        ContextFixture a = context("A-unknown", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-positive", "!p(John);");
        ContextFixture d = context("D-negative", "!~p(John);");
        FrontierAnswer blocked = execute(a, invocation("?q(John);"));
        assertFalse(blocked.getHypotheses().isEmpty());
        assertTrue(facts(blocked).isEmpty());
        FrontierInvocation invocation = child(blocked);
        FrontierAnswer yes = execute(c, invocation);
        FrontierAnswer no = execute(d, invocation);
        assertEquals(FrontierAnswer.Truth.FALSE, no.getTruth());
        assertTrue(facts(no).isEmpty());
        assertTrue(SuppliedEvidence.fromAnswers(Arrays.asList(yes, no)).isEmpty());
        assertEquals(FrontierAnswer.Truth.NULL, execute(a,
                FrontierRequest.continueWith(blocked, facts(no))).getTruth());
    }

    @Test
    void irrelevantFactsAndParentConclusionAreExcludedFromPacket() throws Exception {
        ContextFixture a = context("A-minimal", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-minimal", "!q(John);", "!noise(John);", "!p(Mary);");
        FrontierAnswer blocked = execute(a, invocation("?q(John);"));
        List<SuppliedEvidence> proof = new ArrayList<SuppliedEvidence>();
        proof.addAll(facts(execute(c, invocation("?q(John);"))));
        proof.addAll(facts(execute(c, invocation("?noise(John);"))));
        proof.addAll(facts(execute(c, invocation("?p(Mary);"))));
        FrontierRequest request = FrontierRequest.continueWith(blocked, proof);
        assertTrue(request.getEvidence().isEmpty());
        assertEquals(blocked.getExecutionState(), request.executionState(a.ref));
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, request).getTruth());
    }

    @Test
    void continuationIsBoundToExactBlockedTargetRevision() throws Exception {
        ContextFixture a = context("A-pin", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-pin", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-pin", "!p(John);");
        FrontierAnswer blocked = execute(a, invocation("?q(John);"));
        FrontierRequest request = FrontierRequest.continueWith(blocked, facts(execute(c, child(blocked))));
        ContextFixture newer = append(a, "!noise(New);");
        assertThrows(IllegalArgumentException.class, () -> execute(newer, request));
        assertThrows(IllegalArgumentException.class, () -> execute(b, request));
        assertEquals(FrontierAnswer.Truth.TRUE, execute(a, request).getTruth());
        assertEquals(FrontierAnswer.Truth.NULL, execute(newer, blocked.getInvocation()).getTruth());
        assertEquals(newer.ref, currentRef(a.location));
    }

    @Test
    void fingerprintIgnoresOrderDuplicatesProvenanceAndForeignTermIds() throws Exception {
        ContextFixture a = context("A-key", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-key", "!p(John);", "!p(Mary);");
        ContextFixture d = context("D-key", "!padding(Noise);", "!p(John);", "!p(Mary);");
        FrontierAnswer blocked = execute(a, invocation("?$root q(root);"));
        FrontierInvocation child = child(blocked);
        List<SuppliedEvidence> one = facts(execute(c, child));
        List<SuppliedEvidence> duplicate = new ArrayList<SuppliedEvidence>(one);
        Collections.reverse(duplicate);
        duplicate.addAll(one);
        FrontierRequest first = FrontierRequest.continueWith(blocked, one);
        FrontierRequest repeated = FrontierRequest.continueWith(blocked, duplicate);
        assertEquals(first.getEvidenceFingerprint(), repeated.getEvidenceFingerprint());
        assertEquals(first.executionState(a.ref), repeated.executionState(a.ref));
        List<SuppliedEvidence> both = SuppliedEvidence.fromAnswers(Arrays.asList(
                execute(c, child), execute(d, child)));
        assertEquals(2, both.size());
        assertEquals(2, both.get(0).getSupports().size());
        FrontierRequest otherProvenance = FrontierRequest.continueWith(blocked, both);
        assertEquals(first.executionState(a.ref), otherProvenance.executionState(a.ref));
        assertThrows(UnsupportedOperationException.class, () -> first.getEvidence().clear());
        assertThrows(UnsupportedOperationException.class, () -> one.get(0).getArguments().clear());
        assertThrows(UnsupportedOperationException.class, () -> both.get(0).getSupports().clear());
        assertThrows(IllegalArgumentException.class, () -> FrontierRequest.continueWith(
                execute(a, first), one));
    }

    @Test
    void semanticStateIgnoresInvocationTokenAndVariableNamesButKeepsConstraints() throws Exception {
        ContextFixture a = context("A-shape", "!noise(John);");
        FrontierExecutionState one = initialState(a, "?$x q(x);");
        FrontierExecutionState renamed = initialState(a, "?$other q(other);");
        assertEquals(one, renamed);
        assertEquals(one.hashCode(), renamed.hashCode());
        assertNotEquals(initialState(a, "?$x q(x,x);"), initialState(a, "?$a $b q(a,b);"));
        assertNotEquals(initialState(a, "?q(John);"), initialState(a, "?q(Mary);"));
        assertNotEquals(initialState(a, "?q('42');"), initialState(a, "?q(42);"));
        assertNotEquals(initialState(a, "?q(John);"), initialState(a, "?~q(John);"));
        ContextFixture b = context("B-shape", "!noise(John);");
        assertNotEquals(one, initialState(b, "?$x q(x);"));
    }

    @Test
    void hashCollisionDoesNotHideDifferentRelevantEvidence() throws Exception {
        ContextFixture a = context("A-collision", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-collision", "!p(Aa);", "!p(BB);");
        FrontierAnswer blocked = execute(a, invocation("?$root q(root);"));
        List<SuppliedEvidence> proof = facts(execute(c, child(blocked)));
        assertEquals(2, proof.size());
        FrontierRequest aa = FrontierRequest.continueWith(blocked, Collections.singletonList(
                proof.stream().filter(f -> "Aa".equals(f.getArguments().get(0).materialize().getValue()))
                        .findFirst().get()));
        FrontierRequest bb = FrontierRequest.continueWith(blocked, Collections.singletonList(
                proof.stream().filter(f -> "BB".equals(f.getArguments().get(0).materialize().getValue()))
                        .findFirst().get()));
        assertEquals(aa.getEvidenceFingerprint().hashCode(), bb.getEvidenceFingerprint().hashCode());
        assertNotEquals(aa.getEvidenceFingerprint(), bb.getEvidenceFingerprint());
        assertNotEquals(aa.executionState(a.ref), bb.executionState(a.ref));
        assertEquals(Collections.singleton("Aa"), rows(execute(a, aa)));
        assertEquals(Collections.singleton("BB"), rows(execute(a, bb)));
    }

    @Test
    void staleAndResumedStatesAtSameTargetCannotShareResultBatch() throws Exception {
        ContextFixture x = context("X-stale", "!resident(Rick,Vienna);");
        ContextFixture a = context("A-stale", "!@x p(x) -> q(x);");
        ContextFixture c = context("C-stale", "!p(John);");
        FrontierAnswer blocked = execute(a, invocation("?q(John);"));
        FrontierAnswer resumed = execute(a, FrontierRequest.continueWith(blocked,
                facts(execute(c, child(blocked)))));
        List<FrontierAnswer> stale = Arrays.asList(blocked, resumed);
        assertThrows(IllegalArgumentException.class, () -> FrontierAggregate.of(stale));
        assertThrows(IllegalArgumentException.class, () -> SuppliedEvidence.fromAnswers(stale));
        ConnectionVector vector = ConnectionVector.empty().with(
                ConnectionManager.qualifyConnect(x.location, a.location));
        Mind target = mind();
        int before = target.getTerms().size();
        try (OperationSnapshot operation = OperationSnapshot.open(x.location, vector)) {
            assertThrows(IllegalArgumentException.class, () -> FrontierLiftSession.liftInto(target,
                    operation, stale));
        }
        assertEquals(before, target.getTerms().size());
    }

    @Test
    void latestResponsesFromDifferentTargetsMayUseDifferentRelevantPackets() throws Exception {
        ContextFixture a = context("A-latest", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-latest", "!noise(John);");
        ContextFixture c = context("C-latest", "!p(John);");
        FrontierInvocation invocation = invocation("?q(John);");
        FrontierAnswer blocked = execute(a, invocation);
        FrontierAnswer resumed = execute(a, FrontierRequest.continueWith(blocked,
                facts(execute(c, child(blocked)))));
        FrontierAnswer other = execute(b, invocation);
        assertEquals(FrontierAggregate.Truth.TRUE,
                FrontierAggregate.of(Arrays.asList(resumed, other)).getTruth());
        assertEquals(1, SuppliedEvidence.fromAnswers(Arrays.asList(resumed, other)).size());
    }

    @Test
    void manualGroundCausalChainRequiresEachNativeAncestorReproof() throws Exception {
        ContextFixture a = context("A-chain", "!@x p(x) -> q(x);");
        ContextFixture b = context("B-chain", "!@x q(x) -> r(x);");
        ContextFixture c = context("C-chain", "!p(John);");
        FrontierAnswer blockedB = execute(b, invocation("?r(John);"));
        FrontierAnswer blockedA = execute(a, child(blockedB));
        FrontierAnswer leaf = execute(c, child(blockedA));
        assertTrue(FrontierRequest.continueWith(blockedB, facts(leaf)).getEvidence().isEmpty(),
                "p is not the immediate q demand of B");
        FrontierAnswer q = execute(a, FrontierRequest.continueWith(blockedA, facts(leaf)));
        assertEquals(FrontierAnswer.Truth.TRUE, q.getTruth());
        FrontierAnswer r = execute(b, FrontierRequest.continueWith(blockedB, facts(q)));
        assertEquals(FrontierAnswer.Truth.TRUE, r.getTruth());
        assertEquals(FrontierAnswer.Truth.NULL, execute(a, blockedA.getInvocation()).getTruth());
        assertEquals(FrontierAnswer.Truth.NULL, execute(b, blockedB.getInvocation()).getTruth());
    }

    @Test
    void manualReorderedChainPreservesIndependentJohnMaryAndTomOtherVariants() throws Exception {
        ContextFixture a = context("A-pairs", "!@a @b parent(a,b) -> ancestor(b,a);");
        ContextFixture b = context("B-pairs", "!@a @b ancestor(a,b) -> result(b,a);");
        ContextFixture c = context("C-pairs", "!parent(John,Mary);", "!parent(Tom,Other);");
        FrontierAnswer blockedB = execute(b, invocation("?$x $y result(x,y);"));
        FrontierAnswer blockedA = execute(a, child(blockedB));
        FrontierAnswer leaf = execute(c, child(blockedA));
        assertTrue(blockedB.getValues().isEmpty());
        assertTrue(blockedA.getValues().isEmpty());
        FrontierAnswer ancestor = execute(a, FrontierRequest.continueWith(blockedA, facts(leaf)));
        assertEquals(2, ancestor.getValues().size());
        FrontierAnswer result = execute(b, FrontierRequest.continueWith(blockedB, facts(ancestor)));
        assertEquals(FrontierAnswer.Truth.TRUE, result.getTruth());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("John/Mary", "Tom/Other")), rows(result));
    }

    @Test
    void nativeDonorSeedIsIdempotentWithinEphemeralMind() throws Exception {
        ContextFixture c = context("C-idempotent", "!p(John);");
        SuppliedEvidence fact = facts(execute(c, invocation("?p(John);"))).get(0);
        Mind root = mind();
        assertTrue(root.compile("!@x p(x) -> q(x);"));
        Mind work = Mind.ephemeralChild(root);
        try {
            fact.materialize(work);
            fact.materialize(work);
            assertEquals(Boolean.TRUE, work.queryCanonical("?$value q(value);", null, false));
            assertEquals(1, work.getValues().size());
            assertEquals("John", work.getValues().iterator().next().get("value").toString());
        } finally {
            root.discardEphemeral(work);
        }
        assertNull(root.queryCanonical("?q(John);", null, false));
    }

    private FrontierExecutionState initialState(ContextFixture fixture, String query) throws Exception {
        return FrontierRequest.initial(invocation(query)).executionState(fixture.ref);
    }

    private List<SuppliedEvidence> facts(FrontierAnswer answer) {
        return SuppliedEvidence.fromAnswers(Collections.singletonList(answer));
    }

    private FrontierInvocation child(FrontierAnswer answer) {
        assertEquals(1, answer.getUnresolvedFrontiers().size());
        return FrontierInvocation.create(answer.getUnresolvedFrontiers().get(0).getQuery());
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
