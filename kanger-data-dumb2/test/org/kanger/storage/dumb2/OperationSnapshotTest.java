package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Version;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.Step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3.1 qualification for durable directed connections and immutable operation
 * revision selection.
 */
public class OperationSnapshotTest {

    @TempDir
    Path root;

    @Test
    void connectionPublicationKeepsPinnedTargetAndHistoricalSourceRevisions()
            throws Exception {
        Path xLocation = root.resolve("X");
        Path aLocation = root.resolve("A");

        ContextStore x = ContextStore.create(xLocation);
        ContextStore a = ContextStore.create(aLocation);
        try {
            x.getBase("index").add(
                    step(0L, 11, Long.valueOf(100L), null));
            assertEquals(1L, x.flush());

            IBase aBase = a.getBase("index");
            aBase.add(step(0L, 21, Long.valueOf(10L), null));
            assertEquals(1L, a.flush());

            RevisionRef xR1 =
                    new RevisionRef(x.getContextId(), 1L);
            RevisionRef aR1 =
                    new RevisionRef(a.getContextId(), 1L);
            ConnectionVector desired = new ConnectionVector(
                    Arrays.asList(new ContextConnection(
                            aLocation,
                            aR1,
                            new CompatibilityCertificate(
                                    xR1,
                                    aR1,
                                    Version.CORE_VERSION_S))));

            assertEquals(
                    2L,
                    x.publishTopology(
                            desired,
                            "Pin A@1"));

            RevisionRef xR2 =
                    new RevisionRef(x.getContextId(), 2L);
            ConnectionVector publishedR2 =
                    ConnectionStore.read(
                            xLocation,
                            xR2);

            OperationSnapshot operation =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(
                        xR2,
                        operation.getSourceRef());
                assertEquals(
                        publishedR2,
                        operation.getConnections());
                ContextSnapshot target =
                        operation.getTarget(a.getContextId());
                assertEquals(1L, target.getRevision());
                assertEquals(Long.valueOf(10L),
                        target.getBase("index").get(0L).getData());
                assertNull(target.getBase("index").get(1L));

                Step previous = new Step();
                previous.setId(0L);
                previous.setHash(21);
                previous.setData(Long.valueOf(10L));
                aBase.add(step(
                        1L, 22, Long.valueOf(20L), previous));
                assertEquals(2L, a.flush());

                assertEquals(1L, target.getRevision(),
                        "running operation must remain pinned to A@R1");
                assertNull(target.getBase("index").get(1L),
                        "A@R2 must not leak into the running operation");
            } finally {
                operation.close();
            }

            OperationSnapshot stillPinned =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(1L,
                        stillPinned.getTarget(
                                a.getContextId()).getRevision());
            } finally {
                stillPinned.close();
            }

            RevisionRef aR2 =
                    new RevisionRef(a.getContextId(), 2L);
            ConnectionVector repinned = publishedR2.with(
                    new ContextConnection(
                            aLocation,
                            aR2,
                            new CompatibilityCertificate(
                                    xR2,
                                    aR2,
                                    Version.CORE_VERSION_S)));
            assertEquals(
                    3L,
                    x.publishTopology(
                            repinned,
                            "Repin A@2"));

