package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.internal.IContextFederation;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        CanonicalCommandProcessor.Result switched =
                processor.execute(
                        parser.parse(
                                "ctx switch "
                                        + a.contextId
                                        + " "
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
                                "ctx disconnect "
                                        + a.contextId),
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
