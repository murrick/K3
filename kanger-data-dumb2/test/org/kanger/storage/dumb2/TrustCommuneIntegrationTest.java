package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.internal.IContextFederation;
import static org.junit.jupiter.api.Assertions.*;

class TrustCommuneIntegrationTest {
    @TempDir Path directory;
    private User user;
    private DB data;
    private Mind mind;
    private final CommandParser parser = new CommandParser();
    private final CanonicalCommandProcessor processor = new CanonicalCommandProcessor();

    @Test void fullFamilyQueriesPreservePositiveAndNegativeAnswersInBothConnectionOrders() throws Exception {
        family();
        for (String[] order : Arrays.asList(new String[]{"facts", "natives"}, new String[]{"natives", "facts"})) {
            open("X");
            try {
                for (String name : order) command("ctx connect " + name + " trust own");
                assertEquals(2, data.federationSnapshot().getConnections().size());
                for (String query : Arrays.asList("?$x father(John,x);", "?$x child(x,John);")) {
                    rows(query, "Tom", "Sarah");
                    boolean commune = false;
                    Set<UUID> sources = new HashSet<>();
                    for (org.kanger.interfaces.IRule solution : mind.getSolutions()) {
                        IContextFederation.RuleRow proof = ContextProofProjection.solution(mind, solution);
                        commune |= collect(proof.causes, sources);
                    }
                    assertTrue(commune, "Joint answers must identify their commune");
                    assertEquals(2, sources.size(), "Both facts and rules must retain their real proof sources");
                }
                assertEquals(Boolean.FALSE, mind.query("?$x mother(John,x);", null, false));
                assertEquals(Boolean.FALSE, mind.query("?mother(John,Mary);", null, false));
                long revision = data.getRevision();
                assertEquals(Boolean.FALSE, mind.query("?$x mother(?,x);", new Object[]{"John"}, false));
                assertEquals(revision, data.getRevision());
                assertNull(mind.query("?$x unknownRelation(John,x);", null, false));
                IContextFederation.ExplainResult diagnosis = mind.explainQuery("?$x father(John,x);");
                List<IContextFederation.CausalStep> steps = new ArrayList<>();
                diagnosis.getPasses().forEach(pass -> steps.addAll(pass.getContinuation().getCausalSteps()));
                assertTrue(steps.stream().anyMatch(step -> "own".equals(step.getTarget().getCommune())));
                assertTrue(steps.stream().filter(step -> step.getTarget().getCommune()!=null)
                        .allMatch(step -> step.getTarget().getCommuneMembers().size()==2));
                assertTrue(data.executeOpinions(mind, null, "?father(John,Tom);", Collections.emptyList()).containsKey("facts"));
                command("ctx close facts");
                assertEquals(1, data.federationSnapshot().getConnections().size());
                assertNull(mind.query("?father(John,Tom);", null, false));
            } finally { close(); }
        }
    }

    @Test void negativeCommuneProofDoesNotOverrideAnIndependentPositiveOpinion() throws Exception {
        family();
        create("opposing", "!mother(John,Tom);");
        open("X");
        try {
            command("ctx connect facts trust own"); command("ctx connect natives trust own");
            assertEquals(Boolean.FALSE, mind.query("?$x mother(John,x);", null, false));
            assertTrue(mind.getValues().isEmpty());
            assertTrue(mind.getSolutions().isEmpty());
            command("ctx connect opposing");
            assertEquals(IContextFederation.FrontierTruth.CONFLICT,
                    mind.explainQuery("?$x mother(John,x);").getFinalTruth());
            command("ctx close opposing");
            assertEquals(Boolean.FALSE, mind.query("?$x mother(John,x);", null, false));
            rows("?$x father(John,x);", "Tom", "Sarah");
        } finally { close(); }
    }

    @Test void privateEditsAndNestedRollbackRestoreReadyCommuneStates() throws Exception {
        create("rules", "!@x p(x) -> q(x);"); create("facts", "!p(John);"); create("X", "!anchor(X);");
        open("X");
        try {
            command("ctx connect rules trust own"); command("ctx connect facts trust own");
            rows("?$x q(x);", "John");
            command("transaction start");
            command("ctx ask facts -p(John);"); command("ctx ask facts !p(Mary);");
            rows("?$x q(x);", "Mary");
            command("transaction start");
            command("ctx ask facts -p(Mary);"); command("ctx ask facts !p(Tom);");
            rows("?$x q(x);", "Tom");
            command("rollback"); rows("?$x q(x);", "Mary");
            command("rollback"); rows("?$x q(x);", "John");
            assertTrue(data.federationSnapshot().getConnections().stream().allMatch(c -> c.getInitialization().isEmpty()));
        } finally { close(); }
        open("facts");
        try { rows("?$x p(x);", "John"); } finally { close(); }
    }

