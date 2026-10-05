package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.command.CommandParser;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.internal.IContextFederation;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3.10 operator-visible federation projection.
 */
public class ContextFederationOperatorTest {

    @TempDir
    Path root;

    @Test
    void exactPinRemainsVisibleUntilDeliberateSwitch()
            throws Exception {
        ContextFixture x =
                context("X", "!anchor(X);");
        ContextFixture a =
                context("A", "!male(Tom);");

        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("X");
        user.setCurrentMind(mind);

        IContextFederation federation = data;
        IContextFederation.Connection connected =
                federation.connectContext("A");

        assertEquals("A", connected.getLocator());
        assertEquals(
                a.contextId,
                connected.getTargetContextId());
        assertEquals(
                a.revision,
                connected.getPinnedRevision());
        assertEquals(
                a.revision,
                connected.getCurrentRevision());
        assertFalse(connected.hasNewerRevision());
        assertEquals(
                IContextFederation.PinPolicy.EXACT_REVISION,
                connected.getPinPolicy());
        assertEquals(
                IContextFederation.CompatibilityStatus.QUALIFIED,
                connected.getCompatibilityStatus());

        IContextFederation.Snapshot initial =
                federation.federationSnapshot();
        assertEquals(
                x.contextId,
                initial.getSourceContextId());
        assertEquals(
                x.revision,
                initial.getSourceRevision());
        assertEquals(
                1,
                initial.getConnections().size());

        advance("A", "!female(Jane);");

        IContextFederation.Snapshot advanced =
                federation.federationSnapshot();
        IContextFederation.Connection stillPinned =
                advanced.getConnections().get(0);
        assertEquals(
                a.revision,
                stillPinned.getPinnedRevision(),
                "target CURRENT advance must not imply FOLLOW_HEAD");
        assertEquals(
                a.revision + 1L,
                stillPinned.getCurrentRevision());
        assertTrue(stillPinned.hasNewerRevision());

        IContextFederation.VersionHistory versions =
                federation.versionHistory("A");
        assertEquals(
                a.revision,
                versions.getPinnedRevision());
        assertEquals(
                a.revision + 1L,
                versions.getCurrentRevision());
        assertEquals(
                a.revision + 1L,
                versions.getRevisions().get(0)
                        .getRevision());
        assertEquals(
                a.revision,
                versions.getRevisions().get(1)
                        .getRevision());

        IContextFederation.VersionHistory sourceVersions =
                federation.versionHistory(null);
        assertFalse(
                sourceVersions.hasPinnedRevision());
        assertEquals(
                x.revision,
                sourceVersions.getCurrentRevision());

        IContextFederation.Connection switched =
                federation.switchContextRevision(
                        a.contextId,
                        a.revision + 1L);
        assertEquals(
                a.revision + 1L,
                switched.getPinnedRevision());
        assertFalse(switched.hasNewerRevision());

        IContextFederation.Snapshot afterSwitch =
                federation.federationSnapshot();
        assertEquals(
                x.revision,
                afterSwitch.getSourceRevision(),
                "target repin must not advance source revision");
        assertEquals(
                a.revision + 1L,
                afterSwitch.getConnections().get(0)
                        .getPinnedRevision());

        federation.disconnectContext(a.contextId);
        assertTrue(
                federation.federationSnapshot()
                        .getConnections().isEmpty());

        user.setCurrentMind(
                mind.closeStorage());
    }

