package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.exception.CommandErrorException;
import static org.junit.jupiter.api.Assertions.*;

class CommuneRuntimeTest {
    @TempDir Path directory;

    @Test void fullFamilyCompositionReturnsBothChildrenAndFathers() throws Exception {
        String source;
        try (java.io.InputStream input = getClass().getResourceAsStream("natives.k")) {
            assertNotNull(input);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
        StringBuilder rules = new StringBuilder(), facts = new StringBuilder();
        for (String line : source.split("\\R")) {
            if (line.startsWith("!@")) rules.append(line).append('\n');
            else if (line.startsWith("!")) facts.append(line).append('\n');
        }
        create("rules", rules.toString()); create("facts", facts.toString()); create("X", "!anchor(X);");
        ContextConnection a = connection("rules"), b = connection("facts");
        try {
            for (List<ContextConnection> order : Arrays.asList(Arrays.asList(a,b), Arrays.asList(b,a)))
                try (CommuneRuntime runtime = CommuneRuntime.prepare(order)) {
                    for (String query : Arrays.asList("?$x father(John,x);", "?$x child(x,John);")) {
                        Mind work = Mind.ephemeralChild(runtime.mind());
                        try {
                            assertTrue(work.queryCanonical(query, new LinkedList<>(), false));
                            Set<String> values = new HashSet<>();
                            work.getValues().forEach(row -> values.add(row.get("x").toString()));
                            assertEquals(new HashSet<>(Arrays.asList("Tom", "Sarah")), values, query);
                        } finally { runtime.mind().discardEphemeral(work); }
                    }
                    assertFalse(runtime.mind().isStorageUsed(), "Joint productions have no persistent provider");
                }
        } finally { a.closeLayer(); b.closeLayer(); }
    }

    @Test void rejectedCandidateDoesNotReplaceExistingJointRuntime() throws Exception {
        create("A", "!p(John);"); create("B", "!~p(John);"); create("X", "!anchor(X);");
        ContextConnection a = connection("A"), b = connection("B");
        try (CommuneRuntime accepted = CommuneRuntime.prepare(Collections.singletonList(a))) {
            assertThrows(CommandErrorException.class, () -> CommuneRuntime.prepare(Arrays.asList(a,b)));
            Mind work = Mind.ephemeralChild(accepted.mind());
            try { assertTrue(work.queryCanonical("?p(John);", new LinkedList<>(), false)); }
            finally { accepted.mind().discardEphemeral(work); }
        } finally { a.closeLayer(); b.closeLayer(); }
    }

    @Test void changedMemberHasIndependentRuntimeAndPreviousStateRemainsUsable() throws Exception {
        create("A", "!@x p(x) -> q(x);"); create("B", "!p(John);"); create("X", "!anchor(X);");
        ContextConnection a = connection("A"), b = connection("B");
        ContextConnection changed = new ContextConnection(b.getTargetLocation(), b.getTarget(),
                b.getCertificate(), Arrays.asList("-p(John);", "!p(Mary);"));
        try (CommuneRuntime previous = CommuneRuntime.prepare(Arrays.asList(a,b));
             CommuneRuntime next = CommuneRuntime.prepare(Arrays.asList(a,changed))) {
            for (int repeat = 0; repeat < 3; repeat++) {
                assertRows(previous, "John"); assertRows(next, "Mary");
            }
        } finally { a.closeLayer(); b.closeLayer(); changed.closeLayer(); }
        // The private edits and joint productions never changed the pin.
        try (SnapshotMindRuntime original = SnapshotMindRuntime.open(b.getTargetLocation(), b.getTarget(), "check-source")) {
            Mind work = Mind.ephemeralChild(original.getMind());
            try {
                assertTrue(work.queryCanonical("?p(John);", new LinkedList<>(), false));
                assertNull(work.queryCanonical("?p(Mary);", new LinkedList<>(), false));
            } finally { original.getMind().discardEphemeral(work); }
        }
    }

    private void assertRows(CommuneRuntime runtime, String expected) throws Exception {
        Mind work = Mind.ephemeralChild(runtime.mind());
        try {
            assertTrue(work.queryCanonical("?$x q(x);", new LinkedList<>(), false));
            Set<String> rows = new HashSet<>();
            work.getValues().forEach(row -> rows.add(row.get("x").toString()));
            assertEquals(Collections.singleton(expected), rows);
        } finally { runtime.mind().discardEphemeral(work); }
    }

    @Test void checkpointsReusePreparedStatesAcrossNestedChangesAndQueries() throws Exception {
        create("A", "!@x p(x) -> q(x);"); create("B", "!p(John);"); create("X", "!anchor(X);");
        ContextConnection a = connection("A"), b = connection("B");
        ContextConnection mary = new ContextConnection(b.getTargetLocation(), b.getTarget(),
                b.getCertificate(), Arrays.asList("-p(John);", "!p(Mary);"));
        ContextConnection tom = new ContextConnection(b.getTargetLocation(), b.getTarget(),
                b.getCertificate(), Arrays.asList("-p(John);", "!p(Tom);"));
        CommuneRuntime original;
        try (CommuneRuntimeCache cache = new CommuneRuntimeCache()) {
            CommuneRuntimeCache.State u0 = cache.prepare(group(a, b));
            CommuneRuntimeCache.State u1 = cache.prepare(group(a, mary));
            CommuneRuntimeCache.State u2 = cache.prepare(group(a, tom));
            original = u0.groups().get("own");
            assertNotSame(original, u1.groups().get("own"));
            assertRows(u2.groups().get("own"), "Tom");
            // Restoring either checkpoint needs no preparation or source replay.
            assertRows(u1.groups().get("own"), "Mary");
            assertRows(original, "John");
            for (int i = 0; i < 3; i++) {
                assertSame(original, cache.prepare(group(b, a)).groups().get("own"));
                assertRows(original, "John");
            }
            assertTrue(cache.prepare(Collections.emptyMap()).groups().isEmpty());
            assertSame(original, cache.prepare(group(a, b)).groups().get("own"));
            assertThrows(UnsupportedOperationException.class, () -> u0.groups().clear());
        } finally { a.closeLayer(); b.closeLayer(); mary.closeLayer(); tom.closeLayer(); }
        assertThrows(IllegalStateException.class, original::mind);
    }

    @Test void sourceRecertificationKeepsTheSameComputedKnowledge() throws Exception {
        create("A", "!p(John);"); create("X", "!anchor(X);");
        ContextConnection a = connection("A");
        CompatibilityCertificate old = a.getCertificate();
        RevisionRef source = old.getLeft().equals(a.getTarget()) ? old.getRight() : old.getLeft();
        ContextConnection recertified = a.recertified(new CompatibilityCertificate(a.getTarget(),
                new RevisionRef(source.getContextId(), source.getRevision() + 1), old.getSemanticVersion()));
        try (CommuneRuntimeCache cache = new CommuneRuntimeCache()) {
            CommuneRuntime first = cache.prepare(group(a)).groups().get("own");
            assertSame(first, cache.prepare(group(recertified)).groups().get("own"));
        } finally { a.closeLayer(); recertified.closeLayer(); }
    }

    @Test void failedMultiGroupCandidateLeavesAcceptedRuntimeUsable() throws Exception {
        create("A", "!p(John);"); create("B", "!~p(John);"); create("X", "!anchor(X);");
        ContextConnection a = connection("A"), b = connection("B");
        try (CommuneRuntimeCache cache = new CommuneRuntimeCache()) {
            CommuneRuntime accepted = cache.prepare(group(a)).groups().get("own");
            Map<String,List<ContextConnection>> candidate = new LinkedHashMap<>();
            candidate.put("other", Collections.singletonList(b));
            candidate.put("own", Arrays.asList(a,b));
            assertThrows(IllegalArgumentException.class, () -> cache.prepare(candidate));
            candidate.put("other", Collections.singletonList(a));
            candidate.put("own", Collections.singletonList(b));
            assertEquals(2, cache.prepare(candidate).groups().size(), "Separate communes may disagree");
            assertThrows(CommandErrorException.class, () -> cache.prepare(group(a,b)));
            assertSame(accepted, cache.prepare(group(a)).groups().get("own"));
            Mind work = Mind.ephemeralChild(accepted.mind());
            try { assertTrue(work.queryCanonical("?p(John);", new LinkedList<>(), false)); }
            finally { accepted.mind().discardEphemeral(work); }
        } finally { a.closeLayer(); b.closeLayer(); }
    }

    private Map<String,List<ContextConnection>> group(ContextConnection... members) {
        return Collections.singletonMap("own", Arrays.asList(members));
    }

    private ContextConnection connection(String name) throws Exception {
        return ConnectionManager.qualifyConnect(directory.resolve("X"), directory.resolve(name));
    }
    private void create(String name, String source) throws Exception {
        User user = new User(); user.setDatabaseDir(directory + File.separator);
        DB data = new DB(); data.init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
        try { assertTrue(mind.compile(source, null, false)); }
        finally { user.setCurrentMind(mind.closeStorage()); }
    }
}
