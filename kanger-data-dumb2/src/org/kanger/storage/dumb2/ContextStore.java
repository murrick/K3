/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * DUMB 2.0 Context lifecycle and storage-wide publication owner.
 *
 * <p>A Context is identified by one stable ContextId and one currently
 * published durable revision. Schema bases are acquired by stable string
 * descriptor and remain Context-local physical namespaces.</p>
 *
 * <p>Mutations are staged in {@link ContextBase}. A storage-wide flush writes
 * a complete immutable next revision generation first and advances the
 * revision marker only afterwards. Therefore a visible revision can never
 * point at an older or partially written physical generation.</p>
 *
 * <p>The supplied {@code location} is a locator, not Context identity.
 * Metadata sidecars are derived as {@code <location>.context} and
 * {@code <location>.revision}. Physical generations live under
 * {@code <location>.dumb2}. Moving or renaming the complete Context preserves
 * its persisted ContextId and revision.</p>
 */
final class ContextStore implements AutoCloseable, PersistentTypeResolver {

    static final String CONTEXT_SUFFIX = ".context";
    static final String REVISION_SUFFIX = ".revision";
    static final String STATE_SUFFIX = ".dumb2";

    private final Path location;
    private final UUID contextId;
    private final ContextManifestStore.Origin origin;
    private final ContextLock contextLock;
    private final TypeRegistry typeRegistry;
    private final Map<String, ContextBase> bases =
            new LinkedHashMap<String, ContextBase>();

    private long revision;
    private long revisionManifestBaseline;
    private int publishedTypeCount;
    private boolean closed;

    private ContextStore(Path location,
                         UUID contextId,
                         long revision,
                         ContextManifestStore.Origin origin,
                         long revisionManifestBaseline,
                         ContextLock contextLock,
                         TypeRegistry typeRegistry) {
        this.location = location;
        this.contextId = contextId;
        this.revision = revision;
        this.origin = origin;
        this.revisionManifestBaseline =
                revisionManifestBaseline;
        this.contextLock = contextLock;
        this.typeRegistry = typeRegistry;
        this.publishedTypeCount = typeRegistry.size();
    }

    /**
     * Creates a new empty Context and acquires its mutable lifecycle lock.
     *
     * <p>If revision creation fails after a fresh identity has been created,
     * that fresh identity is removed again. Existing metadata is never deleted
     * or repaired implicitly.</p>
     */
    static ContextStore create(Path location) throws IOException, StorageLifecycleException {
        Path normalized = requireLocation(location);
        Path contextPath = contextPath(normalized);
        Path revisionPath = revisionPath(normalized);

        ContextManifestStore.Manifest manifest = ContextManifestStore.create(contextPath);
        UUID contextId = manifest.getContextId();
        long revision;
        try {
            revision = RevisionStore.create(revisionPath);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(contextPath);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

        ContextLock lock = null;
        try {
            lock = acquireLock(normalized);
            return new ContextStore(
                    normalized,
                    contextId,
                    revision,
                    manifest.getOrigin(),
                    manifest.getRevisionManifestBaseline(),
                    lock,
                    manifest.getTypeRegistry());
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            closeQuietly(lock, failure);
            throw failure;
        }
    }

    /**
     * Reopens one complete Context metadata pair and acquires its lifecycle
     * lock. Revision zero denotes the initial empty Context and needs no
     * physical generation directory.
     */
    static ContextStore open(Path location)
            throws IOException, StorageLifecycleException {
        Path normalized = requireLocation(location);
        Path contextPath = contextPath(normalized);
        Path revisionPath = revisionPath(normalized);

        boolean hasContext = Files.exists(contextPath);
        boolean hasRevision = Files.exists(revisionPath);
        if (!hasContext && !hasRevision) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context was not found at " + normalized);
        }
        if (hasContext != hasRevision) {
            throw incomplete(normalized);
        }

        ContextLock lock = acquireLock(normalized);
        try {
            ContextManifestStore.Manifest manifest = ContextManifestStore.read(contextPath);
            UUID contextId = manifest.getContextId();
            long revision = RevisionStore.read(revisionPath);
            if (revision > RevisionStore.INITIAL_REVISION
                    && !Files.isDirectory(generationPath(normalized, revision))) {
                throw corruption("DUMB2 Context revision " + revision
                        + " has no physical generation at " + normalized);
            }
            ContextStore store = new ContextStore(
                    normalized,
                    contextId,
                    revision,
                    manifest.getOrigin(),
                    manifest.getRevisionManifestBaseline(),
                    lock,
                    manifest.getTypeRegistry());
            if (revision > RevisionStore.INITIAL_REVISION) {
                store.validatePublishedGeneration();
            }
            return store;
        } catch (NoSuchFileException failure) {
            StorageLifecycleException incomplete = incomplete(normalized);
            incomplete.addSuppressed(failure);
            closeQuietly(lock, incomplete);
            throw incomplete;
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            closeQuietly(lock, failure);
            throw failure;
        }
    }

