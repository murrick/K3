package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.Step;
import org.kanger.units.Term;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary query and semantic explain at the complete causal operation boundary. */
class CausalFederationQualificationTest {
    @TempDir Path root;

    @Test
    void conflictingJohnDoesNotEraseProvenMaryFromFreeAncestors() throws Exception {
        context("A", "!@x p(x) -> q(x);", "!~p(John);");
        context("B", "!@x q(x) -> r(x);"); context("C", "!p(John);");
        try (Fixture x=open("X")) {
            assertTrue(x.mind.query("!p(Mary);",null,false)); x.connect("A","B","C");
            long revision=x.data.getRevision();
            assertTrue(x.mind.query("?r(Mary);",null,false));
            assertNull(x.mind.query("?r(Tom);",null,false));
            IContextFederation.ExplainResult trace=x.mind.explainQuery("?$x r(x);");
            assertEquals(IContextFederation.FrontierTruth.TRUE, trace.getFinalTruth());
            assertEquals(set("Mary"),values(trace,"x"));
            assertTrue(trace.getPasses().stream().flatMap(pass->pass.getContinuation().getObservations().stream())
                    .anyMatch(observation->observation.getTruth()==IContextFederation.FrontierTruth.CONFLICT
                            && observation.getQuerySource().contains("John")));
            assertTrue(steps(trace).stream().flatMap(step->step.getSuppliedEvidence().stream())
                    .noneMatch(fact->fact.getStatement().contains("John")));
            for (String predicate : Arrays.asList("r","q","p")) {
                assertEquals(Boolean.TRUE,x.mind.query("?$x "+predicate+"(x);",null,false));
                assertEquals(set("Mary"),values(x.mind,"x"));
            }
            assertTrue(x.mind.query("?$x r(x);",null,true));
            assertFalse(x.mind.getQueryConflicts().isEmpty());
            assertTrue(x.mind.getCurrentLogRecord(org.kanger.enums.LogMode.ANALYZER).getRecord().contains("Conflicting substitution:"));
            assertNull(x.mind.query("?r(John);",null,true));
            assertTrue(x.mind.getCurrentLogRecord(org.kanger.enums.LogMode.ANALYZER).getRecord().contains("Result: CONFLICT"));
            assertTrue(x.mind.query("?r(Mary);",null,false));
            assertTrue(x.mind.getQueryConflicts().isEmpty());
            assertEquals(revision,x.data.getRevision());
            assertEquals(3,x.data.federationSnapshot().getConnections().size());
        }
    }

    @Test
    void conflictingTupleWithinOneDonorKeepsItsOtherRow() throws Exception {
        context("A", "!@x p(x) -> q(x);", "!~p(John);"); context("B", "!@x q(x) -> r(x);");
        context("C", "!p(John);", "!p(Mary);");
        try (Fixture x=open("X")) {
            assertTrue(x.mind.query("!anchor(Unrelated);",null,false));
            x.connect("A","B","C");
            assertEquals(Boolean.TRUE,x.mind.query("?$x r(x);",null,false));
            assertEquals(set("Mary"),values(x.mind,"x"));
            assertNull(x.mind.query("?r(John);",null,false));
        }
    }

