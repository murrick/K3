/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.Version;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Hypothesis;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.interfaces.internal.StorageTelemetry;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Runtime-facing IData adapter for one autonomous DUMB 2.0 Context.
 *
 * <p>This class is exposed through the explicit {@code dumb2}
 * RuntimeBootstrap provider. The normal Console/Server runtime layout still
 * packages only the stable DUMB provider; isolated manual-soak launchers place
 * DUMB2 on the classpath instead.</p>
 *
 * <p>The adapter owns only physical Context lifecycle. Semantic transaction
 * layering, commit/rollback and factory publication remain in User/Mind.
 * Root settlement eventually reaches {@link #flush()}, where
 * {@link ContextStore} publishes one storage-wide revision.</p>
 */
public final class DB implements IData, IContextFederation {

    private IUser user;
    private ContextStore context;
    private String storageName = "";
    private final Map<String, IBase> bases =
            new LinkedHashMap<String, IBase>();
    /*
     * Session-local topology candidate. Published vectors remain immutable
     * revision state in ConnectionStore; ctx connect/disconnect/switch mutate
     * only this adapter instance until a future authorized publication boundary
     * is introduced.
     */
    private ConnectionVector workingConnections =
            ConnectionVector.empty();

    @Override
    public synchronized void init(IUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        this.user = user;
        ((User) user).setData(this);
    }

    @Override
    public synchronized void use(String name) throws Exception {
        requireInitialized();
        if (name == null || name.trim().isEmpty()) {
            throw new CommandErrorException("DB name expected");
        }
        if (!isClosed()) {
            close();
        }

        Path location = location(name);
        boolean hasContext = Files.exists(ContextStore.contextPath(location));
        boolean hasRevision = Files.exists(ContextStore.revisionPath(location));
        boolean hasState = Files.exists(ContextStore.stateRoot(location));

        ContextStore acquired;
        if (!hasContext && !hasRevision && !hasState) {
            acquired = ContextStore.create(location);
        } else {
            acquired = ContextStore.open(location);
        }

        context = acquired;
        storageName = name;
        bases.clear();
        workingConnections = publishedConnections();
    }

    @Override
    public synchronized void close() throws Exception {
        if (context == null) {
            bases.clear();
            workingConnections = ConnectionVector.empty();
            storageName = "";
            return;
        }

        /*
         * ContextStore.close() keeps the Context open if flush fails. Mirror
         * that failure atomicity here: only clear the IData generation after
         * the physical owner has actually closed.
         */
        context.close();
        bases.clear();
        workingConnections = ConnectionVector.empty();
        context = null;
        storageName = "";
    }

    @Override
    public synchronized void flush() throws Exception {
        requireOpen();
        long before = context.getRevision();
        context.flush();
        if (context.getRevision() != before) {
            /*
             * A local semantic publication establishes a new immutable source
             * revision. A session topology candidate is revision-scoped, so it
             * is conservatively rebased to that revision's published vector.
             */
            workingConnections = publishedConnections();
        }
    }

    @Override
    public synchronized void remove(String name) throws Exception {
        requireInitialized();

        String target;
        if (name == null || name.isEmpty()) {
            if (isClosed()) {
                throw new CommandErrorException("DB name expected");
            }
            target = storageName;
        } else {
            target = name;
        }

        if (!isClosed() && storageName.equals(target)) {
            close();
        }

        Path location = location(target);
        boolean existed = storageArtifactsExist(location);
        if (!existed) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                    "DUMB2 Context was not found: " + target);
        }

        IOException failure = null;
        try {
            deleteRecursively(ContextStore.stateRoot(location));
        } catch (IOException error) {
            failure = error;
        }
        try {
            Files.deleteIfExists(ContextStore.revisionPath(location));
        } catch (IOException error) {
            failure = accumulate(failure, error);
        }
        try {
            Files.deleteIfExists(ContextStore.contextPath(location));
        } catch (IOException error) {
            failure = accumulate(failure, error);
        }
        try {
            ConnectionStore.delete(location);
        } catch (IOException error) {
            failure = accumulate(failure, error);
        }

        if (failure != null || storageArtifactsExist(location)) {
            StorageLifecycleException incomplete = new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_DELETE_INCOMPLETE,
                    "DUMB2 Context deletion was incomplete: " + target);
            if (failure != null) {
                incomplete.addSuppressed(failure);
            }
            throw incomplete;
        }
        pruneEmptyParents(location);
    }

    @Override
    public synchronized boolean exists(String name) throws Exception {
        requireInitialized();
        if (name == null || name.isEmpty()) {
            return false;
        }
        return storageArtifactsExist(location(name));
    }

    @Override
    public synchronized void reindex(IReactor<String> reactor, IMind mind)
            throws Exception {
        requireOpen();
        if (!(mind instanceof Mind)) {
            throw new IllegalArgumentException(
                    "DUMB2 reindex requires org.kanger.Mind");
        }
        long before = context.getRevision();
        context.reindex(reactor, (Mind) mind);
        if (context.getRevision() != before) {
            workingConnections = publishedConnections();
        }
    }

    @Override
    public synchronized boolean isClosed() {
        return context == null || context.isClosed();
    }

    @Override
    public synchronized String getStorageName() {
        return isClosed() ? "" : storageName;
    }

    @Override
    public synchronized IBase getBase(String schema) throws Exception {
        requireOpen();
        IBase base = bases.get(schema);
        if (base == null) {
            base = context.getBase(schema);
            bases.put(schema, base);
        }
        return base;
    }

    @Override
    public synchronized IBase connect(String schema) {
        return isClosed() ? null : bases.get(schema);
    }

    @Override
    public String getDescription() {
        return "DUMB 2.0 data model";
    }

    @Override
    public StorageTelemetry telemetry() {
        return StorageTelemetry.unavailable();
    }

    @Override
    public synchronized Collection<String> list() {
        if (user == null) {
            return new ArrayList<String>();
        }

        Path root = databaseRoot();
        ArrayList<String> result = new ArrayList<String>();
        if (!Files.isDirectory(root)) {
            return result;
        }

        try {
            collectContexts(root, root, result);
        } catch (IOException ignored) {
            /*
             * IData.list() has no checked failure surface. A namespace that
             * cannot be enumerated truthfully is exposed as the successfully
             * observed subset; exists()/use() retain checked diagnostics.
             */
        }
        return result;
    }

    synchronized long getRevision() {
        return context == null ? -1L : context.getRevision();
    }

    synchronized java.util.UUID getContextId() {
        return context == null ? null : context.getContextId();
    }

    @Override
    public synchronized IContextFederation.SourceDependencyPlan
            prepareSourceDependencies(
                    List<IContextFederation.SourceDependencyRequest> requests)
            throws Exception {
        requireOpen();
        if (requests == null) {
            throw new NullPointerException("requests");
        }

        RevisionRef sourceRef =
                new RevisionRef(
                        context.getContextId(),
                        context.getRevision());
        ConnectionVector prepared =
                ConnectionVector.empty();
        ArrayList<IContextFederation.SourceDependency> projected =
                new ArrayList<IContextFederation.SourceDependency>();

        for (IContextFederation.SourceDependencyRequest request
                : requests) {
            if (request == null) {
                throw new NullPointerException(
                        "source dependency request");
            }
            Path targetLocation =
                    resolveFederationLocator(
                            request.getLocator());
            ContextConnection connection =
                    request.isExact()
                            ? ConnectionManager.qualifyConnect(
                                    context.getLocation(),
                                    targetLocation,
                                    request.getExactRevision())
                            : ConnectionManager.qualifyConnect(
                                    context.getLocation(),
                                    targetLocation);

            java.util.UUID targetId =
                    connection.getTarget()
                            .getContextId();
            if (prepared.find(targetId) != null) {
                throw new IllegalArgumentException(
                        "Duplicate source dependency Context: "
                                + targetId);
            }
            prepared = prepared.with(connection);
            projected.add(
                    projectSourceDependency(
                            connection));
        }

        PairQualification.CompositionQualification composition =
                PairQualification.qualifyCompositionState(
                        context.getLocation(),
                        sourceRef.getRevision(),
                        prepared);
        if (!composition.isValid()) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "Declarative source dependencies fail direct multi-context composition qualification for "
                            + sourceRef,
                    composition.getCollisions());
        }

        sortSourceDependencies(projected);
        return new PreparedSourceDependencyPlan(
                sourceRef,
                prepared,
                projected);
    }

    @Override
    public synchronized void installSourceDependencies(
            IContextFederation.SourceDependencyPlan plan)
            throws Exception {
        requireOpen();
        if (!(plan instanceof PreparedSourceDependencyPlan)) {
            throw new IllegalArgumentException(
                    "Source dependency plan does not belong to DUMB2");
        }
        PreparedSourceDependencyPlan prepared =
                (PreparedSourceDependencyPlan) plan;
        RevisionRef sourceRef =
                new RevisionRef(
                        context.getContextId(),
                        context.getRevision());

        ConnectionVector vector = prepared.vector;
        if (!sourceRef.equals(prepared.source)) {
            /*
             * A successful Core compile may publish R+1 before declarative
             * metadata is installed. Rebind the same exact dependency targets
             * to the actual source revision; never follow target CURRENT.
             */
            vector = ConnectionVector.empty();
            for (IContextFederation.SourceDependency dependency
                    : prepared.dependencies) {
                Path targetLocation =
                        resolveFederationLocator(
                                dependency.getLocator());
                ContextConnection connection =
                        ConnectionManager.qualifyConnect(
                                context.getLocation(),
                                targetLocation,
                                dependency.getRevision());
                if (!dependency.getContextId().equals(
                        connection.getTarget().getContextId())) {
                    throw new StorageLifecycleException(
                            StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                            "Declarative dependency Context identity changed at "
                                    + dependency.getLocator()
                                    + ": expected "
                                    + dependency.getContextId()
                                    + " found "
                                    + connection.getTarget().getContextId());
                }
                vector = vector.with(connection);
            }
        }

        PairQualification.CompositionQualification composition =
                PairQualification.qualifyCompositionState(
                        context.getLocation(),
                        sourceRef.getRevision(),
                        vector);
        if (!composition.isValid()) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "Declarative source dependencies fail direct multi-context composition qualification for "
                            + sourceRef,
                    composition.getCollisions());
        }

        for (ContextConnection connection
                : vector.getConnections()) {
            if (!connection.getCertificate().matches(
                    sourceRef,
                    connection.getTarget(),
                    Version.CORE_VERSION_S)) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                        "Prepared source dependency certificate is stale for "
                                + sourceRef + " / "
                                + connection.getTarget());
            }
        }

        workingConnections = vector;
    }

    @Override
    public synchronized List<IContextFederation.SourceDependency>
            sourceDependencies() throws Exception {
        requireOpen();
        ArrayList<IContextFederation.SourceDependency> result =
                new ArrayList<IContextFederation.SourceDependency>();
        for (ContextConnection connection
                : workingConnections.getConnections()) {
            result.add(
                    projectSourceDependency(
                            connection));
        }
        sortSourceDependencies(result);
        return Collections.unmodifiableList(result);
    }

    private IContextFederation.SourceDependency projectSourceDependency(
            ContextConnection connection) {
        return new IContextFederation.SourceDependency(
                displayFederationLocator(
                        connection.getTargetLocation()),
                connection.getTarget()
                        .getContextId(),
                connection.getTarget()
                        .getRevision());
    }

    private void sortSourceDependencies(
            List<IContextFederation.SourceDependency> dependencies) {
        Collections.sort(
                dependencies,
                new Comparator<IContextFederation.SourceDependency>() {
                    @Override
                    public int compare(
                            IContextFederation.SourceDependency left,
                            IContextFederation.SourceDependency right) {
                        int locator = left.getLocator()
                                .compareTo(right.getLocator());
                        if (locator != 0) {
                            return locator;
                        }
                        int context = left.getContextId().toString()
                                .compareTo(
                                        right.getContextId().toString());
                        if (context != 0) {
                            return context;
                        }
                        return Long.compare(
                                left.getRevision(),
                                right.getRevision());
                    }
                });
    }

    @Override
    public synchronized IContextFederation.Snapshot federationSnapshot()
            throws Exception {
        requireOpen();

        Path sourceLocation = context.getLocation();
        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation);
        try {
            RevisionRef sourceRef = new RevisionRef(
                    source.getContextId(),
                    source.getRevision());
            ArrayList<IContextFederation.Connection> connections =
                    new ArrayList<IContextFederation.Connection>();
            for (ContextConnection connection
                    : workingConnections.getConnections()) {
                connections.add(projectConnection(
                        sourceRef, connection));
            }
            return new IContextFederation.Snapshot(
                    storageName,
                    sourceRef.getContextId(),
                    sourceRef.getRevision(),
                    connections);
        } finally {
            source.close();
        }
    }

    @Override
    public synchronized IContextFederation.VersionHistory versionHistory(
            String targetLocator) throws Exception {
        requireOpen();

        Path sourceLocation =
                context.getLocation()
                        .toAbsolutePath()
                        .normalize();
        Path selectedLocation = sourceLocation;
        String selectedLocator = storageName;
        long pinnedRevision = -1L;

        if (targetLocator != null
                && !targetLocator.trim().isEmpty()) {
            Path requested =
                    resolveFederationLocator(
                            targetLocator);
            if (!sourceLocation.equals(requested)) {
                ContextConnection connection =
                        connectedContext(
                                requested,
                                targetLocator);
                selectedLocation =
                        connection.getTargetLocation()
                                .toAbsolutePath()
                                .normalize();
                selectedLocator =
                        displayFederationLocator(
                                connection.getTargetLocation());
                pinnedRevision =
                        connection.getTarget()
                                .getRevision();
            }
        }

        ContextSnapshot current =
                ContextSnapshot.open(
                        selectedLocation);
        try {
            long currentRevision =
                    current.getRevision();
            ArrayList<IContextFederation.RevisionVersion>
                    revisions =
                    new ArrayList<IContextFederation.RevisionVersion>();

            if (currentRevision
                    == RevisionStore.INITIAL_REVISION) {
                revisions.add(
                        new IContextFederation.RevisionVersion(
                                RevisionStore.INITIAL_REVISION,
                                ""));
            } else {
                for (long revision = currentRevision;
                     revision > RevisionStore.INITIAL_REVISION;
                     --revision) {
                    Path generation =
                            ContextStore.generationPath(
                                    selectedLocation,
                                    revision);
                    if (!Files.isDirectory(generation)) {
                        throw new StorageLifecycleException(
                                StorageLifecycleErrorCode
                                        .STORAGE_SEMANTIC_CORRUPTION,
                                "DUMB2 revision history lost "
                                        + selectedLocator
                                        + "@"
                                        + revision);
                    }

                    String description = "";
                    if (RevisionManifestStore.exists(
                            generation)) {
                        description =
                                RevisionManifestStore.read(
                                        generation)
                                        .getDescription();
                    }
                    revisions.add(
                            new IContextFederation.RevisionVersion(
                                    revision,
                                    description));
                }
            }

            return new IContextFederation.VersionHistory(
                    selectedLocator,
                    current.getContextId(),
                    currentRevision,
                    pinnedRevision,
                    revisions);
        } finally {
            current.close();
        }
    }

    @Override
    public synchronized IContextFederation.Connection connectContext(
            String targetLocator) throws Exception {
        requireOpen();
        ContextConnection connection =
                ConnectionManager.qualifyConnect(
                        context.getLocation(),
                        resolveFederationLocator(targetLocator));
        workingConnections =
                workingConnections.with(connection);
        return projectConnection(
                new RevisionRef(
                        context.getContextId(),
                        context.getRevision()),
                connection);
    }

    @Override
    public synchronized void disconnectContext(
            String targetLocator) throws Exception {
        disconnectContext(
                connectedContextId(targetLocator));
    }

    @Override
    public synchronized void disconnectContext(
            java.util.UUID targetContextId) throws Exception {
        requireOpen();
        workingConnections =
                workingConnections.without(targetContextId);
    }

    @Override
    public synchronized IContextFederation.Connection switchContextRevision(
            String targetLocator,
            long targetRevision) throws Exception {
        return switchContextRevision(
                connectedContextId(targetLocator),
                targetRevision);
    }

    @Override
    public synchronized IContextFederation.Connection switchContextRevision(
            java.util.UUID targetContextId,
            long targetRevision) throws Exception {
        requireOpen();
        ContextConnection connection =
                ConnectionManager.qualifySwitchRevision(
                        context.getLocation(),
                        workingConnections,
                        targetContextId,
                        targetRevision);
        workingConnections =
                workingConnections.with(connection);
        return projectConnection(
                new RevisionRef(
                        context.getContextId(),
                        context.getRevision()),
                connection);
    }

    private IContextFederation.Connection projectConnection(
            RevisionRef sourceRef,
            ContextConnection connection) throws Exception {
        RevisionRef targetRef = connection.getTarget();

        ContextSnapshot pinned = ContextSnapshot.open(
                connection.getTargetLocation(),
                targetRef.getRevision());
        try {
            if (!targetRef.getContextId().equals(
                    pinned.getContextId())) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                        "Pinned Context identity mismatch for "
                                + connection.getTargetLocation()
                                + ": expected "
                                + targetRef.getContextId()
                                + ", found "
                                + pinned.getContextId());
            }
        } finally {
            pinned.close();
        }

        long currentRevision;
        ContextSnapshot current = ContextSnapshot.open(
                connection.getTargetLocation());
        try {
            if (!targetRef.getContextId().equals(
                    current.getContextId())) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                        "Target CURRENT identity mismatch for "
                                + connection.getTargetLocation()
                                + ": expected "
                                + targetRef.getContextId()
                                + ", found "
                                + current.getContextId());
            }
            currentRevision = current.getRevision();
        } finally {
            current.close();
        }

        CompatibilityCertificate certificate =
                connection.getCertificate();
        IContextFederation.CompatibilityStatus status =
                certificate.matches(
                        sourceRef,
                        targetRef,
                        Version.CORE_VERSION_S)
                ? IContextFederation.CompatibilityStatus.QUALIFIED
                : IContextFederation.CompatibilityStatus.STALE;

        return new IContextFederation.Connection(
                displayFederationLocator(
                        connection.getTargetLocation()),
                targetRef.getContextId(),
                targetRef.getRevision(),
                currentRevision,
                IContextFederation.PinPolicy.EXACT_REVISION,
                status,
                certificate.getSemanticVersion());
    }

    @Override
    public synchronized boolean hasConnectedContexts()
            throws Exception {
        requireOpen();
        return !workingConnections.isEmpty();
    }

    @Override
    public synchronized IContextFederation.QueryResult continueFederatedQuery(
            IMind sourceMind,
            String querySource,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        requireOpen();
        if (!(sourceMind instanceof Mind)) {
            throw new IllegalArgumentException(
                    "Federated continuation requires org.kanger.Mind");
        }
        if (sourceMind.getUser() != user) {
            throw new IllegalArgumentException(
                    "Federated continuation requires the active storage User");
        }

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        (Mind) sourceMind,
                        context.getLocation(),
                        workingConnections,
                        querySource,
                        externals,
                        logging);
        return projectQueryResult(result);
    }

    @Override
    public synchronized IContextFederation.QueryResult executeIsolatedQuery(
            IMind sourceMind,
            String targetLocator,
            String querySource) throws Exception {
        requireOpen();
        if (!(sourceMind instanceof Mind)) {
            throw new IllegalArgumentException(
                    "Isolated Context query requires org.kanger.Mind");
        }
        if (sourceMind.getUser() != user) {
            throw new IllegalArgumentException(
                    "Isolated Context query requires the active storage User");
        }
        if (querySource == null
                || querySource.isEmpty()
                || querySource.charAt(0) != '?') {
            throw new IllegalArgumentException(
                    "Isolated Context query requires a query source");
        }

        Path requested =
                resolveFederationLocator(targetLocator);
        Path sourceLocation =
                context.getLocation()
                        .toAbsolutePath().normalize();
        RevisionRef sourceRef =
                new RevisionRef(
                        context.getContextId(),
                        context.getRevision());

        if (sourceLocation.equals(requested)) {
            return projectLocalQuery(
                    (Mind) sourceMind,
                    sourceRef,
                    querySource);
        }

        ContextConnection connection =
                connectedContext(requested, targetLocator);
        RevisionRef targetRef =
                connection.getTarget();
        SnapshotMindRuntime runtime =
                SnapshotMindRuntime.open(
                        connection.getTargetLocation(),
                        targetRef,
                        "isolated-"
                                + targetRef.getContextId().toString());
        Mind root = runtime.getMind();
        Mind work = Mind.ephemeralChild(root);
        try {
            return projectLocalQuery(
                    work,
                    targetRef,
                    querySource);
        } finally {
            Throwable failure = null;
            try {
                work.getSolutions().clear();
                work.getValues().clear();
                root.release(work);
            } catch (Throwable releaseFailure) {
                failure = releaseFailure;
            }
            try {
                runtime.close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (closeFailure != failure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure != null) {
                if (failure instanceof Exception) {
                    throw (Exception) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                throw new RuntimeException(failure);
            }
        }
    }

    private IContextFederation.QueryResult projectLocalQuery(
            Mind mind,
            RevisionRef source,
            String querySource) throws Exception {
        Boolean answer =
                mind.queryCanonical(
                        querySource,
                        new LinkedList<ITerm>(),
                        false);

        ArrayList<IContextFederation.ValueRow> values =
                new ArrayList<IContextFederation.ValueRow>();
        for (Map<String, ITerm> row : mind.getValues()) {
            LinkedHashMap<String, String> bindings =
                    new LinkedHashMap<String, String>();
            for (Map.Entry<String, ITerm> binding
                    : row.entrySet()) {
                ITerm value = binding.getValue();
                bindings.put(
                        binding.getKey(),
                        value == null ? "" : value.toString());
            }
            values.add(
                    new IContextFederation.ValueRow(bindings));
        }

        ArrayList<IContextFederation.ProvisionalHypothesis> hypotheses =
                new ArrayList<IContextFederation.ProvisionalHypothesis>();
        IContextFederation.Revision revision =
                projectRevision(source);
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            hypotheses.add(
                    new IContextFederation.ProvisionalHypothesis(
                            revision,
                            ((Hypothesis) hypothesis).toString(mind)));
        }

        return new IContextFederation.QueryResult(
                answer != null,
                answer == null
                        ? IContextFederation.FrontierTruth.UNKNOWN
                        : (answer.booleanValue()
                                ? IContextFederation.FrontierTruth.TRUE
                                : IContextFederation.FrontierTruth.FALSE),
                0,
                0,
                java.util.Collections
                        .<IContextFederation.FrontierObservation>emptyList(),
                values,
                hypotheses);
    }

    @Override
    public synchronized IContextFederation.QueryResult executeFederatedQuery(
            String querySource) throws Exception {
        requireOpen();

        FrontierContinuationEngine.Result result =
                FrontierContinuationEngine.execute(
                        context.getLocation(),
                        workingConnections,
                        querySource);
        return projectQueryResult(result);
    }

    private IContextFederation.QueryResult projectQueryResult(
            FrontierContinuationEngine.Result result) {
        ArrayList<IContextFederation.FrontierObservation> observations =
                new ArrayList<IContextFederation.FrontierObservation>();
        for (FrontierContinuationEngine.FrontierObservation observation
                : result.getObservations()) {
            FrontierAggregate aggregate =
                    observation.getAggregate();
            observations.add(
                    new IContextFederation.FrontierObservation(
                            observation.getWave(),
                            observation.getQuerySource(),
                            projectTruth(aggregate.getTruth()),
                            projectRevisions(
                                    aggregate.getTrueSources()),
                            projectRevisions(
                                    aggregate.getFalseSources()),
                            projectRevisions(
                                    aggregate.getUnknownSources())));
        }

        ArrayList<IContextFederation.EvidenceInjection> injections =
                new ArrayList<IContextFederation.EvidenceInjection>();
        for (FrontierContinuationEngine.EvidenceInjection injection
                : result.getEvidenceInjections()) {
            LinkedHashMap<String, String> substitutions =
                    new LinkedHashMap<String, String>(
                            injection.getSubstitutions());
            injections.add(
                    new IContextFederation.EvidenceInjection(
                            injection.getStatement(),
                            substitutions,
                            projectRevisions(
                                    injection.getSupports())));
        }

        ArrayList<IContextFederation.ProvisionalHypothesis> hypotheses =
                new ArrayList<IContextFederation.ProvisionalHypothesis>();
        for (FrontierAggregate.ProvisionalHypothesis hypothesis
                : result.getProvisionalHypotheses()) {
            hypotheses.add(
                    new IContextFederation.ProvisionalHypothesis(
                            projectRevision(
                                    hypothesis.getSource()),
                            hypothesis.getStatement()));
        }

        return new IContextFederation.QueryResult(
                result.isResolved(),
                result.isResolved()
                        ? IContextFederation.FrontierTruth.TRUE
                        : IContextFederation.FrontierTruth.UNKNOWN,
                result.getWaves(),
                result.getEvidenceCount(),
                observations,
                java.util.Collections
                        .<IContextFederation.ValueRow>emptyList(),
                injections,
                hypotheses);
    }

    private IContextFederation.FrontierTruth projectTruth(
            FrontierAggregate.Truth truth) {
        switch (truth) {
            case TRUE:
                return IContextFederation.FrontierTruth.TRUE;
            case FALSE:
                return IContextFederation.FrontierTruth.FALSE;
            case UNKNOWN:
                return IContextFederation.FrontierTruth.UNKNOWN;
            case CONFLICT:
                return IContextFederation.FrontierTruth.CONFLICT;
            default:
                throw new IllegalStateException(
                        "Unsupported frontier truth: " + truth);
        }
    }

    private ArrayList<IContextFederation.Revision> projectRevisions(
            Collection<RevisionRef> source) {
        ArrayList<IContextFederation.Revision> result =
                new ArrayList<IContextFederation.Revision>();
        for (RevisionRef ref : source) {
            result.add(projectRevision(ref));
        }
        return result;
    }

    private IContextFederation.Revision projectRevision(
            RevisionRef ref) {
        return new IContextFederation.Revision(
                ref.getContextId(),
                ref.getRevision());
    }

    private java.util.UUID connectedContextId(
            String targetLocator) throws Exception {
        return connectedContext(
                resolveFederationLocator(targetLocator),
                targetLocator)
                .getTarget()
                .getContextId();
    }

    private ContextConnection connectedContext(
            Path requested,
            String targetLocator) throws Exception {
        requireOpen();
        for (ContextConnection connection
                : workingConnections.getConnections()) {
            Path target =
                    connection.getTargetLocation()
                            .toAbsolutePath().normalize();
            if (requested.equals(target)) {
                return connection;
            }
        }
        throw new CommandErrorException(
                "No direct Context connection exists for locator "
                        + targetLocator);
    }

    private Path resolveFederationLocator(
            String locator) throws CommandErrorException {
        if (locator == null || locator.trim().isEmpty()) {
            throw new CommandErrorException(
                    "Context locator expected");
        }
        Path path = Paths.get(locator.trim());
        if (!path.isAbsolute()) {
            path = databaseRoot().resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private String displayFederationLocator(
            Path location) {
        Path root = databaseRoot()
                .toAbsolutePath().normalize();
        Path target = location
                .toAbsolutePath().normalize();
        if (target.startsWith(root)) {
            return root.relativize(target).toString();
        }
        return target.toString();
    }

    private void collectContexts(Path root,
                                 Path directory,
                                 Collection<String> result) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    if (!child.getFileName().toString().endsWith(ContextStore.STATE_SUFFIX)) {
                        collectContexts(root, child, result);
                    }
                    continue;
                }

                String file = child.getFileName().toString();
                if (!file.endsWith(ContextStore.CONTEXT_SUFFIX)) {
                    continue;
                }

                String stem = file.substring(
                        0, file.length() - ContextStore.CONTEXT_SUFFIX.length());
                Path logical = child.resolveSibling(stem);
                Path relative = root.relativize(logical);
                result.add(relative.toString());
            }
        }
    }

    private static final class PreparedSourceDependencyPlan
            implements IContextFederation.SourceDependencyPlan {

        private final RevisionRef source;
        private final ConnectionVector vector;
        private final List<IContextFederation.SourceDependency>
                dependencies;

        private PreparedSourceDependencyPlan(
                RevisionRef source,
                ConnectionVector vector,
                List<IContextFederation.SourceDependency> dependencies) {
            this.source = source;
            this.vector = vector;
            this.dependencies =
                    Collections.unmodifiableList(
                            new ArrayList<IContextFederation.SourceDependency>(
                                    dependencies));
        }

        @Override
        public List<IContextFederation.SourceDependency>
                getDependencies() {
            return dependencies;
        }
    }

    private ConnectionVector publishedConnections()
            throws Exception {
        RevisionRef sourceRef = new RevisionRef(
                context.getContextId(),
                context.getRevision());
        return ConnectionStore.read(
                context.getLocation(), sourceRef);
    }

    private boolean storageArtifactsExist(Path location) {
        return Files.exists(ContextStore.contextPath(location))
                || Files.exists(ContextStore.revisionPath(location))
                || Files.exists(ContextStore.stateRoot(location))
                || Files.exists(ConnectionStore.path(location));
    }

    private Path location(String name) {
        return databaseRoot().resolve(name);
    }

    private Path databaseRoot() {
        String directory = user == null ? "" : user.getDatabaseDir();
        if (directory == null || directory.isEmpty()) {
            return Paths.get(".");
        }
        return Paths.get(directory);
    }

    private void requireInitialized() {
        if (user == null) {
            throw new IllegalStateException("DUMB2 DB is not initialized");
        }
    }

    private void requireOpen() {
        requireInitialized();
        if (isClosed()) {
            throw new IllegalStateException("DUMB2 Context is not open");
        }
    }

    private void pruneEmptyParents(Path location) {
        Path root = databaseRoot().toAbsolutePath().normalize();
        Path directory = location.toAbsolutePath().normalize().getParent();
        while (directory != null && !directory.equals(root)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                if (stream.iterator().hasNext()) {
                    return;
                }
            } catch (IOException failure) {
                return;
            }
            Path parent = directory.getParent();
            try {
                Files.deleteIfExists(directory);
            } catch (IOException failure) {
                return;
            }
            directory = parent;
        }
    }

    private static IOException accumulate(IOException primary, IOException next) {
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private static void deleteRecursively(Path path) throws IOException {
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
}
