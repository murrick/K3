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
    void connectionPersistsAndOperationKeepsPinnedTargetRevision()
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
            CompatibilityCertificate certificateR1 =
                    new CompatibilityCertificate(
                            xR1, aR1, Version.CORE_VERSION_S);
            ConnectionVector vector = new ConnectionVector(
                    Arrays.asList(new ContextConnection(
                            aLocation, aR1, certificateR1)));
            ConnectionStore.write(
                    xLocation, x.getContextId(), vector);

            OperationSnapshot operation =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(
                        new RevisionRef(x.getContextId(), 1L),
                        operation.getSourceRef());
                assertEquals(vector, operation.getConnections());
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

            /*
             * Connection metadata is still pinned to R1 even though A CURRENT
             * is now R2. A new operation therefore also opens exact A@R1.
             */
            OperationSnapshot stillPinned =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(1L,
                        stillPinned.getTarget(
                                a.getContextId()).getRevision());
            } finally {
                stillPinned.close();
            }

            /*
             * A deliberate repin is visible only to the next operation.
             */
            RevisionRef aR2 =
                    new RevisionRef(a.getContextId(), 2L);
            ConnectionVector repinned = vector.with(
                    new ContextConnection(
                            aLocation,
                            aR2,
                            new CompatibilityCertificate(
                                    xR1,
                                    aR2,
                                    Version.CORE_VERSION_S)));
            ConnectionStore.write(
                    xLocation, x.getContextId(), repinned);

            OperationSnapshot next =
                    OperationSnapshot.open(xLocation);
            try {
                ContextSnapshot target =
                        next.getTarget(a.getContextId());
                assertEquals(2L, target.getRevision());
                assertEquals(Long.valueOf(20L),
                        target.getBase("index").get(1L).getData());
            } finally {
                next.close();
            }

            /*
             * Closing/reopening X is not disconnect. The operational sidecar
             * remains bound to the same stable ContextId.
             */
            UUID xId = x.getContextId();
            x.close();
            x = ContextStore.open(xLocation);
            assertEquals(xId, x.getContextId());
            assertEquals(repinned,
                    ConnectionStore.read(
                            xLocation, x.getContextId()));

            ConnectionStore.write(
                    xLocation,
                    x.getContextId(),
                    repinned.without(a.getContextId()));
            assertFalse(Files.exists(
                    ConnectionStore.path(xLocation)));

            OperationSnapshot disconnected =
                    OperationSnapshot.open(xLocation);
            try {
                assertTrue(
                        disconnected.getConnections().isEmpty());
                assertNull(disconnected.getTarget(a.getContextId()));
            } finally {
                disconnected.close();
            }
        } finally {
            a.close();
            x.close();
        }
    }

    @Test
    void revisionAwareConnectionSidecarSwitchesOnlyWithSourceMarker()
            throws Exception {
        Path xLocation = root.resolve("XT");
        Path aLocation = root.resolve("AT");

        ContextStore x = ContextStore.create(xLocation);
        ContextStore a = ContextStore.create(aLocation);
        try {
            IBase xBase = x.getBase("index");
            xBase.add(step(0L, 11, Long.valueOf(100L), null));
            assertEquals(1L, x.flush());

            a.getBase("index").add(
                    step(0L, 21, Long.valueOf(10L), null));
            assertEquals(1L, a.flush());

            RevisionRef xR1 =
                    new RevisionRef(x.getContextId(), 1L);
            RevisionRef xR2 =
                    new RevisionRef(x.getContextId(), 2L);
            RevisionRef aR1 =
                    new RevisionRef(a.getContextId(), 1L);

            ConnectionVector r1 = new ConnectionVector(
                    Arrays.asList(new ContextConnection(
                            aLocation,
                            aR1,
                            new CompatibilityCertificate(
                                    xR1,
                                    aR1,
                                    Version.CORE_VERSION_S))));
            ConnectionVector r2 = new ConnectionVector(
                    Arrays.asList(new ContextConnection(
                            aLocation,
                            aR1,
                            new CompatibilityCertificate(
                                    xR2,
                                    aR1,
                                    Version.CORE_VERSION_S))));

            ConnectionStore.writeTransition(
                    xLocation,
                    xR1,
                    r1,
                    xR2,
                    r2);

            OperationSnapshot before =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(xR1, before.getSourceRef());
                assertEquals(r1, before.getConnections(),
                        "candidate connection vector must not leak before R2 publication");
            } finally {
                before.close();
            }

            Step previous = step(
                    0L, 11, Long.valueOf(100L), null);
            xBase.add(step(
                    1L, 12, Long.valueOf(200L), previous));
            assertEquals(2L, x.flush());

            OperationSnapshot after =
                    OperationSnapshot.open(xLocation);
            try {
                assertEquals(xR2, after.getSourceRef());
                assertEquals(r2, after.getConnections(),
                        "R2 publication must select the prequalified R2 vector");
            } finally {
                after.close();
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
