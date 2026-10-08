package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.internal.IContextFederation;
import java.nio.file.Path;
import java.io.File;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextOpinionsTest {
    @Test void nativeOpinionsDoNotAccumulateHypothesesFromPreviousQueries() throws Exception {
        Mind nativeMind = open("N");
        try {
            assertTrue(nativeMind.compile(new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("natives.k")), java.nio.charset.StandardCharsets.UTF_8), null, false));
        } finally { close(nativeMind); }
        create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("N");
            assertNull(mind.query("?$x son(John,x);", null, false));
            Set<String> fresh = opinionHypotheses(command("ctx opinions N").get("N"));
            assertNull(mind.query("?male(Tom);", null, false));
            command("ctx opinions N");
            assertNull(mind.query("?$x son(John,x);", null, false));
            Set<String> afterPrevious = opinionHypotheses(command("ctx opinions N").get("N"));
            assertEquals(fresh, afterPrevious);
            assertFalse(fresh.isEmpty());
        } finally { close(mind); }
    }

    private Set<String> opinionHypotheses(IContextFederation.Opinion opinion) {
        Set<String> statements = new TreeSet<>();
        for (IContextFederation.ProvisionalHypothesis hypothesis : opinion.getResult().getProvisionalHypotheses())
            statements.add(hypothesis.getStatement());
        return statements;
    }
    @Test void unresolvedEnumerationAfterOpinionsKeepsFinalResultMessage() throws Exception {
        create("N", "!@x @y son(x,y) -> male(x);", "!child(Tom,Mary);");
        create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("N");
            assertNull(mind.query("?male(Tom);", null, true));
            command("ctx opinions");
            assertNull(mind.query("?$x son(John,x);", null, true));
            assertEquals("Result: WHO KNOWS? No Hypothesis.",
                    mind.getCurrentLogRecord(org.kanger.enums.LogMode.ANALYZER).getRecord());
        } finally { close(mind); }
    }
    @TempDir Path directory;
    private User user;
    private DB data;
    private Mind open(String name) throws Exception {
        user = new User(); user.setDatabaseDir(directory.toString() + File.separator);
        data = new DB(); data.init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind); return mind;
    }
    private void close(Mind mind) throws Exception { user.setCurrentMind(mind.closeStorage()); }
    private void create(String name, String... facts) throws Exception {
        Mind mind = open(name);
        try { for (String fact : facts) assertEquals(Boolean.TRUE, mind.query(fact, null, false), fact); }
        finally { close(mind); }
    }
    private Map<String,IContextFederation.Opinion> command(String source) throws Exception {
        return new CanonicalCommandProcessor().execute(new CommandParser().parse(source), user).getContextOpinions();
    }

    @Test void contradictoryOpinionsRemainSeparateAndCachedWithoutChangingX() throws Exception {
        create("A", "!p(John);"); create("B", "!~p(John);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A"); data.connectContext("B");
            long revision = data.getRevision();
            assertNull(mind.query("?p(John);", null, false));
            assertTrue(mind.hasOtherContextOpinions());
            int rules = mind.getRules().size(); int solves = mind.getSolutions().size();
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            assertEquals(new HashSet<>(Arrays.asList("A", "B")), opinions.keySet());
            assertEquals(IContextFederation.FrontierTruth.TRUE, opinions.get("A").getResult().getResultTruth());
            assertEquals(IContextFederation.FrontierTruth.FALSE, opinions.get("B").getResult().getResultTruth());
            assertFalse(opinions.get("A").getSolutions().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> opinions.clear());
            assertSame(opinions, command("ctx values"));
            assertSame(opinions.get("A"), command("ctx solves A").get("A"));
            assertSame(opinions, command("ctx when"));
            assertEquals(revision, data.getRevision()); assertEquals(rules, mind.getRules().size());
            assertEquals(solves, mind.getSolutions().size()); assertEquals(2, data.federationSnapshot().getConnections().size());
            mind.query("?anchor(X);", null, false);
            assertThrows(CommandErrorException.class, () -> command("ctx values"));
        } finally { close(mind); }
    }

    @Test void unknownHypothesesAreLocalAndDoNotNeedAHint() throws Exception {
        create("A", "!@x p(x) -> r(x);"); create("B", "!@x q(x) -> r(x);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A"); data.connectContext("B");
            assertNull(mind.query("?r(John);", null, false));
            assertFalse(mind.hasOtherContextOpinions());
            int nativeHypotheses = mind.getHypothesis().size();
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            assertEquals(2, opinions.size());
            assertTrue(opinions.get("A").getResult().getProvisionalHypotheses().stream().anyMatch(h -> h.getStatement().contains("p(John)")));
            assertTrue(opinions.get("B").getResult().getProvisionalHypotheses().stream().anyMatch(h -> h.getStatement().contains("q(John)")));
            for (IContextFederation.Opinion opinion : opinions.values())
                for (IContextFederation.ProvisionalHypothesis hypothesis : opinion.getResult().getProvisionalHypotheses())
                    assertEquals(opinion.getSource().getContextId(), hypothesis.getSource().getContextId());
            assertEquals(nativeHypotheses, mind.getHypothesis().size());
        } finally { close(mind); }
    }

    @Test void opinionsUseCompleteQueryNotIntermediateFrontierOrExternalEvidence() throws Exception {
        create("A", "!@x p(x) -> q(x);"); create("C", "!p(Mary);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A"); data.connectContext("C");
            assertEquals(Boolean.TRUE, mind.query("?q(Mary);", null, false));
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            assertEquals(IContextFederation.FrontierTruth.UNKNOWN, opinions.get("A").getResult().getResultTruth());
            assertFalse(opinions.containsKey("C"));
            assertTrue(opinions.get("A").getResult().getProvisionalHypotheses().stream().anyMatch(h -> h.getStatement().contains("p(Mary)")));
            assertNull(mind.query("?p(Mary), q(Mary);", null, false));
            assertFalse(command("ctx opinions").values().stream().anyMatch(o -> o.getResult().getResultTruth()==IContextFederation.FrontierTruth.TRUE));
        } finally { close(mind); }
    }

    @Test void liveU1AndMutationTopologyInvalidation() throws Exception {
        create("A", "!p(John);"); create("X", "!anchor(X);");
        Mind root = open("X"); Mind work = root;
        try {
            data.connectContext("A"); work = new Mind(root); user.setCurrentMind(work);
            assertEquals(Boolean.TRUE, work.query("!p(Mary);", null, false));
            work.query("?$x p(x);", null, false);
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            assertTrue(opinions.get("X").isWorking());
            assertEquals("Mary", opinions.get("X").getResult().getValues().get(0).getBindings().get("x"));
            assertEquals("John", opinions.get("A").getResult().getValues().get(0).getBindings().get("x"));
            work.getRules().get(0).setComment("Updated local comment");
            assertThrows(CommandErrorException.class, () -> command("ctx solves"));
            work.query("?$x p(x);", null, false); command("ctx opinions");
            work.query("!p(Tom);", null, false);
            assertThrows(CommandErrorException.class, () -> command("ctx values"));
            work.query("?$x p(x);", null, false); command("ctx opinions");
            data.disconnectContext("A");
            assertThrows(CommandErrorException.class, () -> command("ctx when"));
        } finally { root.discardEphemeral(work); user.setCurrentMind(root); close(root); }
    }

    @Test void compoundOpinionContainsOnlyFullQueryBindingsAndBothProofs() throws Exception {
        create("A", "!p(Mary);", "!age(Mary,30);"); create("B", "!p(John);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A"); data.connectContext("B");
            mind.query("?$x $y p(x), age(x,y), y > 10;", null, false);
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            IContextFederation.Opinion a = opinions.get("A");
            assertEquals(IContextFederation.FrontierTruth.TRUE, a.getResult().getResultTruth());
            assertEquals(1, a.getResult().getValues().size());
            assertEquals("Mary", a.getResult().getValues().get(0).getBindings().get("x"));
            assertEquals("30.0", a.getResult().getValues().get(0).getBindings().get("y"));
            assertEquals(2, a.getSolutions().size());
            assertTrue(a.getSolutions().stream().anyMatch(r -> r.statement.contains("p(Mary)")));
            assertTrue(a.getSolutions().stream().anyMatch(r -> r.statement.contains("age(Mary")));
            assertFalse(opinions.values().stream().anyMatch(o -> o.getResult().getValues().stream()
                    .anyMatch(row -> row.getBindings().containsValue("John"))));
        } finally { close(mind); }
    }

    @Test void falseOpinionAlongsideUnknownHypothesesSignalsAlternatives() throws Exception {
        create("A", "!~r(John);"); create("B", "!@x p(x) -> r(x);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A"); data.connectContext("B");
            assertEquals(Boolean.FALSE, mind.query("?r(John);", null, true));
            assertTrue(mind.getCurrentLogRecord(org.kanger.enums.LogMode.ANALYZER).getRecord().contains("Result: FALSE"));
            assertTrue(mind.hasOtherContextOpinions());
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            assertEquals(IContextFederation.FrontierTruth.FALSE, opinions.get("A").getResult().getResultTruth());
            assertEquals(IContextFederation.FrontierTruth.UNKNOWN, opinions.get("B").getResult().getResultTruth());
            assertFalse(opinions.get("B").getResult().getProvisionalHypotheses().isEmpty());
            assertFalse(opinions.get("A").getSolutions().isEmpty());
        } finally { close(mind); }
    }

    @Test void advancingTargetCurrentDoesNotReinterpretSavedOrNewOpinions() throws Exception {
        create("A", "!p(John);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            long pin = data.connectContext("A").getPinnedRevision();
            mind.query("?$x p(x);", null, false);
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            User sourceUser = user; DB sourceData = data;
            try { create("A", "!p(Mary);"); } finally { user = sourceUser; data = sourceData; }
            assertSame(opinions, command("ctx values"));
            IContextFederation.Opinion opinion = command("ctx opinions A").get("A");
            assertEquals(pin, opinion.getSource().getRevision());
            assertEquals(1, opinion.getResult().getValues().size());
            assertEquals("John", opinion.getResult().getValues().get(0).getBindings().get("x"));
            close(mind); mind = open("X");
            assertThrows(CommandErrorException.class, () -> command("ctx opinions"));
        } finally { close(mind); }
    }

    @Test void derivedOpinionPreservesNativeRuleAndDonorProvenanceAfterClose() throws Exception {
        create("A", "!@x p(x) -> q(x);", "!p(Mary);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A"); mind.query("?q(Mary);", null, false);
            IContextFederation.Opinion a = command("ctx opinions A").get("A");
            assertFalse(a.getSolutions().isEmpty());
            assertTrue(a.getSolutions().stream().flatMap(row -> row.causes.stream())
                    .anyMatch(c -> c.ruleStatement.contains("p(x)") && c.donorStatement.contains("p(Mary)")));
            assertThrows(UnsupportedOperationException.class, () -> a.getSolutions().get(0).causes.clear());
            assertSame(a, command("ctx solves A").get("A"));
        } finally { close(mind); }
    }

    @Test void sourceDependenciesAreNeverTraversedAndTransactionsInvalidateViews() throws Exception {
        create("N", "!p(John);"); create("A", "!@x p(x) -> q(x);");
        Mind a = open("A");
        try { data.connectContext("N"); data.saveConnections(a); } finally { close(a); }
        create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            data.connectContext("A");
            assertNull(mind.query("?q(John);", null, false));
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions");
            assertEquals(Collections.singleton("A"), opinions.keySet());
            assertEquals(IContextFederation.FrontierTruth.UNKNOWN, opinions.get("A").getResult().getResultTruth());
            assertEquals(1, data.federationSnapshot().getConnections().size());
            Mind transaction = new Mind(mind); user.setCurrentMind(transaction);
            mind.discardEphemeral(transaction); user.setCurrentMind(mind);
            assertThrows(CommandErrorException.class, () -> command("ctx values"));
            mind.query("?q(John);", null, false); command("ctx opinions");
            data.executeIsolatedQuery(mind, "A", "?p(John);");
            assertThrows(CommandErrorException.class, () -> command("ctx when"));
        } finally { close(mind); }
    }

    @Test void exactPinAndTypedParametersSurviveMutableCallerInput() throws Exception {
        create("A", "!age(Mary,30);"); create("X", "!anchor(X);");
        Mind mind = open("X");
        try {
            long pin = data.connectContext("A").getPinnedRevision();
            Object[] parameters = {"Mary", 30};
            assertEquals(Boolean.TRUE, mind.query("?age(?,?);", parameters, false));
            parameters[0] = "John"; parameters[1] = 99;
            Map<String,IContextFederation.Opinion> opinions = command("ctx opinions A");
            assertEquals(pin, opinions.get("A").getSource().getRevision());
            assertEquals(IContextFederation.FrontierTruth.TRUE, opinions.get("A").getResult().getResultTruth());
            assertFalse(opinions.get("A").getSolutions().isEmpty());
            assertThrows(CommandErrorException.class, () -> command("ctx opinions Unconnected"));
            assertSame(opinions.get("A"), command("ctx solves A").get("A"));
        } finally { close(mind); }
    }
}