    @Test
    void canonicalExplainShowsEveryNativeAncestorAndMinimalPacketInProofOrder() throws Exception {
        context("A", "!@x p(x) -> q(x);");
        context("B", "!@x q(x) -> r(x);");
        context("C", "!p(John);", "!p(Mary);");
        try (Fixture x = open("X")) {
            x.connect("A", "B", "C");
            long revision = x.data.getRevision();
            IContextFederation.ExplainResult result = explain(x, "?$answer r(answer);");
            assertEquals(IContextFederation.FrontierTruth.TRUE, result.getFinalTruth());
            assertEquals(set("John", "Mary"), values(result, "answer"));
            List<IContextFederation.CausalStep> steps = steps(result);
            int cProof = proven(steps, "p(", false);
            int aProof = proven(steps, "q(", true);
            int bProof = proven(steps, "r(", true);
            assertTrue(cProof < aProof && aProof < bProof);
            assertEquals(2, steps.get(aProof).getSuppliedEvidence().size());
            assertEquals(2, steps.get(bProof).getSuppliedEvidence().size());
            assertTrue(steps.get(aProof).getSuppliedEvidence().stream().allMatch(f -> f.getStatement().startsWith("!p(")));
            assertTrue(steps.get(bProof).getSuppliedEvidence().stream().allMatch(f -> f.getStatement().startsWith("!q(")));
            assertTrue(steps.stream().flatMap(s -> s.getDemands().stream()).anyMatch(d -> d.getParentQuery().contains("r(") && d.getChildQuery().contains("q(")));
            assertTrue(steps.stream().flatMap(s -> s.getDemands().stream()).anyMatch(d -> d.getParentQuery().contains("q(") && d.getChildQuery().contains("p(")));
            assertThrows(UnsupportedOperationException.class, () -> steps.get(aProof).getValues().clear());
            assertThrows(UnsupportedOperationException.class, () -> steps.get(aProof).getSuppliedEvidence().clear());
            assertEquals(revision, x.data.getRevision());
            assertFalse(x.mind.isExplainQueryActive());
            assertEquals(Boolean.TRUE, x.mind.query("?$answer r(answer);", null, false));
            assertEquals(set("John", "Mary"), values(x.mind, "answer"));
            assertTrue(x.data.executeFederatedQuery("?$answer r(answer);").getCausalSteps().isEmpty());
        }
    }

    @Test
    void reorderedProjectionIsVisibleWithoutRuntimeVariableIdentity() throws Exception {
        context("Order", "!@a @b parent(a,b) -> ancestor(b,a);");
        context("Pair", "!parent(John,Mary);", "!parent(Tom,Other);");
        try (Fixture x = open("X-order")) {
            x.connect("Order", "Pair");
            IContextFederation.ExplainResult result = explain(x, "?$right $left ancestor(right,left);");
            assertEquals(IContextFederation.FrontierTruth.TRUE, result.getFinalTruth());
            assertEquals(set("Mary/John", "Other/Tom"), result.getValues().stream().map(r -> r.getBindings().get("right") + "/" + r.getBindings().get("left")).collect(Collectors.toSet()));
            IContextFederation.CausalDemand demand = steps(result).stream().flatMap(s -> s.getDemands().stream()).filter(d -> d.getParentQuery().contains("ancestor(")).findFirst().get();
            assertEquals(Arrays.asList(1,0), demand.getParentToChild());
            assertThrows(UnsupportedOperationException.class, () -> demand.getParentToChild().clear());
        }
    }

    @Test
    void workingLayerFactsDriveForeignAncestorsAndDisappearOnRollback() throws Exception {
        context("OverlayA", "!@x p(x) -> q(x);");
        context("OverlayB", "!@x q(x) -> r(x);");
        try (Fixture x = open("X-overlay")) {
            // User transactions historically share canonical registries; predeclare
            // their vocabulary so this check measures knowledge/query rollback.
            assertEquals(Boolean.TRUE, x.mind.query("!anchor(John,Mary);", null, false));
            assertEquals(Boolean.TRUE, x.mind.query("!@x p(x) -> unused(x);", null, false));
            x.connect("OverlayA", "OverlayB");
            long revision = x.data.getRevision();
            Mind tx = new Mind(x.mind);
            x.user.setCurrentMind(tx);
            try {
                assertEquals(Boolean.TRUE, tx.query("!p(John);", null, false));
                assertEquals(Boolean.TRUE, tx.query("!p(Mary);", null, false));
                IContextFederation.ExplainResult result = tx.explainQuery("?$answer r(answer);");
                assertEquals(IContextFederation.FrontierTruth.TRUE, result.getFinalTruth());
                assertEquals(set("John", "Mary"), values(result, "answer"));
                assertTrue(steps(result).stream().anyMatch(s -> s.getTarget().getContextId().equals(x.data.getContextId()) && s.getQuery().contains("p(") && s.getTruth() == IContextFederation.FrontierTruth.TRUE));
                assertEquals(revision, x.data.getRevision());
            } finally {
                x.mind.release(tx);
                x.user.setCurrentMind(x.mind);
            }
            assertEquals(revision, x.data.getRevision(), "rollback before probes");
            assertNull(x.mind.query("?p(John);", null, false));
            assertNull(x.mind.query("?$answer r(answer);", null, false));
            assertEquals(revision, x.data.getRevision());
        }
        try (Fixture reopened = open("X-overlay")) {
            assertNull(reopened.mind.query("?p(John);", null, false));
        }
    }