    @Test
    void federatedQueryProjectsTruthAndExactProvenance()
            throws Exception {
        ContextFixture x =
                context("QX", "!anchor(X);");
        ContextFixture a =
                context("QA", "!male(Tom);");
        ContextFixture b =
                context("QB", "!~male(Tom);");

        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("QX");
        user.setCurrentMind(mind);

        IContextFederation federation = data;
        federation.connectContext("QA");

        IContextFederation.QueryResult positive =
                federation.executeFederatedQuery(
                        "?male(Tom);");
        assertTrue(positive.isResolved());
        assertEquals(1, positive.getObservations().size());
        IContextFederation.FrontierObservation trueObservation =
                positive.getObservations().get(0);
        assertEquals(
                IContextFederation.FrontierTruth.TRUE,
                trueObservation.getTruth());
        assertEquals(
                a.contextId,
                trueObservation.getTrueSources()
                        .get(0).getContextId());
        assertEquals(
                a.revision,
                trueObservation.getTrueSources()
                        .get(0).getRevision());

        federation.connectContext("QB");

        IContextFederation.QueryResult conflict =
                federation.executeFederatedQuery(
                        "?male(Tom);");
        assertFalse(conflict.isResolved());
        assertEquals(1, conflict.getObservations().size());
        IContextFederation.FrontierObservation conflictObservation =
                conflict.getObservations().get(0);
        assertEquals(
                IContextFederation.FrontierTruth.CONFLICT,
                conflictObservation.getTruth());
        assertEquals(
                a.contextId,
                conflictObservation.getTrueSources()
                        .get(0).getContextId());
        assertEquals(
                b.contextId,
                conflictObservation.getFalseSources()
                        .get(0).getContextId());
        assertTrue(conflict.getProvisionalHypotheses().isEmpty());

        /*
         * A pre-existing foreign truth conflict must not freeze an unrelated
         * exact-revision move. This is the operator-level regression for the
         * manual-soak path A@1/B@1 -> A@2/B@1.
         */
        advance("QA", "!female(Jane);");

        IContextFederation.Connection switched =
                federation.switchContextRevision(
                        a.contextId,
                        a.revision + 1L);
        assertEquals(
                a.revision + 1L,
                switched.getPinnedRevision());

        IContextFederation.QueryResult conflictAfterSwitch =
                federation.executeFederatedQuery(
                        "?male(Tom);");
        assertFalse(conflictAfterSwitch.isResolved());
        IContextFederation.FrontierObservation switchedObservation =
                conflictAfterSwitch.getObservations().get(0);
        assertEquals(
                IContextFederation.FrontierTruth.CONFLICT,
                switchedObservation.getTruth());
        assertEquals(
                a.revision + 1L,
                switchedObservation.getTrueSources()
                        .get(0).getRevision());
        assertEquals(
                b.revision,
                switchedObservation.getFalseSources()
                        .get(0).getRevision());

        IContextFederation.Snapshot snapshot =
                federation.federationSnapshot();
        assertEquals(
                x.contextId,
                snapshot.getSourceContextId());
        assertEquals(
                x.revision,
                snapshot.getSourceRevision(),
                "federated query must not publish foreign evidence");

        user.setCurrentMind(
                mind.closeStorage());
    }