    /**
     * Forks exactly one immutable source snapshot into a new independent
     * mutable Context.
     *
     * <p>The target receives a new ContextId and records provenance to the
     * exact source Context/revision. Persistent schema images are copied
     * byte-for-byte, so local operational ids are preserved. A non-empty
     * source starts the new independent revision lineage at R1; an empty R0
     * snapshot remains R0.</p>
     */
    static ContextStore fork(ContextSnapshot source, Path target) throws Exception {
        return fork(source, target, true);
    }

    static ContextStore forkForPublication(ContextSnapshot source, Path target) throws Exception {
        return fork(source, target, false);
    }

    private static ContextStore fork(ContextSnapshot source, Path target, boolean inheritDependencies)
            throws Exception {
        Objects.requireNonNull(source, "source");
        if (source.isClosed()) {
            throw new IllegalStateException("DUMB2 source snapshot is closed");
        }

        Path normalized = requireLocation(target);
        Path targetContext = contextPath(normalized);
        Path targetRevision = revisionPath(normalized);
        Path targetState = stateRoot(normalized);
        if (Files.exists(targetContext)
                || Files.exists(targetRevision)
                || Files.exists(targetState)
                || Files.exists(ConnectionStore.path(normalized))) {
            throw new java.nio.file.FileAlreadyExistsException(
                    "DUMB2 fork target already exists: " + normalized);
        }

        RevisionRef sourceRef =
                new RevisionRef(
                        source.getContextId(),
                        source.getRevision());
        ConnectionVector sourceDependencies = inheritDependencies
                ? ConnectionStore.read(source.getLocation(), sourceRef) : ConnectionVector.empty();

        boolean manifestCreated = false;
        try {
            ContextManifestStore.Manifest targetManifest =
                    ContextManifestStore.createFork(
                            targetContext,
                            source.getContextId(),
                            source.getRevision(),
                            source.snapshotTypeRegistry());
            manifestCreated = true;

            long forkRevision =
                    source.getRevision() == RevisionStore.INITIAL_REVISION
                            ? RevisionStore.INITIAL_REVISION
                            : 1L;

            if (forkRevision > RevisionStore.INITIAL_REVISION) {
                Files.createDirectories(targetState);
                Path generation =
                        generationPath(normalized, forkRevision);
                Path staging = targetState.resolve(
                        ".fork-" + forkRevision + "-"
                                + UUID.randomUUID().toString());
                boolean installed = false;
                try {
                    copyDirectory(
                            source.getGeneration(),
                            staging);

                    RevisionManifestStore.seal(
                            staging,
                            targetManifest.getContextId(),
                            forkRevision,
                            RevisionStore.INITIAL_REVISION,
                            sourceDependencies,
                            "");

                    ConnectionVector forkDependencies =
                            ConnectionVector.empty();
                    if (!sourceDependencies.isEmpty()) {
                        ContextCandidate candidate =
                                ContextCandidate.of(
                                        normalized,
                                        staging,
                                        targetManifest.getContextId(),
                                        forkRevision,
                                        targetManifest.getOrigin(),
                                        targetManifest.getTypeRegistry(),
                                        sourceDependencies);
                        WriteCandidateQualification.Result qualification =
                                WriteCandidateQualification.qualify(
                                        candidate,
                                        sourceDependencies);
                        RevisionRef expected =
                                new RevisionRef(
                                        targetManifest.getContextId(),
                                        forkRevision);
                        if (!expected.equals(
                                qualification.getCandidate())) {
                            throw new IllegalStateException(
                                    "Qualified fork candidate identity mismatch: expected "
                                            + expected
                                            + " found "
                                            + qualification.getCandidate());
                        }
                        forkDependencies =
                                qualification.getConnections();
                        RevisionManifestStore
                                .validateDependencyVector(
                                        staging,
                                        forkDependencies);
                    }

                    Files.move(
                            staging,
                            generation,
                            StandardCopyOption.ATOMIC_MOVE);
                    installed = true;

                    if (!forkDependencies.isEmpty()) {
                        ConnectionStore.stageInitialRevision(
                                normalized,
                                new RevisionRef(
                                        targetManifest.getContextId(),
                                        forkRevision),
                                forkDependencies);
                    }
                } catch (java.nio.file.AtomicMoveNotSupportedException failure) {
                    throw new IOException(
                            "DUMB2 requires atomic same-filesystem fork publication: "
                                    + generation,
                            failure);
                } finally {
                    if (!installed || Files.exists(staging)) {
                        deleteRecursively(staging);
                    }
                }
            }

            RevisionStore.create(
                    targetRevision,
                    forkRevision);
            return ContextStore.open(normalized);
        } catch (Exception | Error failure) {
            if (manifestCreated) {
                try {
                    deleteRecursively(targetState);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    Files.deleteIfExists(
                            ConnectionStore.path(normalized));
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    Files.deleteIfExists(targetRevision);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    Files.deleteIfExists(targetContext);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * Registers a persistent layout in this Context and publishes the manifest
     * before returning its Context-local typeCode.
     *
     * <p>The in-memory registry is append-only. If manifest publication fails,
     * the call fails and no record may use the returned definition; retrying
     * the same registration republishes the same immutable code.</p>
     */
    synchronized TypeDefinition registerType(String typeName, Descriptor descriptor)
            throws Exception {
        requireOpen();
        TypeDefinition definition = typeRegistry.register(typeName, descriptor);
        if (typeRegistry.size() != publishedTypeCount) {
            ContextManifestStore.publish(contextPath(location), contextId, typeRegistry);
            publishedTypeCount = typeRegistry.size();
        }
        return definition;
    }

    @Override
    public synchronized TypeDefinition resolveType(int typeCode) {
        requireOpen();
        return typeRegistry.resolve(typeCode);
    }

    synchronized TypeRegistry snapshotTypeRegistry() {
        requireOpen();
        TypeRegistry snapshot = new TypeRegistry();
        for (TypeDefinition definition : typeRegistry.definitions()) {
            snapshot.install(definition.getTypeCode(),
                    definition.getTypeName(),
                    definition.getDescriptor());
        }
        return snapshot;
    }

    synchronized IBase getBase(String schema) throws Exception {
        requireOpen();
        String descriptor = requireSchema(schema);
        ContextBase base = bases.get(descriptor);
        if (base == null) {
            base = new ContextBase(this, descriptor);
            bases.put(descriptor, base);
        }
        return base;
    }

    /**
     * Publishes all dirty schema images as one new immutable Context revision.
     *
     * <p>The previous generation is copied forward so unopened schema
     * namespaces are preserved. Dirty acquired bases replace their snapshot in
     * the staging generation. Only after the complete directory has been
     * atomically installed does the revision marker advance.</p>
     *
     * @return currently published revision; unchanged for a clean Context
     */
    private ConnectionVector qualifiedWorkingConnections;

    synchronized ConnectionVector getQualifiedWorkingConnections() {
        return qualifiedWorkingConnections;
    }

    synchronized long flush() throws Exception {
        return flush(null);
    }

    synchronized long flush(ConnectionVector working) throws Exception {
        return flush(working, "Updated local Context");
    }

    synchronized long flush(ConnectionVector working, String description) throws Exception {
        requireOpen();
        qualifiedWorkingConnections = null;

        boolean dirty = false;
        for (ContextBase base : bases.values()) {
            if (base.isDirty()) {
                base.validateWorkingState();
                dirty = true;
            }
        }
        if (!dirty) {
            return revision;
        }
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("DUMB2 revision space exhausted");
        }

        long persisted = RevisionStore.read(revisionPath(location));
        if (persisted != revision) {
            throw new IllegalStateException(
                    "DUMB2 Context revision changed while open: expected="
                            + revision + " actual=" + persisted);
        }

        /*
         * R is the only source allowed to seed R+1. Revalidate its complete
         * physical image before copying anything so an out-of-band corruption
         * cannot be silently promoted into a newly published generation.
         */
        if (revision > RevisionStore.INITIAL_REVISION) {
            validatePublishedGeneration();
        }

        long next = revision + 1L;
        ensureRevisionManifestPolicy(next);
        ConnectionVector currentConnections =
                publishedConnections();
        Path root = stateRoot(location);
        Path previous = generationPath(location, revision);
        Path target = generationPath(location, next);
        Path staging = root.resolve(
                ".staging-" + next + "-" + UUID.randomUUID().toString());

        Files.createDirectories(root);
        deleteRecursively(staging);

        boolean generationInstalled = false;
        try {
            if (revision > RevisionStore.INITIAL_REVISION) {
                if (!Files.isDirectory(previous)) {
                    throw corruption("DUMB2 Context revision " + revision
                            + " lost its physical generation at " + location);
                }
                copyDirectory(previous, staging);
            } else {
                Files.createDirectories(staging);
            }

            for (ContextBase base : bases.values()) {
                if (base.isDirty()) {
                    base.writeSnapshot(staging);
                }
            }

            RevisionManifestStore.seal(
                    staging,
                    contextId,
                    next,
                    revision,
                    currentConnections,
                    description);

            qualifyAndStageConnectionTransition(
                    staging,
                    next,
                    currentConnections);

            // Validate session-only pins before CURRENT advances. They are deliberately
            // excluded from this revision's durable dependency vector.
            ConnectionVector qualifiedWorking = working == null || working.isEmpty() ? working
                    : WriteCandidateQualification.qualify(
                            ContextCandidate.of(this, staging, next, currentConnections),
                            working).getConnections();

            /*
             * A target with no matching visible revision is an orphan left by
             * a failed/crashed publication. The Context lock proves that no
             * concurrent writer can own it now, so rebuilding it is safe.
             */
            if (Files.exists(target)) {
                deleteRecursively(target);
            }

            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "DUMB2 requires atomic same-filesystem generation publication: "
                                + target, failure);
            }
            generationInstalled = true;

            long published = RevisionStore.advance(revisionPath(location), revision);
            if (published != next) {
                throw new IllegalStateException(
                        "Unexpected DUMB2 published revision " + published
                                + "; expected " + next);
            }

            revision = published;
            qualifiedWorkingConnections = qualifiedWorking;
            for (ContextBase base : bases.values()) {
                if (base.isDirty()) {
                    base.markPublished();
                }
            }
            return revision;
        } finally {
            if (!generationInstalled || Files.exists(staging)) {
                deleteRecursively(staging);
            }
            /*
             * If generation installation succeeded but revision publication
             * failed, target intentionally remains an unpublished orphan. The
             * next exclusive flush rebuilds it while revision still names R.
             */
        }
    }

    /**
     * Publishes one already-qualified working dependency topology as a new
     * immutable Context revision without changing local B/G content.
     *
     * <p>This is a storage primitive only. Authorization/ownership is a higher
     * publication-layer concern and must gate callers before this method becomes
     * a user-visible operation.</p>
     */
    synchronized long publishTopology(ConnectionVector desiredConnections, String description) throws Exception {
        return publishGeneration(null, desiredConnections, description, false);
    }

    synchronized long publishGeneration(Path preparedGeneration,
            ConnectionVector desiredConnections, String description) throws Exception {
        return publishGeneration(preparedGeneration, desiredConnections, description, true);
    }

    private synchronized long publishGeneration(Path preparedGeneration,
            ConnectionVector desiredConnections, String description, boolean forceRevision) throws Exception {
        requireOpen();
        if (desiredConnections == null
                || description == null) {
            throw new NullPointerException();
        }
        for (ContextBase base : bases.values()) {
            if (base.isDirty()) {
                throw new IllegalStateException(
                        "DUMB2 topology publication requires a settled Context");
            }
        }
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "DUMB2 revision space exhausted");
        }

