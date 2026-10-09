package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.internal.IContextFederation;
import java.nio.file.Path;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class ContextConnectionLayerTest {
    @TempDir Path directory;
    User user; DB data; Mind mind;
    void open(String name) throws Exception {
        user = new User(); user.setDatabaseDir(directory + File.separator);
        data = new DB(); data.init(user); mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
    }
    void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    void setup() throws Exception {
        open("N");
        try { assertTrue(mind.query("!p(John);", null, false));
            assertTrue(mind.query("!@x p(x) -> q(x);", null, false)); }
        finally { close(); }
        open("X"); mind.query("!anchor(X);", null, false); data.connectContext("N");
    }
    CanonicalCommandProcessor.Result ask(String command) throws Exception {
        return new CanonicalCommandProcessor().execute(new CommandParser().parse("ctx ask N " + command), user);
    }
    @Test void incompatibleAuthoringRejectsBeforeSettlementAndLeavesQueriesUsable() throws Exception {
        open("N");
        try {
            mind.query("!male(John);", null, false);
            mind.query("!@x ~(male(x), female(x));", null, false);
        } finally { close(); }
        open("X");
        try {
            mind.query("!anchor(X);", null, false); data.connectContext("N");
            long revision = data.getRevision();
            Mind root = mind;
            org.kanger.exception.StorageLifecycleException rejection = assertThrows(
                    org.kanger.exception.StorageLifecycleException.class,
                    () -> root.query("!female(John);", null, false));
            assertEquals("STORAGE_CONTEXT_CONFLICT", rejection.getCode());
            assertEquals(revision, data.getRevision());
            assertFalse(mind.getSourceCode().contains("!female(John)"));
            assertEquals(Boolean.FALSE, mind.query("?female(John);", null, false));
            assertTrue(mind.queryCheck(false));
            assertTrue(mind.query("!female(Mary);", null, false));
        } finally { close(); }
        open("X");
        try { assertFalse(mind.getSourceCode().contains("!female(John)"));
            assertTrue(mind.query("?female(Mary);", null, false)); }
        finally { close(); }
    }

    @Test void rejectedExplicitCommitKeepsUserLayerAvailableForRollback() throws Exception {
        setup();
        try {
            long revision = data.getRevision();
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            processor.execute(parser.parse("transaction start"), user);
            Mind child = (Mind) user.getCurrentMind();
            child.query("!~p(John);", null, false);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,
                    () -> processor.execute(parser.parse("commit"), user));
            assertSame(child, user.getCurrentMind());
            assertEquals(revision, data.getRevision());
            processor.execute(parser.parse("rollback"), user);
            mind = (Mind) user.getCurrentMind();
            assertTrue(mind.query("?p(John);", null, false));
            assertFalse(mind.getSourceCode().contains("!~p(John)"));
        } finally { close(); }
    }

    @Test void pairValidationDoesNotReplaceConsistencyCheckStatusWithReplayLog() throws Exception {
        setup();
        try {
            assertTrue(mind.query("?", null, true));
            assertEquals("SUCCESS: No Collisions in Program",
                    mind.getCurrentLogRecord(org.kanger.enums.LogMode.ANALYZER).getRecord());
            assertTrue(mind.query("?q(John);", null, false));
            assertTrue(mind.query("?", null, true));
            assertEquals("SUCCESS: No Collisions in Program",
                    mind.getCurrentLogRecord(org.kanger.enums.LogMode.ANALYZER).getRecord());
        } finally { close(); }
    }

    @Test void federatedSolutionsRetainDetachedProofThroughEveryContext() throws Exception {
        open("A"); try { mind.query("!@x p(x) -> q(x);", null, false); } finally { close(); }
        open("B"); try { mind.query("!@x q(x) -> r(x);", null, false); } finally { close(); }
        open("C"); try { mind.query("!p(John);", null, false); } finally { close(); }
        open("X");
        try {
            mind.query("!p(Mary);", null, false);
            data.connectContext("A"); data.connectContext("B"); data.connectContext("C");
            assertTrue(mind.query("?$x r(x);", null, false));
            assertEquals(2, mind.getValues().size());
            for (org.kanger.interfaces.IRule rule : mind.getSolutions()) {
                IContextFederation.RuleRow proof = ContextProofProjection.solution(mind, rule);
                assertFalse(proof.causes.isEmpty(), proof.statement);
                java.util.Set<java.util.UUID> contexts = new java.util.HashSet<>();
                java.util.List<String> statements = new java.util.ArrayList<>();
                collectProof(proof.causes, contexts, statements);
                assertTrue(statements.stream().anyMatch(v -> v.contains("p(x) -> q(x)")), statements.toString());
                assertTrue(statements.stream().anyMatch(v -> v.contains("q(x) -> r(x)")), statements.toString());
                assertEquals(3, contexts.size(), statements.toString());
                java.util.UUID donor = data.federationSnapshot().getSourceContextId();
                if (proof.statement.contains("John"))
                    for (IContextFederation.Connection connection : data.federationSnapshot().getConnections())
                        if (connection.getLocator().equals("C")) donor = connection.getTargetContextId();
                assertTrue(contexts.contains(donor), statements.toString());
                assertTrue(statements.stream().anyMatch(v -> v.contains(proof.statement.contains("Mary")
                        ? "!p(Mary);" : "!p(John);")), statements.toString());
            }
            String oldFact = ContextProofProjection.factKey(mind.getSolutions().iterator().next(), mind);
            assertNull(mind.query("?r(Nobody);", null, false));
            assertTrue(mind.getContextProofs(oldFact).isEmpty());
        } finally { close(); }
    }
    private void collectProof(java.util.List<IContextFederation.ProofCause> proofs,
            java.util.Set<java.util.UUID> contexts, java.util.List<String> statements) {
        for (IContextFederation.ProofCause proof : proofs) {
            if (proof.contextSource != null) contexts.add(proof.contextSource.getContextId());
            statements.add(proof.ruleStatement);
            collectProof(proof.causes, contexts, statements);
        }
    }

    @Test void reopeningAndQueryingProductionsDoesNotPublishRevision() throws Exception {
        open("N");
        long revision;
        try {
            assertTrue(mind.query("!p(John);", null, false));
            assertTrue(mind.query("!@x p(x) -> q(x);", null, false));
            revision = data.getRevision();
        } finally { close(); }
        open("N");
        try {
            assertEquals(revision, data.getRevision());
            assertTrue(mind.query("?q(John);", null, false));
            assertEquals(revision, data.getRevision());
        } finally { close(); }
        open("N");
        try { assertEquals(revision, data.getRevision()); }
        finally { close(); }
    }

    @Test void editsAreSharedByQueriesOpinionsAndRulesAndSurvivePublication() throws Exception {
        setup();
        long targetRevision = data.federationSnapshot().getConnections().get(0).getPinnedRevision();
        try {
            ask("-p(John);"); ask("!p(Mary);");
            assertNull(mind.query("?q(John);", null, false));
            assertTrue(mind.query("?q(Mary);", null, false));
            assertTrue(mind.collectContextOpinions("N").get("N").isConfigured());
            assertEquals(IContextFederation.FrontierTruth.TRUE, ask("?q(Mary);").getFederationQueryResult().getResultTruth());
            assertTrue(data.federationSnapshot().hasWorkingChanges());
            data.saveConnections(mind);
            assertFalse(data.federationSnapshot().hasWorkingChanges());
        } finally { close(); }
        open("X");
        try { assertTrue(mind.query("?q(Mary);", null, false)); assertNull(mind.query("?q(John);", null, false)); }
        finally { close(); }
        open("N");
        try { assertEquals(targetRevision, data.getRevision()); assertTrue(mind.query("?p(John);", null, false));
            assertNull(mind.query("?p(Mary);", null, false)); }
        finally { close(); }
    }
    @Test void rollbackRestoresConnectionInitializationAndRejectedEditIsAtomic() throws Exception {
        setup();
        try {
            Mind root = mind;
            new CanonicalCommandProcessor().execute(new CommandParser().parse("transaction start"), user);
            mind = (Mind) user.getCurrentMind();
            ask("!p(Mary);");
            assertTrue(mind.query("?q(Mary);", null, false));
            new CanonicalCommandProcessor().execute(new CommandParser().parse("transaction rollback"), user);
            mind = (Mind) user.getCurrentMind();
            assertNull(mind.query("?q(Mary);", null, false));
            assertTrue(data.federationSnapshot().getConnections().get(0).getInitialization().isEmpty());
            ask("!p(Mary);");
            assertThrows(Exception.class, () -> ask("!~p(Mary);"));
            assertEquals(1, data.federationSnapshot().getConnections().get(0).getInitialization().size());
            assertTrue(mind.query("?q(Mary);", null, false));
        } finally { close(); }
    }
    @Test void sourceRoundTripRetainsCommandsAfterTheirConnection() throws Exception {
        setup(); String source;
        try {
            ask("-p(John);"); ask("!p(Mary);");
            source = SourceContextMaterializer.materializeCurrentLevel(mind);
            assertTrue(source.contains("//! ctx init -p(John);"));
            assertTrue(source.indexOf("ctx connect") < source.indexOf("ctx init"));
        } finally { close(); }
        open("Y");
        try { assertTrue(mind.compile(source, null, false));
            assertTrue(mind.query("?q(Mary);", null, false)); assertNull(mind.query("?q(John);", null, false)); }
        finally { close(); }
    }
    @Test void unknownOpinionContainsHypothesesWithoutConditionalSolutions() throws Exception {
        open("B");
        try { mind.query("!@x q(x) -> r(x);", null, false); } finally { close(); }
        open("X");
        try {
            mind.query("!anchor(X);", null, false); data.connectContext("B");
            assertNull(mind.query("?r(John);", null, false));
            IContextFederation.Opinion opinion = mind.collectContextOpinions("B").get("B");
            assertEquals(IContextFederation.FrontierTruth.UNKNOWN, opinion.getResult().getResultTruth());
            assertTrue(opinion.getSolutions().isEmpty());
            assertTrue(opinion.getResult().getValues().isEmpty());
            assertEquals(1, opinion.getResult().getProvisionalHypotheses().size());
            assertEquals("!q(John);", opinion.getResult().getProvisionalHypotheses().get(0).getStatement());
        } finally { close(); }
    }
    @Test void failedRepinPreservesPinnedRevisionAndEffectiveLayer() throws Exception {
        setup();
        try {
            ask("!p(Mary);");
            long pinned = data.federationSnapshot().getConnections().get(0).getPinnedRevision();
            User other = new User(); other.setDatabaseDir(directory + File.separator);
            DB original = new DB(); original.init(other);
            Mind target = new Mind(other); other.setCurrentMind(target);
            target = (Mind) target.useStorage("N"); other.setCurrentMind(target);
            long newer;
            try { assertTrue(target.query("!~p(Mary);", null, false)); newer = original.getRevision(); }
            finally { other.setCurrentMind(target.closeStorage()); }
            assertThrows(Exception.class, () -> data.switchContextRevision("N", newer));
            assertEquals(pinned, data.federationSnapshot().getConnections().get(0).getPinnedRevision());
            assertTrue(mind.query("?q(Mary);", null, false));
        } finally { close(); }
    }

    @Test void quantifiedCommandsAndMultilineSourceRoundTripPreserveOriginalText() throws Exception {
        setup(); String source;
        try {
            ask("!@x\n q(x) -> r(x);");
            assertTrue(mind.query("?r(John);", null, false));
            source = SourceContextMaterializer.materializeCurrentLevel(mind);
            assertTrue(source.contains("//! ctx init+  q(x) -> r(x);"));
        } finally { close(); }
        open("Y");
        try {
            assertTrue(mind.compile(source, null, false));
            assertTrue(mind.query("?r(John);", null, false));
        } finally { close(); }
    }
    @Test void authoringAndSourceImportQualifyEffectiveMaskedContext() throws Exception {
        setup(); String source;
        try {
            ask("-p(John);");
            assertTrue(mind.query("!~p(John);", null, false));
            data.connectContext("N");
            assertEquals(1, data.federationSnapshot().getConnections().get(0).getInitialization().size());
            data.saveConnections(mind);
            source = SourceContextMaterializer.materializeCurrentLevel(mind);
        } finally { close(); }
        open("Y");
        try { assertTrue(mind.compile(source, null, false));
            assertEquals(Boolean.FALSE, mind.query("?p(John);", null, false)); }
        finally { close(); }
    }
    @Test void nativeNoOpsRetainIntentAndDoNotBlockInitializationReplay() throws Exception {
        setup();
        try {
            ask("!p(John);"); ask("-p(Nobody);");
            data.saveConnections(mind);
        } finally { close(); }
        open("X");
        try { assertTrue(mind.query("?q(John);", null, false));
            assertEquals(2, data.federationSnapshot().getConnections().get(0).getInitialization().size()); }
        finally { close(); }
    }
}