    @Test
    void compoundQueryConsumesCausalAgeAndKeepsCalculatedFilterLocal() throws Exception {
        context("AgeA", "!@x @y rawAge(x,y) -> knownAge(x,y);");
        context("AgeB", "!@x @y knownAge(x,y) -> age(x,y);");
        context("AgeC", "!rawAge(Tom,18);", "!rawAge(Young,8);");
        try (Fixture x = open("X-compound")) {
            assertEquals(Boolean.TRUE, x.mind.query("!son(Tom,John);", null, false));
            assertEquals(Boolean.TRUE, x.mind.query("!son(Young,John);", null, false));
            x.connect("AgeA", "AgeB", "AgeC");
            long revision = x.data.getRevision();
            IContextFederation.ExplainResult result = explain(x, "?$person $years son(person,John) && age(person,years) && years > 10;");
            assertEquals(IContextFederation.FrontierTruth.TRUE, result.getFinalTruth());
            assertEquals(Collections.singleton("Tom"), values(result, "person"));
            assertEquals(Collections.singleton("18.0"), values(result, "years"));
            assertTrue(steps(result).stream().anyMatch(s -> s.getQuery().contains("rawAge(")));
            assertFalse(steps(result).stream().anyMatch(s -> s.getQuery().contains(">")));
            assertEquals(revision, x.data.getRevision());
        }
    }

    @Test
    void negativeGroundChainPreservesOrdinaryFalseLifecycle() throws Exception {
        context("NegA", "!@x ~p(x) -> ~q(x);");
        context("NegB", "!@x ~q(x) -> ~r(x);");
        context("NegC", "!~p(John);");
        try (Fixture x = open("X-negative")) {
            x.connect("NegA", "NegB", "NegC");
            assertEquals(Boolean.FALSE, x.mind.query("?r(John);", null, false));
            IContextFederation.ExplainResult result = explain(x, "?r(John);");
            assertEquals(IContextFederation.FrontierTruth.FALSE, result.getFinalTruth());
            assertTrue(steps(result).stream().flatMap(s -> s.getSuppliedEvidence().stream()).anyMatch(f -> f.getStatement().startsWith("!~p(")));
            assertTrue(steps(result).stream().flatMap(s -> s.getSuppliedEvidence().stream()).anyMatch(f -> f.getStatement().startsWith("!~q(")));
            assertTrue(result.getValues().isEmpty());
        }
    }

    @Test
    void knownChildConflictIsExplainedWithoutSuggestingHypotheses() throws Exception {
        context("ConflictA", "!@x p(x) -> q(x);");
        context("ConflictC", "!p(John);");
        context("ConflictD", "!~p(John);");
        try (Fixture x = open("X-conflict")) {
            assertEquals(Boolean.TRUE, x.mind.query("!@x q(x) -> r(x);", null, false));
            x.connect("ConflictA", "ConflictC", "ConflictD");
            IContextFederation.ExplainResult result = explain(x, "?r(John);");
            assertEquals(IContextFederation.FrontierTruth.CONFLICT, result.getFinalTruth(), result.getPasses().stream().flatMap(p -> p.getContinuation().getObservations().stream()).map(o -> o.getQuerySource() + ":" + o.getTruth()).collect(Collectors.joining(";")) + " steps=" + steps(result).stream().map(st -> st.getQuery() + ":" + st.getTruth() + " demands=" + st.getDemands().stream().map(d -> d.getChildQuery()).collect(Collectors.joining(","))).collect(Collectors.joining(";")));
            assertTrue(result.getPasses().stream().allMatch(p -> p.getContinuation().getProvisionalHypotheses().isEmpty()));
            assertTrue(x.mind.getHypothesis().isEmpty(), "known contradiction must not suggest native hypotheses");
            assertTrue(result.getValues().isEmpty());
        }
    }