            OperationSnapshot next =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(
                        3L,
                        next.getSourceRef().getRevision());
                ContextSnapshot target =
                        next.getTarget(a.getContextId());
                assertEquals(2L, target.getRevision());
                assertEquals(Long.valueOf(20L),
                        target.getBase("index").get(1L).getData());
            } finally {
                next.close();
            }

            UUID xId = x.getContextId();
            x.close();
            x = ContextStore.open(xLocation);
            assertEquals(xId, x.getContextId());
            assertEquals(3L, x.getRevision());

            ConnectionVector historicalR2 =
                    ConnectionStore.read(
                            xLocation,
                            xR2);
            assertEquals(
                    aR1,
                    historicalR2.find(
                            a.getContextId())
                            .getTarget());

            assertEquals(
                    4L,
                    x.publishTopology(
                            ConnectionVector.empty(),
                            "Disconnect A"));
            assertTrue(Files.exists(
                    ConnectionStore.path(xLocation)));

            OperationSnapshot disconnected =
                    OperationSnapshot.open(xLocation);
            try {
                assertTrue(
                        disconnected.getConnections().isEmpty());
                assertNull(
                        disconnected.getTarget(
                                a.getContextId()));
            } finally {
                disconnected.close();
            }

            assertEquals(
                    aR2,
                    ConnectionStore.read(
                            xLocation,
                            new RevisionRef(xId, 3L))
                            .find(a.getContextId())
                            .getTarget(),
                    "KEEP_ALL must retain the exact dependency vector of X@R3");
        } finally {
            a.close();
            x.close();
        }
    }

    @Test
    void revisionAwareConnectionStoreKeepsEveryPublishedVector()
            throws Exception {
        Path xLocation = root.resolve("XT");
        Path aLocation = root.resolve("AT");

        ContextStore x = ContextStore.create(xLocation);
        ContextStore a = ContextStore.create(aLocation);
        try {
            x.getBase("index").add(
                    step(0L, 11, Long.valueOf(100L), null));
            assertEquals(1L, x.flush());

            IBase aBase = a.getBase("index");
            aBase.add(
                    step(0L, 21, Long.valueOf(10L), null));
            assertEquals(1L, a.flush());

            RevisionRef xR1 =
                    new RevisionRef(x.getContextId(), 1L);
            RevisionRef aR1 =
                    new RevisionRef(a.getContextId(), 1L);
            ConnectionVector first =
                    new ConnectionVector(
                            Arrays.asList(
                                    new ContextConnection(
                                            aLocation,
                                            aR1,
                                            new CompatibilityCertificate(
                                                    xR1,
                                                    aR1,
                                                    Version.CORE_VERSION_S))));

            assertEquals(
                    2L,
                    x.publishTopology(
                            first,
                            "First exact pin"));

            Step previous = step(
                    0L, 21, Long.valueOf(10L), null);
            aBase.add(step(
                    1L, 22, Long.valueOf(20L), previous));
            assertEquals(2L, a.flush());

            RevisionRef xR2 =
                    new RevisionRef(x.getContextId(), 2L);
            RevisionRef aR2 =
                    new RevisionRef(a.getContextId(), 2L);
            ConnectionVector second =
                    ConnectionStore.read(
                            xLocation,
                            xR2)
                            .with(new ContextConnection(
                                    aLocation,
                                    aR2,
                                    new CompatibilityCertificate(
                                            xR2,
                                            aR2,
                                            Version.CORE_VERSION_S)));

            assertEquals(
                    3L,
                    x.publishTopology(
                            second,
                            "Second exact pin"));

            RevisionRef xR3 =
                    new RevisionRef(x.getContextId(), 3L);
            assertTrue(
                    ConnectionStore.read(
                            xLocation,
                            xR1)
                            .isEmpty());
            assertEquals(
                    aR1,
                    ConnectionStore.read(
                            xLocation,
                            xR2)
                            .find(a.getContextId())
                            .getTarget());
            assertEquals(
                    aR2,
                    ConnectionStore.read(
                            xLocation,
                            xR3)
                            .find(a.getContextId())
                            .getTarget());

            ContextSnapshot old =
                    ContextSnapshot.open(
                            xLocation,
                            2L);
            try {
                assertEquals(
                        2L,
                        old.getRevision());
            } finally {
                old.close();
            }

            OperationSnapshot current =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(
                        xR3,
                        current.getSourceRef());
                assertEquals(
                        2L,
                        current.getTarget(
                                a.getContextId())
                                .getRevision());
            } finally {
                current.close();
            }
        } finally {
            a.close();
            x.close();
        }
    }

    private static Step step(long id,
                             int hash,
                             Object data,
                             Step next) {
        Step step = new Step();
        step.setId(id);
        step.setHash(hash);
        step.setData(data);
        step.setNext(next);
        return step;
    }
}