    @Test void conflictingCandidateIsRejectedAndPublishKeepsOnlyConfiguration() throws Exception {
        create("rules", "!@x p(x) -> q(x);"); create("facts", "!p(John);");
        create("negative", "!~p(John);"); create("X", "!anchor(X);");
        String exported;
        open("X");
        try {
            command("ctx connect rules trust own"); command("ctx connect facts trust own");
            assertThrows(Exception.class, () -> command("ctx connect negative trust own"));
            assertEquals(2, data.federationSnapshot().getConnections().size());
            rows("?$x q(x);", "John");
            command("ctx ask facts -p(John);"); command("ctx ask facts !p(Mary);");
            rows("?$x q(x);", "Mary");
            command("ctx publish trust-release");
            exported = mind.getSourceCode();
            assertTrue(exported.contains(" trust own"), exported);
            assertTrue(exported.contains("//! ctx init !p(Mary);"), exported);
            assertFalse(exported.contains("!q(Mary);"), exported);
            try (SnapshotMindRuntime snapshot = SnapshotMindRuntime.open(directory.resolve("X"),
                    new RevisionRef(data.getContextId(), data.getRevision()), "check-published")) {
                assertEquals(1, snapshot.getMind().getRules().size(), "Joint productions are absent from durable X");
            }
        } finally { close(); }
        open("X");
        try {
            assertTrue(data.federationSnapshot().getConnections().stream().allMatch(c -> "own".equals(c.getTrustGroup())));
            assertFalse(data.federationSnapshot().hasWorkingChanges());
            rows("?$x q(x);", "Mary");
        } finally { close(); }
        open("copy");
        try { assertTrue(mind.compile(exported, null, false)); rows("?$x q(x);", "Mary"); }
        finally { close(); }
    }

    @Test void distinctCommunesKeepConflictingOpinionsAndSafeRows() throws Exception {
        create("positive", "!p(John); !p(Mary);"); create("negative", "!~p(John);"); create("X", "!anchor(X);");
        open("X");
        try {
            command("ctx connect positive trust first"); command("ctx connect negative trust second");
            rows("?$x p(x);", "Mary");
            Map<String,IContextFederation.Opinion> opinions = data.executeOpinions(mind, null, "?p(John);", Collections.emptyList());
            assertEquals(IContextFederation.FrontierTruth.TRUE, opinions.get("positive").getResult().getResultTruth());
            assertEquals(IContextFederation.FrontierTruth.FALSE, opinions.get("negative").getResult().getResultTruth());
            assertNull(opinions.get("positive").getSource().getCommune(), "An isolated opinion belongs to its physical Context");
            command("transaction start");
            command("ctx close positive");
            command("rollback");
            rows("?$x p(x);", "Mary");
        } finally { close(); }
    }

    @Test void symbolicMetadataRoundTripAndInvalidModeHaveNoSideEffects() throws Exception {
        create("uses trust words", "!p(John);"); create("X", "!anchor(X);");
        open("X");
        try {
            String canonicalCommand = new org.kanger.command.CommandFormatter().format(parser.parse("ctx connect \"uses trust words\" trust 7"));
            command(canonicalCommand);
            assertEquals("7", parser.parse(canonicalCommand).getArgument("trustGroup"));
            String source = SourceContextMaterializer.materializeCurrentLevel(mind);
            assertTrue(source.contains("//! ctx connect \"uses trust words\"@"), source);
            assertTrue(source.contains(" trust 7"), source);
            assertThrows(org.kanger.command.CommandParseException.class, () -> parser.parse("ctx connect x trust \"bad name\""));
            assertEquals(1, data.federationSnapshot().getConnections().size());
            assertEquals("7", data.federationSnapshot().getConnections().get(0).getTrustGroup());
            close(); open("copy");
            assertTrue(mind.compile(source, null, false));
            assertEquals("7", data.federationSnapshot().getConnections().get(0).getTrustGroup());
            rows("?$x p(x);", "John");
        } finally { close(); }
    }

    private boolean collect(List<IContextFederation.ProofCause> causes, Set<UUID> sources) {
        boolean commune = false;
        for (IContextFederation.ProofCause cause : causes) {
            if (cause.commune != null) {
                commune = true; assertEquals("own", cause.commune); assertEquals(2, cause.communeMembers.size());
                assertNull(cause.contextSource, "Joint answer must not impersonate a routing member");
            }
            if (cause.contextSource != null) sources.add(cause.contextSource.getContextId());
            commune |= collect(cause.causes, sources);
        }
        return commune;
    }
    private void family() throws Exception {
        String source;
        try (java.io.InputStream input = getClass().getResourceAsStream("natives.k")) {
            assertNotNull(input);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            source = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
        StringBuilder rules = new StringBuilder(), facts = new StringBuilder();
        for (String line : source.split("\\R")) {
            if (line.startsWith("!@")) rules.append(line).append('\n');
            else if (line.startsWith("!")) facts.append(line).append('\n');
        }
        create("natives", rules.toString()); create("facts", facts.toString()); create("X", "!anchor(X);");
    }
    private void rows(String query, String... expected) throws Exception {
        assertEquals(Boolean.TRUE, mind.query(query, null, false), query);
        Set<String> actual = new HashSet<>();
        mind.getValues().forEach(row -> actual.add(row.get("x").toString()));
        assertEquals(new HashSet<>(Arrays.asList(expected)), actual, query);
    }
    private void command(String source) throws Exception {
        CanonicalCommandProcessor.Result result = processor.execute(parser.parse(source), user);
        assertTrue(result.isSuccess(), source + ": " + result.getDescription());
        mind = (Mind) user.getCurrentMind();
    }
    private void create(String name, String source) throws Exception {
        open(name);
        try { assertTrue(mind.compile(source, null, false)); } finally { close(); }
    }
    private void open(String name) throws Exception {
        user = new User(); user.setDatabaseDir(directory + File.separator);
        data = new DB(); data.init(user); mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
    }
    private void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
}