        long persisted =
                RevisionStore.read(
                        revisionPath(location));
        if (persisted != revision) {
            throw new IllegalStateException(
                    "DUMB2 Context revision changed while open: expected="
                            + revision + " actual=" + persisted);
        }

        if(revision>RevisionStore.INITIAL_REVISION) validatePublishedGeneration();

        ConnectionVector currentConnections =
                publishedConnections();
        if (!forceRevision && preparedGeneration == null && java.util.Arrays.equals(
                RevisionManifestStore.dependencyDigest(
                        currentConnections),
                RevisionManifestStore.dependencyDigest(
                        desiredConnections))) {
            return revision;
        }

        long next = revision + 1L;
        ensureRevisionManifestPolicy(next);
        Path root = stateRoot(location);
        Path previous =
                generationPath(location, revision);
        Path target =
                generationPath(location, next);
        Path staging = root.resolve(
                ".topology-" + next + "-"
                        + UUID.randomUUID().toString());

        Files.createDirectories(root);
        deleteRecursively(staging);

        boolean generationInstalled = false;
        try {
            if (preparedGeneration != null) {
                copyDirectory(preparedGeneration, staging);
            } else if (revision == RevisionStore.INITIAL_REVISION) {
                Files.createDirectories(staging);
                for (ContextBase base : bases.values()) {
                    base.writeSnapshot(staging);
                }
            } else {
                if (!Files.isDirectory(previous)) {
                    throw corruption(
                            "DUMB2 Context revision " + revision
                                    + " lost its physical generation at "
                                    + location);
                }
                copyDirectory(previous, staging);
            }

            RevisionManifestStore.seal(
                    staging,
                    contextId,
                    next,
                    revision,
                    desiredConnections,
                    description);

            ContextCandidate candidate =
                    ContextCandidate.of(
                            this,
                            staging,
                            next,
                            desiredConnections);
            WriteCandidateQualification.Result qualification =
                    WriteCandidateQualification.qualify(
                            candidate,
                            desiredConnections);
            RevisionRef candidateSource =
                    new RevisionRef(contextId, next);
            if (!candidateSource.equals(
                    qualification.getCandidate())) {
                throw new IllegalStateException(
                        "Qualified topology candidate identity mismatch: expected "
                                + candidateSource
                                + " found "
                                + qualification.getCandidate());
            }

            ConnectionVector publishedConnections =
                    qualification.getConnections();
            RevisionManifestStore.validateDependencyVector(
                    staging,
                    publishedConnections);
            ConnectionStore.writeTransition(
                    location,
                    new RevisionRef(contextId, revision),
                    currentConnections,
                    candidateSource,
                    publishedConnections);

            if (Files.exists(target)) {
                deleteRecursively(target);
            }
            try {
                Files.move(
                        staging,
                        target,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "DUMB2 requires atomic same-filesystem topology publication: "
                                + target,
                        failure);
            }
            generationInstalled = true;

            long published =
                    RevisionStore.advance(
                            revisionPath(location),
                            revision);
            if (published != next) {
                throw new IllegalStateException(
                        "Unexpected DUMB2 topology revision "
                                + published
                                + "; expected " + next);
            }
            revision = published;
            return revision;
        } finally {
            if (!generationInstalled
                    || Files.exists(staging)) {
                deleteRecursively(staging);
            }
        }
    }

    /**
     * Rewrites every acquired schema through the current canonical adapters
     * and publishes the result as one atomic next Context revision.
     *
     * <p>Canonical images are built without mutating the live bases. Only after
     * the complete pass succeeds is a new immutable generation installed and
     * the revision marker advanced. Therefore a failed reindex cannot leak a
     * partially canonicalized generation through a later close/flush.</p>
     *
     * @return the published revision; unchanged when every record is already
     *         in its canonical physical layout
     */
    synchronized long reindex(IReactor<String> reactor, Mind mind)
            throws Exception {
        requireOpen();
        if (mind == null) {
            throw new IllegalArgumentException("mind is required");
        }

        for (ContextBase base : bases.values()) {
            if (base.isDirty()) {
                throw new IllegalStateException(
                        "DUMB2 reindex requires a settled Context");
            }
        }

        long persisted = RevisionStore.read(revisionPath(location));
        if (persisted != revision) {
            throw new IllegalStateException(
                    "DUMB2 Context revision changed while open: expected="
                            + revision + " actual=" + persisted);
        }

        if (revision > RevisionStore.INITIAL_REVISION) {
            validatePublishedGeneration();
        }

        Map<String, TreeMap<Long, byte[]>> canonical =
                new LinkedHashMap<String, TreeMap<Long, byte[]>>();
        boolean changed = false;
        for (Map.Entry<String, ContextBase> entry : bases.entrySet()) {
            if (reactor != null) {
                reactor.run(entry.getKey());
            }
            TreeMap<Long, byte[]> image =
                    entry.getValue().canonicalizedRecords(mind);
            canonical.put(entry.getKey(), image);
            if (!entry.getValue().sameRecords(image)) {
                changed = true;
            }
        }

        if (!changed) {
            return revision;
        }
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("DUMB2 revision space exhausted");
        }

        long next = revision + 1L;
        ensureRevisionManifestPolicy(next);
        ConnectionVector currentConnections =
                publishedConnections();
        Path root = stateRoot(location);
        Path previous = generationPath(location, revision);
        Path target = generationPath(location, next);
        Path staging = root.resolve(
                ".reindex-" + next + "-" + UUID.randomUUID().toString());

        Files.createDirectories(root);
        deleteRecursively(staging);

        boolean generationInstalled = false;
        try {
            if (revision > RevisionStore.INITIAL_REVISION) {
                if (!Files.isDirectory(previous)) {
                    throw corruption("DUMB2 Context revision " + revision
                            + " lost its physical generation at " + location);
                }
                copyDirectory(previous, staging);
            } else {
                Files.createDirectories(staging);
            }

            for (Map.Entry<String, TreeMap<Long, byte[]>> entry
                    : canonical.entrySet()) {
                ContextBase base = bases.get(entry.getKey());
                if (!base.sameRecords(entry.getValue())) {
                    base.writeSnapshot(staging, entry.getValue());
                }
            }

            RevisionManifestStore.seal(
                    staging,
                    contextId,
                    next,
                    revision,
                    currentConnections,
                    "");

            qualifyAndStageConnectionTransition(
                    staging,
                    next,
                    currentConnections);

            if (Files.exists(target)) {
                deleteRecursively(target);
            }

            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "DUMB2 requires atomic same-filesystem reindex publication: "
                                + target, failure);
            }
            generationInstalled = true;

            long published =
                    RevisionStore.advance(revisionPath(location), revision);
            if (published != next) {
                throw new IllegalStateException(
                        "Unexpected DUMB2 reindex revision " + published
                                + "; expected " + next);
            }

            revision = published;
            for (Map.Entry<String, TreeMap<Long, byte[]>> entry
                    : canonical.entrySet()) {
                ContextBase base = bases.get(entry.getKey());
                if (!base.sameRecords(entry.getValue())) {
                    base.installPublishedRecords(entry.getValue());
                }
            }
            return revision;
        } finally {
            if (!generationInstalled || Files.exists(staging)) {
                deleteRecursively(staging);
            }
            /*
             * If the generation was installed but revision publication failed,
             * it remains an unpublished orphan while the visible marker still
             * names the previous authoritative revision. A later publication
             * rebuilds the same target under the exclusive Context lock.
             */
        }
    }

    /**
     * Runs M3.8 qualification against the complete unpublished generation and
     * pre-publishes a revision-aware connection transition.
     *
     * <p>The sidecar contains both source revisions before CURRENT advances.
     * Therefore R continues to select its old certificates if publication
     * stops or the process crashes, while R+1 already has a fully qualified
     * vector waiting when the revision marker moves.</p>
     */
    private void qualifyAndStageConnectionTransition(
            Path staging,
            long next,
            ConnectionVector currentConnections)
            throws Exception {
        if (currentConnections == null) {
            throw new NullPointerException(
                    "currentConnections");
        }

        /*
         * No sidecar is needed for a historically disconnected Context.
         * The v2 manifest still binds the canonical empty dependency set.
         */
        Path connectionsPath =
                ConnectionStore.path(location);
        if (currentConnections.isEmpty()
                && !Files.exists(connectionsPath)) {
            return;
        }

        RevisionRef currentSource =
                new RevisionRef(
                        contextId, revision);
        RevisionRef candidateSource =
                new RevisionRef(
                        contextId, next);

        if (currentConnections.isEmpty()) {
            RevisionManifestStore.validateDependencyVector(
                    staging,
                    ConnectionVector.empty());
            ConnectionStore.writeTransition(
                    location,
                    currentSource,
                    currentConnections,
                    candidateSource,
                    ConnectionVector.empty());
            return;
        }

        ContextCandidate candidate =
                ContextCandidate.of(
                        this,
                        staging,
                        next,
                        currentConnections);
        WriteCandidateQualification.Result qualification =
                WriteCandidateQualification.qualify(
                        candidate,
                        currentConnections);

        if (!candidateSource.equals(
                qualification.getCandidate())) {
            throw new IllegalStateException(
                    "Qualified candidate identity mismatch: expected "
                            + candidateSource
                            + " found "
                            + qualification.getCandidate());
        }

        ConnectionVector candidateConnections =
                qualification.getConnections();
        /*
         * Qualification refreshes certificates for source R+1 but is not
         * allowed to change the exact target ContextId/RevisionId set sealed
         * into the candidate manifest.
         */
        RevisionManifestStore.validateDependencyVector(
                staging,
                candidateConnections);

        ConnectionStore.writeTransition(
                location,
                currentSource,
                currentConnections,
                candidateSource,
                candidateConnections);
    }

    private ConnectionVector publishedConnections()
            throws IOException, StorageLifecycleException {
        return ConnectionStore.read(
                location,
                new RevisionRef(
                        contextId,
                        revision));
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }

        /*
         * Failed flush leaves the handle open and lock held so the caller can
         * repair/retry. We only invalidate bases after durable publication.
         */
        flush();

        for (ContextBase base : bases.values()) {
            base.closeFromOwner();
        }
        bases.clear();
        closed = true;
        contextLock.close();
    }

    synchronized boolean isClosed() {
        return closed;
    }

    UUID getContextId() {
        return contextId;
    }

    ContextManifestStore.Origin getOrigin() {
        return origin;
    }

    synchronized long getRevision() {
        return revision;
    }

    Path getLocation() {
        return location;
    }

    Path schemaPath(String schema) {
        return schemaPath(generationPath(location, revision), schema);
    }

    Path schemaPath(Path generation, String schema) {
        return schemaPathForGeneration(generation, schema);
    }

    static Path schemaPathForGeneration(Path generation, String schema) {
        Objects.requireNonNull(generation, "generation");
        return generation.resolve(encodeSchema(requireSchema(schema)) + ".base");
    }

    static Path contextPath(Path location) {
        Path normalized = requireLocation(location);
        return normalized.resolveSibling(
                normalized.getFileName().toString() + CONTEXT_SUFFIX);
    }

    static Path revisionPath(Path location) {
        Path normalized = requireLocation(location);
        return normalized.resolveSibling(
                normalized.getFileName().toString() + REVISION_SUFFIX);
    }

    static Path stateRoot(Path location) {
        Path normalized = requireLocation(location);
        return normalized.resolveSibling(
                normalized.getFileName().toString() + STATE_SUFFIX);
    }

    static Path generationPath(Path location, long revision) {
        return stateRoot(location).resolve("revision-" + revision);
    }

    /**
     * Validates the complete visible physical generation without semantic
     * hydration. A Context is not considered openable when any published base
     * is malformed, misnamed, disconnected, or references a typeCode absent
     * from the published manifest.
     */
    private void validatePublishedGeneration()
            throws IOException, StorageLifecycleException {
        Path generation = generationPath(location, revision);
        int baseCount = 0;
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(generation)) {
            for (Path child : stream) {
                String file =
                        child.getFileName().toString();
                if (RevisionManifestStore.FILE_NAME.equals(file)) {
                    if (!Files.isRegularFile(
                            child, LinkOption.NOFOLLOW_LINKS)) {
                        throw corruption(
                                "Invalid DUMB2 revision manifest entry "
                                        + child);
                    }
                    continue;
                }
                if (!Files.isRegularFile(
                        child, LinkOption.NOFOLLOW_LINKS)
                        || !file.endsWith(".base")) {
                    throw corruption(
                            "Unexpected entry in published DUMB2 generation "
                                    + child);
                }

                ++baseCount;
                String schema = ContextBase.readStoredSchema(child);
                Path canonical = schemaPath(generation, schema);
                if (!canonical.equals(child)) {
                    throw corruption(
                            "DUMB2 schema snapshot path does not match "
                                    + "its stored descriptor: " + child);
                }

                ContextBase probe = null;
                try {
                    probe = new ContextBase(this, schema);
                } catch (StorageLifecycleException failure) {
                    throw failure;
                } catch (IOException failure) {
                    throw failure;
                } catch (Exception failure) {
                    StorageLifecycleException invalid = corruption(
                            "Cannot validate published DUMB2 schema " + child);
                    invalid.addSuppressed(failure);
                    throw invalid;
                } finally {
                    if (probe != null) {
                        probe.closeFromOwner();
                    }
                }
            }
        }
        if (baseCount == 0) {
            throw corruption(
                    "Published DUMB2 revision " + revision
                            + " contains no schema snapshots at " + generation);
        }

        boolean sealed =
                RevisionManifestStore.exists(generation);
        if (revision >= revisionManifestBaseline
                && !sealed) {
            throw corruption(
                    "Published DUMB2 revision " + revision
                            + " requires a revision manifest at "
                            + generation);
        }
        if (sealed) {
            RevisionManifestStore.Manifest revisionManifest =
                    RevisionManifestStore.validate(
                            generation,
                            contextId,
                            revision,
                            revision - 1L);
            if (revisionManifest.hasDependencyDigest()) {
                ConnectionVector dependencies =
                        ConnectionStore.read(
                                location,
                                new RevisionRef(
                                        contextId,
                                        revision));
                RevisionManifestStore.validateDependencyVector(
                        revisionManifest,
                        dependencies,
                        generation);
            }
        }
    }

    private void ensureRevisionManifestPolicy(
            long nextRevision)
            throws IOException, StorageLifecycleException {
        ContextManifestStore.Manifest manifest =
                ContextManifestStore.requireRevisionManifestsFrom(
                        contextPath(location),
                        contextId,
                        nextRevision,
                        typeRegistry);
        revisionManifestBaseline =
                manifest.getRevisionManifestBaseline();
    }

    private static Path lockPath(Path location) {
        return stateRoot(location).resolve(".lock");
    }

    private static ContextLock acquireLock(Path location)
            throws IOException, StorageLifecycleException {
        Path lockPath = lockPath(location);
        Files.createDirectories(lockPath.getParent());
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException failure) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN,
                        "DUMB2 Context is already open at " + location);
            }
            return new ContextLock(channel, lock);
        } catch (IOException | StorageLifecycleException
                 | RuntimeException | Error failure) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("DUMB2 Context is closed: " + location);
        }
    }

    private static String requireSchema(String schema) {
        Objects.requireNonNull(schema, "schema");
        if (schema.isEmpty()) {
            throw new IllegalArgumentException("DUMB2 schema descriptor must not be empty");
        }
        return schema;
    }

    private static String encodeSchema(String schema) {
        byte[] bytes = schema.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        final char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte raw : bytes) {
            int value = raw & 0xff;
            if ((value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '-' || value == '_' || value == '.') {
                encoded.append((char) value);
            } else {
                encoded.append('%');
                encoded.append(hex[(value >>> 4) & 0x0f]);
                encoded.append(hex[value & 0x0f]);
            }
        }
        return encoded.toString();
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path child : stream) {
                Path destination = target.resolve(child.getFileName().toString());
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    copyDirectory(child, destination);
                } else {
                    Files.copy(child, destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static Path requireLocation(Path location) {
        Objects.requireNonNull(location, "location");
        if (location.getFileName() == null) {
            throw new IllegalArgumentException(
                    "DUMB2 Context location must have a final path component");
        }
        return location;
    }

    private static StorageLifecycleException incomplete(Path location) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                "Incomplete DUMB2 Context metadata at " + location
                        + ": both " + CONTEXT_SUFFIX + " and " + REVISION_SUFFIX
                        + " sidecars are required");
    }

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION, message);
    }

    private static void closeQuietly(ContextLock lock, Throwable primary) {
        if (lock == null) {
            return;
        }
        try {
            lock.close();
        } catch (IOException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static final class ContextLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private ContextLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                if (lock.isValid()) {
                    lock.release();
                }
            } catch (IOException error) {
                failure = error;
            }
            try {
                channel.close();
            } catch (IOException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