    @Test
    void fullyLocalQueryAndEmptyTopologyPreserveNativeBehaviorWithoutTrace() throws Exception {
        try (Fixture x = open("X-local")) {
            assertEquals(Boolean.TRUE, x.mind.query("!@x p(x) -> q(x);", null, false));
            assertEquals(Boolean.TRUE, x.mind.query("!p(John);", null, false));
            IContextFederation.ExplainResult result = explain(x, "?$answer q(answer);");
            assertEquals(IContextFederation.FrontierTruth.TRUE, result.getFinalTruth());
            assertTrue(steps(result).isEmpty());
            assertEquals(Collections.singleton("John"), values(result, "answer"));
        }
    }

    @Test
    void freshGroundQueryConstantCannotPublishXOrDropWorkingTopology() throws Exception {
        context("FreshA", "!@x p(x) -> q(x);");
        context("FreshC", "!p(John);");
        try (Fixture x = open("X-fresh")) {
            assertEquals(Boolean.TRUE, x.mind.query("!@x q(x) -> r(x);", null, false));
            x.connect("FreshA", "FreshC");
            long revision = x.data.getRevision();
            assertEquals(Boolean.TRUE, x.mind.query("?r(John);", null, false));
            assertEquals(revision, x.data.getRevision());
            assertEquals(2, x.data.federationSnapshot().getConnections().size());
            assertNull(x.mind.query("?r(Nobody);", null, false));
            assertEquals(revision, x.data.getRevision());
            assertEquals(2, x.data.federationSnapshot().getConnections().size());
        }
    }

    @Test
    void successiveFreeQueriesPreserveConnectionsWithFactsSplitBetweenCAndX() throws Exception {
        context("A", "!@x p(x) -> q(x);");
        context("B", "!@x q(x) -> r(x);");
        context("C", "!p(John);");
        try (Fixture x = open("X")) {
            assertEquals(Boolean.TRUE, x.mind.query("!p(Mary);", null, false));
            x.connect("A", "B", "C");
            long revision = x.data.getRevision();
            for (String predicate : Arrays.asList("r", "q", "r", "q")) {
                assertEquals(Boolean.TRUE, x.mind.query("?$x " + predicate + "(x);", null, false));
                assertEquals(set("John", "Mary"), values(x.mind, "x"));
                assertEquals(revision, x.data.getRevision());
                assertEquals(3, x.data.federationSnapshot().getConnections().size());
            }
        }
    }

    @Test
    void connectAndRepeatedQueriesAcceptPinnedRevisionsWithUnusedDictionaryRecords() throws Exception {
        context("A", "!@x p(x) -> q(x);");
        context("B", "!@x q(x) -> r(x);");
        context("C", "!p(John);");
        context("X", "!p(Mary);");
        // A legal published dictionary may contain vocabulary retained by an
        // earlier query, even though no durable rule references it anymore.
        for (String name : Arrays.asList("A", "X")) {
            try (ContextStore store = ContextStore.open(root.resolve(name))) {
                IBase dictionary = store.getBase("dictionary");
                Term unused = new Term("UnusedQueryVocabulary", new Mind(new User()));
                unused.setId(dictionary.nextId());
                Step record = new Step();
                record.setId(unused.getId());
                record.setHash(unused.getHash());
                record.setData(unused);
                record.setNext(dictionary.getRoot());
                dictionary.add(record);
                store.flush();
            }
        }
        long pinnedA;
        Map<String, String> generationBefore;
        try (ContextSnapshot snapshot = ContextSnapshot.open(root.resolve("A"))) {
            pinnedA = snapshot.getRevision();
            generationBefore = generationBytes(snapshot.getGeneration());
        }
        try (Fixture x = open("X")) {
            x.connect("A", "B", "C");
            long revision = x.data.getRevision();
            assertEquals(Boolean.TRUE, x.mind.query("?$x r(x);", null, false));
            assertEquals(set("John", "Mary"), values(x.mind, "x"));
            x.data.disconnectContext("A");
            x.connect("A");
            assertEquals(Boolean.TRUE, x.mind.query("?$x q(x);", null, false));
            assertEquals(set("John", "Mary"), values(x.mind, "x"));
            assertEquals(revision, x.data.getRevision());
            assertEquals(3, x.data.federationSnapshot().getConnections().size());
        }
        try (ContextSnapshot snapshot = ContextSnapshot.open(root.resolve("A"))) {
            assertEquals(pinnedA, snapshot.getRevision());
            assertEquals(generationBefore, generationBytes(snapshot.getGeneration()));
        }
    }

