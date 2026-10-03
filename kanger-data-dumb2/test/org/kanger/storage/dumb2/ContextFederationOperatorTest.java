package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
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