    @Test
    void isolatedCtxQueryUsesOnlyRequestedExactContext()
            throws Exception {
        ContextFixture a =
                context("IA", "!age(Tom,42);");
        context("IB", "!secret(Tom);");

        /*
         * Give A a durable exact dependency on B. This publishes A@R+1.
         * An isolated query in A must still remain local-only and therefore
         * must not traverse A's own ConnectionVector.
         */
        ConnectionManager.connect(
                root.resolve("IA"),
                root.resolve("IB"));
        long pinnedARevision =
                a.revision + 1L;

        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("IX");
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query(
                        "!local(OnlyX);",
                        null,
                        false)));

        IContextFederation federation = data;
        federation.connectContext("IA");

        IContextFederation.QueryResult foreign =
                federation.executeIsolatedQuery(
                        mind,
                        "IA",
                        "?$x age(Tom,x);");
        assertEquals(
                IContextFederation.FrontierTruth.TRUE,
                foreign.getResultTruth());
        assertEquals(1, foreign.getValues().size());
        assertEquals(
                "42.0",
                foreign.getValues().get(0)
                        .getBindings().get("x"));

        IContextFederation.QueryResult noRecursiveFederation =
                federation.executeIsolatedQuery(
                        mind,
                        "IA",
                        "?secret(Tom);");
        assertEquals(
                IContextFederation.FrontierTruth.UNKNOWN,
                noRecursiveFederation.getResultTruth());

        /*
         * Advance A after X pinned it. Diagnostic addressing must still use
         * the exact pinned revision until deliberate ctx switch.
         */
        advance("IA", "!female(Jane);");
        IContextFederation.QueryResult pinned =
                federation.executeIsolatedQuery(
                        mind,
                        "IA",
                        "?female(Jane);");
        assertEquals(
                IContextFederation.FrontierTruth.UNKNOWN,
                pinned.getResultTruth());
        assertEquals(
                pinnedARevision,
                federation.federationSnapshot()
                        .getConnections().get(0)
                        .getPinnedRevision());

        IContextFederation.QueryResult local =
                federation.executeIsolatedQuery(
                        mind,
                        "IX",
                        "?local(OnlyX);");
        assertEquals(
                IContextFederation.FrontierTruth.TRUE,
                local.getResultTruth());

        IContextFederation.QueryResult localDoesNotSeeA =
                federation.executeIsolatedQuery(
                        mind,
                        "IX",
                        "?age(Tom,42);");
        assertEquals(
                IContextFederation.FrontierTruth.UNKNOWN,
                localDoesNotSeeA.getResultTruth());

        final Mind isolatedMind = mind;
        assertThrows(
                CommandErrorException.class,
                () -> federation.executeIsolatedQuery(
                        isolatedMind,
                        "NOT_CONNECTED",
                        "?male(Tom);"));

        user.setCurrentMind(
                mind.closeStorage());
    }

    @Test
    void workingTopologyDoesNotRewritePublishedRevision()
            throws Exception {
        ContextFixture x =
                context("WX", "!anchor(X);");
        ContextFixture a =
                context("WA", "!male(Tom);");

        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("WX");
        user.setCurrentMind(mind);

        IContextFederation federation = data;
        federation.connectContext("WA");
        assertEquals(
                1,
                federation.federationSnapshot()
                        .getConnections().size());

        /*
         * The live session sees its candidate topology, but an independent
         * exact-revision operation still sees the immutable published vector.
         */
        OperationSnapshot published =
                OperationSnapshot.open(root.resolve("WX"));
        try {
            assertEquals(
                    new RevisionRef(x.contextId, x.revision),
                    published.getSourceRef());
            assertTrue(
                    published.getConnections().isEmpty(),
                    "working connect must not rewrite published X@R");
        } finally {
            published.close();
        }

        advance("WA", "!female(Jane);");
        federation.switchContextRevision(
                a.contextId,
                a.revision + 1L);
        assertEquals(
                a.revision + 1L,
                federation.federationSnapshot()
                        .getConnections().get(0)
                        .getPinnedRevision());

        federation.disconnectContext(a.contextId);
        assertTrue(
                federation.federationSnapshot()
                        .getConnections().isEmpty());

        user.setCurrentMind(
                mind.closeStorage());

        /*
         * Closing the session discards the working candidate; reopen restores
         * the vector bound to the published revision.
         */
        User reopenedUser = new User();
        reopenedUser.setDatabaseDir(
                root.toString() + File.separator);
        DB reopenedData = new DB();
        reopenedData.init(reopenedUser);
        Mind reopenedMind = new Mind(reopenedUser);
        reopenedUser.setCurrentMind(reopenedMind);
        reopenedMind = (Mind) reopenedMind.useStorage("WX");
        reopenedUser.setCurrentMind(reopenedMind);
        assertTrue(
                ((IContextFederation) reopenedData)
                        .federationSnapshot()
                        .getConnections().isEmpty());
        reopenedUser.setCurrentMind(
                reopenedMind.closeStorage());
    }

    @Test
    void canonicalCtxSurfaceDrivesQualifiedDumb2Federation()
            throws Exception {
        ContextFixture x =
                context("CX", "!anchor(X);");
        ContextFixture a =
                context("CA", "!male(Tom);");

        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("CX");
        user.setCurrentMind(mind);

        CanonicalCommandProcessor processor =
                new CanonicalCommandProcessor();
        CommandParser parser = new CommandParser();

        CanonicalCommandProcessor.Result initial =
                processor.execute(
                        parser.parse("ctx"), user);
        assertEquals(
                "CX",
                initial.getFederationSnapshot()
                        .getSourceLocator());
        assertEquals(
                x.contextId,
                initial.getFederationSnapshot()
                        .getSourceContextId());
        assertTrue(
                initial.getFederationSnapshot()
                        .getConnections().isEmpty());

        CanonicalCommandProcessor.Result connected =
                processor.execute(
                        parser.parse("ctx connect CA"), user);
        assertEquals(
                a.contextId,
                connected.getFederationSnapshot()
                        .getConnections().get(0)
                        .getTargetContextId());

        CanonicalCommandProcessor.Result isolated =
                processor.execute(
                        parser.parse("ctx CA ?male(Tom);"),
                        user);
        assertEquals(
                IContextFederation.FrontierTruth.TRUE,
                isolated.getFederationQueryResult()
                        .getResultTruth());

        CanonicalCommandProcessor.Result query =
                processor.execute(
                        parser.parse("ctx query ?male(Tom);"),
                        user);
        assertTrue(
                query.getFederationQueryResult()
                        .isResolved());
        assertEquals(
                IContextFederation.FrontierTruth.TRUE,
                query.getFederationQueryResult()
                        .getObservations().get(0)
                        .getTruth());

        advance("CA", "!female(Jane);");

        CanonicalCommandProcessor.Result newer =
                processor.execute(
                        parser.parse("ctx"), user);
        assertTrue(
                newer.getFederationSnapshot()
                        .getConnections().get(0)
                        .hasNewerRevision());

        CanonicalCommandProcessor.Result version =
                processor.execute(
                        parser.parse("ctx version CA"),
                        user);
        assertEquals(
                a.revision,
                version.getContextVersionHistory()
                        .getPinnedRevision());
        assertEquals(
                a.revision + 1L,
                version.getContextVersionHistory()
                        .getCurrentRevision());

        CanonicalCommandProcessor.Result switched =
                processor.execute(
                        parser.parse(
                                "ctx switch CA "
                                        + (a.revision + 1L)),
                        user);
        assertEquals(
                a.revision + 1L,
                switched.getFederationSnapshot()
                        .getConnections().get(0)
                        .getPinnedRevision());

        CanonicalCommandProcessor.Result disconnected =
                processor.execute(
                        parser.parse(
                                "ctx disconnect CA"),
                        user);
        assertTrue(
                disconnected.getFederationSnapshot()
                        .getConnections().isEmpty());

        user.setCurrentMind(
                mind.closeStorage());
    }

    private void advance(
            String name,
            String assertion) throws Exception {
        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query(assertion, null, false)));
        user.setCurrentMind(
                mind.closeStorage());
    }

    private ContextFixture context(
            String name,
            String assertion) throws Exception {
        User user = new User();
        user.setDatabaseDir(
                root.toString() + File.separator);
        DB data = new DB();
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name);
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(
                mind.query(assertion, null, false)));

        long revision = data.getRevision();
        java.util.UUID contextId =
                data.getContextId();
        assertNotNull(contextId);
        user.setCurrentMind(
                mind.closeStorage());
        return new ContextFixture(
                contextId, revision);
    }

    private static final class ContextFixture {
        final java.util.UUID contextId;
        final long revision;

        ContextFixture(
                java.util.UUID contextId,
                long revision) {
            this.contextId = contextId;
            this.revision = revision;
        }
    }
}