    private Map<String, String> generationBytes(Path generation) throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        try (java.util.stream.Stream<Path> files = Files.walk(generation)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                result.put(generation.relativize(file).toString(),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    @Test
    void freshExternalParameterIsCanonicalOnlyInsideQueryScopes() throws Exception {
        context("ExternalA", "!@x p(x) -> q(x);");
        context("ExternalC", "!p(John);");
        try (Fixture x = open("X-external")) {
            assertEquals(Boolean.TRUE, x.mind.query("!@x q(x) -> r(x);", null, false));
            x.connect("ExternalA", "ExternalC");
            long revision = x.data.getRevision();
            assertEquals(Boolean.TRUE, x.mind.query("?r(?);", new Object[]{"John"}, false));
            assertEquals(revision, x.data.getRevision());
            assertEquals(2, x.data.federationSnapshot().getConnections().size());
        }
    }

    private int proven(List<IContextFederation.CausalStep> steps, String predicate, boolean supplied) {
        for (int i=0; i<steps.size(); ++i) {
            IContextFederation.CausalStep step = steps.get(i);
            if (step.getQuery().contains(predicate) && !step.getQuery().contains("~")
                    && step.getTruth() == IContextFederation.FrontierTruth.TRUE
                    && (!supplied || !step.getSuppliedEvidence().isEmpty())) return i;
        }
        fail("Missing native proof of " + predicate); return -1;
    }
    private Set<String> set(String... values) { return new LinkedHashSet<String>(Arrays.asList(values)); }
    private Set<String> values(IContextFederation.ExplainResult result, String variable) { return result.getValues().stream().map(r -> r.getBindings().get(variable)).collect(Collectors.toSet()); }
    private Set<String> values(Mind mind, String variable) { Set<String> result = new LinkedHashSet<String>(); mind.getValues().forEach(r -> result.add(r.get(variable).toString())); return result; }
    private List<IContextFederation.CausalStep> steps(IContextFederation.ExplainResult result) {
        List<IContextFederation.CausalStep> steps = new ArrayList<IContextFederation.CausalStep>();
        for (IContextFederation.ExplainPass pass : result.getPasses()) steps.addAll(pass.getContinuation().getCausalSteps());
        return steps;
    }
    private IContextFederation.ExplainResult explain(Fixture x, String query) throws Exception {
        return new CanonicalCommandProcessor().execute(new CommandParser().parse("ctx explain " + query), x.user).getContextExplainResult();
    }
    private void context(String name, String... statements) throws Exception {
        try (Fixture fixture = open(name)) {
            for (String statement : statements) assertEquals(Boolean.TRUE, fixture.mind.query(statement, null, false));
        }
    }
    private Fixture open(String name) throws Exception {
        User user = new User(); user.setDatabaseDir(root.toString() + File.separator);
        DB data = new DB(); data.init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
        return new Fixture(user,data,mind);
    }
    private static final class Fixture implements AutoCloseable {
        final User user; final DB data; Mind mind;
        Fixture(User user, DB data, Mind mind) { this.user=user; this.data=data; this.mind=mind; }
        void connect(String... targets) throws Exception { for (String target : targets) data.connectContext(target); }
        @Override public void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    }
}
