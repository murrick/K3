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
 * <p>This class is exposed through the {@code dumb2} RuntimeBootstrap provider,
 * the default storage module in standard Console, Server and SDK deliveries.
 * Legacy DUMB remains separately available for explicit compatibility use.</p>
 *
 * <p>The adapter owns only physical Context lifecycle. Semantic transaction
 * layering, commit/rollback and factory publication remain in User/Mind.
 * Root settlement eventually reaches {@link #flush()}, where
 * {@link ContextStore} publishes one storage-wide revision.</p>
 */
public final class DB implements IData, IContextFederation, org.kanger.interfaces.internal.IRevisionPublication {

    private IUser user;
    private ContextStore context;
    private ContextSnapshot historical;
    private Path historicalLocation;
    private String storageName = "";
    private final Map<String, IBase> bases =
            new LinkedHashMap<String, IBase>();
    /*
     * Session-local topology candidate. Published vectors remain immutable
     * revision state in ConnectionStore; ctx connect/disconnect/switch mutate
     * only this adapter instance until the explicit settled-root save boundary.
     */
    private String nextRevisionDescription;
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
    public synchronized void validateStorageOpen(IMind source, String name) throws Exception {
        if (name==null) return;
        int at=name.lastIndexOf('@');
        if (at<=0 || !name.substring(at+1).matches("[0-9]+")) return;
        if (source!=null && source.getTransactionLevel()>0)
            throw new CommandErrorException("Opening an immutable revision requires U0; commit or roll back user transactions first");
        long revision;
        try { revision=Long.parseLong(name.substring(at+1)); }
        catch (NumberFormatException malformed) { throw new CommandErrorException("Invalid Context revision: " + name); }
        try (ContextSnapshot ignored=ContextSnapshot.open(location(name.substring(0,at)),revision)) { }
    }

    @Override
    public synchronized void use(String name) throws Exception {
        requireInitialized();
        if (name == null || name.trim().isEmpty()) {
            throw new CommandErrorException("DB name expected");
        }
        validateStorageOpen(user.getCurrentMind(),name);
        if (!isClosed()) {
            close();
        }

        int at=name.lastIndexOf('@');
        if (at>0 && name.substring(at+1).matches("[0-9]+")) {
            if (user.getCurrentMind()!=null && user.getCurrentMind().getTransactionLevel()>0)
                throw new CommandErrorException("Opening an immutable revision requires U0; commit or roll back user transactions first");
            long revision;
            try { revision=Long.parseLong(name.substring(at+1)); }
            catch (NumberFormatException malformed) { throw new CommandErrorException("Invalid Context revision: " + name); }
            Path selected=location(name.substring(0,at));
            historical=ContextSnapshot.open(selected,revision);
            historicalLocation=selected; storageName=name; bases.clear();
            retiredLayers.addAll(workingConnections.getConnections());
            workingConnections =publishedConnections();
            try {
                for (ContextConnection connection : workingConnections.getConnections())
                    if (!connection.getInitialization().isEmpty()) connection.layer();
            } catch (Exception failure) { close(); throw failure; }
            return;
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
        retiredLayers.addAll(workingConnections.getConnections());
        workingConnections = publishedConnections();
        try {
            for (ContextConnection connection : workingConnections.getConnections())
                if (!connection.getInitialization().isEmpty()) connection.layer();
        } catch (Exception failure) { close(); throw failure; }
    }

    private final java.util.Set<ContextConnection> retiredLayers =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ContextConnection, Boolean>());
    private void closeConnectionLayers() throws Exception {
        retiredLayers.addAll(workingConnections.getConnections());
        for (ContextConnection connection : retiredLayers) connection.closeLayer();
        retiredLayers.clear();
    }

    @Override
    public synchronized void close() throws Exception {
        closeConnectionLayers();
        if (historical!=null) {
            for (IBase base : bases.values()) if (base instanceof SnapshotRuntimeBase) ((SnapshotRuntimeBase)base).close();
            historical.close(); historical=null; historicalLocation=null;
            bases.clear(); workingConnections=ConnectionVector.empty(); storageName="";
            return;
        }
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

    private boolean validatingCommit;

    @Override
    public synchronized void validateCommit(IMind proposed) throws Exception {
        if (validatingCommit || isClosed() || isReadOnly() || workingConnections.isEmpty()) return;
        validatingCommit = true;
        try {
            validateEffectiveConnections((Mind) proposed, workingConnections);
        } finally { validatingCommit = false; }
    }

    private void validateEffectiveConnections(Mind proposed, ConnectionVector connections) throws Exception {
        RevisionRef source = new RevisionRef(context.getContextId(), getRevision());
        for (ContextConnection connection : connections.getConnections()) {
            PairQualification.Result pair = PairQualification.qualify(proposed, source, connection);
            if (!pair.isCompatible()) throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "Proposed Context pair is not compatible: " + source + " / " + connection.getTarget(),
                    pair.getCollisions());
        }
        PairQualification.CompositionQualification composition =
                PairQualification.qualifyCompositionState(proposed, connections);
        if (!composition.isValid()) throw new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                "Proposed Context introduces an X-anchored composition conflict", composition.getCollisions());
    }

    @Override
    public synchronized void flush() throws Exception {
        requireInitialized();
        // An attached provider also serves offline Minds. Root settlement and
        // session logout may flush without any persistent Context being open.
        if (isClosed()) return;
        if (isReadOnly()) return;
        long before = getRevision();
        ConnectionVector pending = workingConnections;
        try {
            context.flush(pending, nextRevisionDescription == null ? "Updated local Context" : nextRevisionDescription);
        } finally {
            nextRevisionDescription = null;
        }
        if (getRevision() != before) {
            // Ordinary authoring advances X without implicitly saving session topology.
            // Requalify exact target pins against the new source revision.
            retiredLayers.addAll(workingConnections.getConnections());
            workingConnections = context.getQualifiedWorkingConnections();
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

        if (target.matches(".+@[0-9]+"))
            throw new CommandErrorException("Immutable Context revisions cannot be removed through a storage name");

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
        int at=name.lastIndexOf('@');
        if (at>0 && name.substring(at+1).matches("[0-9]+")) {
            try (ContextSnapshot selected=ContextSnapshot.open(location(name.substring(0,at)),Long.parseLong(name.substring(at+1)))) { return true; }
            catch (Exception unavailable) { return false; }
        }
        return storageArtifactsExist(location(name));
    }

    @Override
    public synchronized void reindex(IReactor<String> reactor, IMind mind)
            throws Exception {
        requireWritable();
        requireOpen();
        if (!(mind instanceof Mind)) {
            throw new IllegalArgumentException(
                    "DUMB2 reindex requires org.kanger.Mind");
        }
        long before = getRevision();
        context.reindex(reactor, (Mind) mind);
        if (getRevision() != before) {
            retiredLayers.addAll(workingConnections.getConnections());
            workingConnections = publishedConnections();
        }
    }

    @Override
    public synchronized boolean isClosed() {
        return historical==null ? context==null || context.isClosed() : historical.isClosed();
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
            base = historical==null ? context.getBase(schema) : new SnapshotRuntimeBase(historical.getBase(schema));
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
        if (historical!=null && !result.contains(storageName)) result.add(storageName);
        return result;
    }

    public synchronized long getRevision() {
        return historical!=null ? historical.getRevision() : context==null ? -1L : context.getRevision();
    }

    synchronized java.util.UUID getContextId() {
        return historical!=null ? historical.getContextId() : context==null ? null : context.getContextId();
    }

    @Override public synchronized boolean isReadOnly() { return historical!=null; }

    private String sourceLocator() { return historical==null ? storageName : displayFederationLocator(historicalLocation); }

    private Path activeLocation() { return historical!=null ? historicalLocation : context.getLocation(); }
    private void requireWritable() throws CommandErrorException {
        if (isReadOnly()) throw new CommandErrorException("Context " + storageName + " is an immutable revision; open CURRENT for authoring");
    }

    private ContextConnection configuredConnection(Path location, long revision, List<String> commands) throws Exception {
        ContextSnapshot target = revision < 0 ? ContextSnapshot.open(location) : ContextSnapshot.open(location, revision);
        RevisionRef ref;
        try { ref = new RevisionRef(target.getContextId(), target.getRevision()); }
        finally { target.close(); }
        if (ref.getContextId().equals(getContextId()))
            throw new CommandErrorException("A Context cannot connect to itself");
        ContextConnection candidate = new ContextConnection(location, ref,
                new CompatibilityCertificate(new RevisionRef(getContextId(), getRevision()), ref, Version.CORE_VERSION_S), commands);
        boolean accepted = false;
        try {
            PairQualification.Result pair = PairQualification.qualify(activeLocation(), getRevision(), candidate);
            if (!pair.isCompatible()) throw new CommandErrorException("Configured source dependency is incompatible with X");
            ContextConnection qualified = candidate.recertified(pair.getCertificate());
            retiredLayers.add(qualified);
            accepted = true;
            return qualified;
        } finally { if (!accepted) candidate.closeLayer(); }
    }

    @Override
    public synchronized IContextFederation.SourceDependencyPlan
            prepareSourceDependencies(
                    List<IContextFederation.SourceDependencyRequest> requests)
            throws Exception {
        requireWritable();
        requireOpen();
        if (requests == null) {
            throw new NullPointerException("requests");
        }

        RevisionRef sourceRef =
                new RevisionRef(
                        getContextId(),
                        getRevision());
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
                    !request.getInitialization().isEmpty()
                            ? configuredConnection(targetLocation, request.isExact() ? request.getExactRevision() : -1L,
                                    request.getInitialization())
                    : request.isExact()
                            ? ConnectionManager.qualifyConnect(
                                    activeLocation(),
                                    targetLocation,
                                    request.getExactRevision())
                            : ConnectionManager.qualifyConnect(
                                    activeLocation(),
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
                        activeLocation(),
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
        requireWritable();
        requireOpen();
        if (!(plan instanceof PreparedSourceDependencyPlan)) {
            throw new IllegalArgumentException(
                    "Source dependency plan does not belong to DUMB2");
        }
        PreparedSourceDependencyPlan prepared =
                (PreparedSourceDependencyPlan) plan;
        RevisionRef sourceRef =
                new RevisionRef(
                        getContextId(),
                        getRevision());

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
                        dependency.getInitialization().isEmpty()
                                ? ConnectionManager.qualifyConnect(activeLocation(), targetLocation, dependency.getRevision())
                                : configuredConnection(targetLocation, dependency.getRevision(), dependency.getInitialization());
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
                        activeLocation(),
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

        retiredLayers.addAll(workingConnections.getConnections());
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
                        .getRevision(), connection.getInitialization());
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

        Path sourceLocation = activeLocation();
        ContextSnapshot source =
                ContextSnapshot.open(sourceLocation,getRevision());
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
            ArrayList<IContextFederation.Connection> published = new ArrayList<IContextFederation.Connection>();
            for (ContextConnection c:publishedConnections().getConnections()) {
                published.add(new IContextFederation.Connection(displayFederationLocator(c.getTargetLocation()),
                        c.getTarget().getContextId(),c.getTarget().getRevision(),c.getTarget().getRevision(),
                        IContextFederation.PinPolicy.EXACT_REVISION,
                        c.getCertificate().matches(sourceRef,c.getTarget(),Version.CORE_VERSION_S)
                                ?IContextFederation.CompatibilityStatus.QUALIFIED:IContextFederation.CompatibilityStatus.STALE,
                        c.getCertificate().getSemanticVersion(), c.getInitialization()));
            }
            ArrayList<IContextFederation.DependencyNotice> notices = new ArrayList<IContextFederation.DependencyNotice>();
            for(ContextConnection owner:workingConnections.getConnections()) {
                ConnectionVector declared=ConnectionStore.read(owner.getTargetLocation(),owner.getTarget());
                for(ContextConnection dependency:declared.getConnections()) {
                    ContextConnection actual=workingConnections.find(dependency.getTarget().getContextId());
                    Long revision=dependency.getTarget().getContextId().equals(sourceRef.getContextId())
                            ?Long.valueOf(sourceRef.getRevision()):actual==null?null:Long.valueOf(actual.getTarget().getRevision());
                    notices.add(new IContextFederation.DependencyNotice(projectSourceDependency(owner),
                            projectSourceDependency(dependency),revision));
                }
            }
            return new IContextFederation.Snapshot(
                    sourceLocator(),
                    sourceRef.getContextId(),
                    sourceRef.getRevision(),
                    connections, published, notices);
        } finally {
            source.close();
        }
    }

    @Override
    public synchronized IContextFederation.Revision forkContext(IMind source, String locator) throws Exception {
        requireOpen();
        if (!(source instanceof Mind) || source.getUser() != user || user.getCurrentMind() != source)
            throw new CommandErrorException("Fork requires the current canonical Context");
        ((Mind) source).requirePublicationQuiescence();
        if (source.getTransactionLevel() != 0)
            throw new CommandErrorException("Fork requires U0; commit or publish open transactions first");
        if (!workingConnections.equals(publishedConnections()))
            throw new CommandErrorException("Fork requires saved connections; publish the Context first");
        if (locator == null || locator.trim().isEmpty() || locator.contains("@")
                || locator.contains("/") || locator.contains("\\") || locator.equals(".") || locator.equals(".."))
            throw new CommandErrorException("Fork target must be a new Context name without a revision or path");
        Path target = location(locator);
        if (storageArtifactsExist(target))
            throw new CommandErrorException("Fork target already exists: " + locator);
        try (ContextSnapshot snapshot = ContextSnapshot.open(activeLocation(), getRevision());
             ContextStore fork = snapshot.fork(target)) {
            return new IContextFederation.Revision(fork.getContextId(), fork.getRevision());
        }
    }

    @Override
    public synchronized IMind publishContext(IMind source, String description) throws Exception {
        requireWritable();
        requireOpen();
        if (!(source instanceof Mind) || source.getUser() != user || user.getCurrentMind() != source) {
            throw new CommandErrorException("Publication requires the current canonical Context");
        }
        ((Mind) source).requirePublicationQuiescence();
        description = org.kanger.RevisionDescriptions.validate(description);
        Path workspace = Files.createTempDirectory(activeLocation().toAbsolutePath().getParent(), ".publication-");
        Path candidateLocation = workspace.resolve("candidate");
        DB candidateData = null;
        User candidateUser = null;
        boolean accepted = false;
        Exception primaryFailure = null;
        try {
            try (ContextSnapshot baseline = ContextSnapshot.open(activeLocation(), getRevision());
                 ContextStore fork = ContextStore.forkForPublication(baseline, candidateLocation)) {
                // A private physical copy retains native IDs and local closure; no target is mutated.
            }
            candidateUser = new User();
            candidateUser.setDatabaseDir(workspace.toString() + java.io.File.separator);
            candidateData = new DB();
            candidateData.init(candidateUser);
            Mind root = new Mind(candidateUser);
            candidateUser.setCurrentMind(root);
            root = (Mind) root.useStorage("candidate");
            candidateUser.setCurrentMind(root);
            Mind prepared = Mind.preparePublicationStack((Mind) source, root);
            candidateUser.setCurrentMind(prepared);
            if (candidateData.getRevision() == 0L) {
                candidateData.context.publishGeneration(null, ConnectionVector.empty(), "Prepared publication");
            }
            try (ContextSnapshot candidate = ContextSnapshot.open(candidateLocation, candidateData.getRevision())) {
                for (org.kanger.storage.dumb2.descriptor.TypeDefinition definition
                        : candidate.snapshotTypeRegistry().definitions()) {
                    org.kanger.storage.dumb2.descriptor.TypeDefinition actual = context.registerType(
                            definition.getTypeName(), definition.getDescriptor());
                    if (actual.getTypeCode() != definition.getTypeCode()) {
                        throw new CommandErrorException("Publication candidate type mapping changed");
                    }
                }
                context.publishGeneration(candidate.getGeneration(), workingConnections, description);
            }
            accepted = true;
            retiredLayers.addAll(workingConnections.getConnections());
            workingConnections = publishedConnections();
            return ((User) user).reloadAfterContextPublication(source);
        } catch (Exception failure) {
            primaryFailure = accepted ? new org.kanger.exception.TransactionSettlementException(
                    org.kanger.exception.TransactionSettlementException.Outcome.COMMITTED, failure) : failure;
            throw primaryFailure;
        } finally {
            Exception cleanupFailure = null;
            try { if (candidateData != null && !candidateData.isClosed()) candidateData.close(); }
            catch (Exception failure) { cleanupFailure = failure; }
            try { ContextStore.deleteRecursively(workspace); }
            catch (Exception failure) {
                if (cleanupFailure == null) cleanupFailure = failure; else cleanupFailure.addSuppressed(failure);
            }
            if (cleanupFailure != null) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                else if (accepted) throw new org.kanger.exception.TransactionSettlementException(
                        org.kanger.exception.TransactionSettlementException.Outcome.COMMITTED, cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    @Override
    public synchronized void setNextRevisionDescription(String description) throws Exception {
        nextRevisionDescription = org.kanger.RevisionDescriptions.validate(description);
    }

    private static final class TopologyCheckpoint {
        final java.util.UUID sourceId;
        final ConnectionVector vector;
        TopologyCheckpoint(java.util.UUID sourceId, ConnectionVector vector) {
            this.sourceId = sourceId;
            this.vector = vector;
        }
    }

    @Override
    public synchronized Object checkpointConnections() throws Exception {
        requireOpen();
        return new TopologyCheckpoint(getContextId(), workingConnections);
    }

    @Override
    public synchronized void restoreConnections(Object checkpoint) throws Exception {
        requireOpen();
        TopologyCheckpoint saved = (TopologyCheckpoint) checkpoint;
        if (!getContextId().equals(saved.sourceId)) {
            throw new IllegalStateException("Cannot restore topology in another Context");
        }
        retiredLayers.addAll(workingConnections.getConnections());
        workingConnections = requalifyWorkingConnections(saved.vector);
    }

    private ConnectionVector requalifyWorkingConnections(ConnectionVector vector) throws Exception {
        ConnectionVector qualified = ConnectionVector.empty();
        RevisionRef source = new RevisionRef(getContextId(), getRevision());
        for (ContextConnection connection : vector.getConnections()) {
            ContextConnection current = connection;
            if (!connection.getCertificate().matches(source, connection.getTarget(), Version.CORE_VERSION_S)) {
                if (connection.getInitialization().isEmpty()) {
                    current = ConnectionManager.qualifyConnect(activeLocation(), connection.getTargetLocation(), connection.getTarget().getRevision());
                } else {
                    PairQualification.Result pair = PairQualification.qualify(activeLocation(), getRevision(), connection);
                    if (!pair.isCompatible()) throw new CommandErrorException("Configured connection is incompatible with X");
                    current = connection.recertified(pair.getCertificate());
                }
                if (!connection.getTarget().equals(current.getTarget())) {
                    throw new StorageLifecycleException(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                            "Pinned Context identity changed during topology restoration: " + connection.getTarget());
                }
            }
            if (current != connection) current = connection.recertified(current.getCertificate());
            qualified = qualified.with(current);
        }
        return qualified;
    }

    @Override
    public synchronized long saveConnections(IMind source) throws Exception {
        requireWritable();
        requireOpen();
        if(source==null || source.getUser()!=user || source!=user.getCurrentMind()
                || source.getTransactionLevel()!=0)
            throw new CommandErrorException("Context save requires the current settled root");
        // Local DUMB2 uses the same exclusively opened writable Context as
        // ordinary authoring. SMART ownership/ACL publication remains separate.
        long revision=context.publishTopology(workingConnections,"Save explicit Context connections");
        retiredLayers.addAll(workingConnections.getConnections());
        workingConnections =publishedConnections();
        return revision;
    }

    @Override
    public synchronized List<IContextFederation.RuleBlock> inspectRules(IMind source,String locator,
            IContextFederation.RuleSelection selection,Long number) throws Exception {
        requireOpen();
        if(!(source instanceof Mind) || source.getUser()!=user)
            throw new CommandErrorException("Context rules requires the active storage Mind");
        List<IContextFederation.RuleBlock> result=new ArrayList<IContextFederation.RuleBlock>();
        Path local=activeLocation().toAbsolutePath().normalize();
        boolean addressed=locator!=null;
        Path selected=addressed?resolveFederationLocator(locator):null;
        if(!addressed || local.equals(selected)) {
            result.add(org.kanger.ContextRuleInspection.inspect((Mind)source,sourceLocator(),
                    new IContextFederation.Revision(getContextId(),getRevision()),!isReadOnly(),selection,number));
        }
        if(number!=null && !addressed) return Collections.unmodifiableList(result);
        for(ContextConnection c:workingConnections.getConnections()) {
            if(addressed && !c.getTargetLocation().toAbsolutePath().normalize().equals(selected)) continue;
            IContextFederation.RuleBlock block = org.kanger.ContextRuleInspection.inspect(c.layer(),
                    displayFederationLocator(c.getTargetLocation()), projectRevision(c.getTarget()), false, selection, number);
            result.add(new IContextFederation.RuleBlock(block.locator, block.revision, block.working, block.rules,
                    !c.getInitialization().isEmpty()));
        }
        if(result.isEmpty()) throw new CommandErrorException("No direct Context connection exists for locator " + locator);
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized IContextFederation.VersionHistory versionHistory(
            String targetLocator) throws Exception {
        boolean addressed = targetLocator != null && !targetLocator.trim().isEmpty();
        if (!addressed) requireOpen();
        Path sourceLocation = isClosed() ? null : activeLocation().toAbsolutePath().normalize();
        Path selectedLocation = addressed ? resolveFederationLocator(targetLocator) : sourceLocation;
        String selectedLocator = displayFederationLocator(selectedLocation);
        long pinnedRevision = -1L;
        if (selectedLocation.equals(sourceLocation)) {
            selectedLocator = sourceLocator();
            pinnedRevision = historical == null ? -1L : getRevision();
        } else if (!isClosed()) {
            for (ContextConnection connection : workingConnections.getConnections()) {
                if (connection.getTargetLocation().toAbsolutePath().normalize().equals(selectedLocation)) {
                    pinnedRevision = connection.getTarget().getRevision();
                    break;
                }
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
        requireWritable();
        requireOpen();
        Path location = resolveFederationLocator(targetLocator);
        ContextConnection previous = null;
        for (ContextConnection existing : workingConnections.getConnections())
            if (existing.getTargetLocation().equals(location.toAbsolutePath().normalize())) previous = existing;
        ContextConnection connection;
        if (previous != null && !previous.getInitialization().isEmpty()) {
            ContextSnapshot current = ContextSnapshot.open(location);
            long revision;
            try {
                if (!previous.getTarget().getContextId().equals(current.getContextId()))
                    throw new CommandErrorException("Connected Context identity changed");
                revision = current.getRevision();
            } finally { current.close(); }
            connection = ConnectionManager.qualifySwitchRevision(activeLocation(), workingConnections,
                    previous.getTarget().getContextId(), revision);
        } else {
            connection = ConnectionManager.qualifyConnect(activeLocation(), location);
        }
        ConnectionVector candidate =
                workingConnections.with(connection);
        PairQualification.CompositionQualification before =
                PairQualification.qualifyCompositionState(
                        activeLocation(),
                        getRevision(),
                        workingConnections);
        PairQualification.CompositionQualification after =
                PairQualification.qualifyCompositionState(
                        activeLocation(),
                        getRevision(),
                        candidate);
        if (after.introducesNewCollisionComparedTo(before)) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                    "Context connection introduces a new X-anchored composition conflict: "
                            + connection.getTarget(),
                    after.introducedCollisionsComparedTo(before));
        }
        retiredLayers.addAll(workingConnections.getConnections());
        workingConnections = candidate;
        ((User) user).getContextOpinionSession().invalidate();
        return projectConnection(
                new RevisionRef(
                        getContextId(),
                        getRevision()),
                connection);
    }

    @Override
    public synchronized void disconnectContext(
            String targetLocator) throws Exception {
        requireWritable();
        disconnectContext(
                connectedContextId(targetLocator));
    }

    @Override
    public synchronized void disconnectContext(
            java.util.UUID targetContextId) throws Exception {
        requireWritable();
        requireOpen();
        retiredLayers.addAll(workingConnections.getConnections());
        workingConnections =
                workingConnections.without(targetContextId);
        ((User) user).getContextOpinionSession().invalidate();
    }

    @Override
    public synchronized IContextFederation.Connection switchContextRevision(
            String targetLocator,
            long targetRevision) throws Exception {
        requireWritable();
        return switchContextRevision(
                connectedContextId(targetLocator),
                targetRevision);
    }

    @Override
    public synchronized IContextFederation.Connection switchContextRevision(
            java.util.UUID targetContextId,
            long targetRevision) throws Exception {
        requireWritable();
        requireOpen();
        ContextConnection connection =
                ConnectionManager.qualifySwitchRevision(
                        activeLocation(),
                        workingConnections,
                        targetContextId,
                        targetRevision);
        retiredLayers.addAll(workingConnections.getConnections());
        workingConnections =
                workingConnections.with(connection);
        ((User) user).getContextOpinionSession().invalidate();
        return projectConnection(
                new RevisionRef(
                        getContextId(),
                        getRevision()),
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
                certificate.getSemanticVersion(), connection.getInitialization());
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
                        activeLocation(),
                        workingConnections,
                        getRevision(),
                        querySource,
                        externals,
                        logging);
        return projectQueryResult(result);
    }

    @Override
    public synchronized Map<String, IContextFederation.Opinion> executeOpinions(IMind sourceMind,
            String locator, String querySource, List<org.kanger.SemanticTermSnapshot> parameters) throws Exception {
        requireOpen();
        if (!(sourceMind instanceof Mind) || sourceMind.getUser() != user || user.getCurrentMind() != sourceMind)
            throw new CommandErrorException("Context opinions requires the active storage Mind");
        if (querySource == null || querySource.length() < 2 || querySource.charAt(0) != '?')
            throw new CommandErrorException("Context opinions requires a full original query");
        Path selected = locator == null ? null : resolveFederationLocator(locator);
        Map<String, IContextFederation.Opinion> result = new LinkedHashMap<>();
        boolean matched = false;
        if (selected == null || activeLocation().toAbsolutePath().normalize().equals(selected)) {
            matched = true;
            IContextFederation.Opinion opinion = localOpinion((Mind) sourceMind, sourceLocator(),
                    new RevisionRef(getContextId(), getRevision()), !isReadOnly(), querySource, parameters);
            if (opinion.isMeaningful()) result.put(opinion.getLocator(), opinion);
        }
        for (ContextConnection connection : workingConnections.getConnections()) {
            if (selected != null && !connection.getTargetLocation().toAbsolutePath().normalize().equals(selected)) continue;
            matched = true;
            IContextFederation.Opinion opinion = localOpinion(connection.layer(),
                    displayFederationLocator(connection.getTargetLocation()), connection.getTarget(), false,
                    querySource, parameters, !connection.getInitialization().isEmpty());
            if (opinion.isMeaningful()) result.put(opinion.getLocator(), opinion);
        }
        if (!matched) throw new CommandErrorException("No direct Context connection exists for locator " + locator);
        return Collections.unmodifiableMap(result);
    }

    private IContextFederation.Opinion localOpinion(Mind root, String locator, RevisionRef ref,
            boolean working, String query, List<org.kanger.SemanticTermSnapshot> parameters) throws Exception {
        return localOpinion(root, locator, ref, working, query, parameters, false);
    }

    private IContextFederation.Opinion localOpinion(Mind root, String locator, RevisionRef ref,
            boolean working, String query, List<org.kanger.SemanticTermSnapshot> parameters, boolean configured) throws Exception {
        Mind work = Mind.ephemeralChild(root);
        work.clearContextProofs();
        try {
            Queue<ITerm> externals = new LinkedList<>();
            for (org.kanger.SemanticTermSnapshot parameter : parameters)
                externals.add(work.getTerms().projectSemantic(parameter.materialize()));
            IContextFederation.QueryResult answer = projectLocalQuery(work, ref, query, externals, true);
            List<IContextFederation.RuleRow> solutions = new ArrayList<>();
            if (answer.isResolved()) for (org.kanger.interfaces.IRule solution : work.getSolutions())
                solutions.add(org.kanger.ContextProofProjection.solution(work, solution, projectRevision(ref), query));
            return new IContextFederation.Opinion(locator, projectRevision(ref), working, answer, solutions, configured);
        } finally { root.discardEphemeral(work); }
    }

    @Override
    public synchronized void applyConnectionCommand(IMind sourceMind, String locator, String command) throws Exception {
        requireWritable(); requireOpen();
        if (sourceMind != user.getCurrentMind() || sourceMind.getUser() != user)
            throw new CommandErrorException("Connection editing requires the current X Mind");
        if (command == null || command.isEmpty() || "!+-".indexOf(command.charAt(0)) < 0)
            throw new CommandErrorException("Only assertion/addition/deletion commands are allowed in a connection layer");
        org.kanger.compiler.Token token = org.kanger.enums.Tools.extractLine(command, null);
        if (token == null || org.kanger.enums.Tools.extractLine(command, token) != null)
            throw new CommandErrorException("Connection editing requires one complete command");
        ContextConnection original = connectedContext(resolveFederationLocator(locator), locator);
        java.util.List<String> commands = new java.util.ArrayList<String>(original.getInitialization());
        if (commands.size() >= 4096) throw new CommandErrorException("Too many connection initialization commands");
        commands.add(command);
        ContextConnection candidate = new ContextConnection(original.getTargetLocation(), original.getTarget(),
                original.getCertificate(), commands);
        boolean accepted = false;
        try {
            candidate.extendLayer(original, command);
            ConnectionVector proposed = workingConnections.with(candidate);
            validateEffectiveConnections((Mind) sourceMind, proposed);
            PairQualification.CompositionQualification before = PairQualification.qualifyCompositionState(
                    activeLocation(), getRevision(), workingConnections);
            PairQualification.CompositionQualification after = PairQualification.qualifyCompositionState(
                    activeLocation(), getRevision(), proposed);
            if (after.introducesNewCollisionComparedTo(before))
                throw new CommandErrorException("Context layer introduces a new composition conflict");
            retiredLayers.add(original);
            retiredLayers.addAll(workingConnections.getConnections());
            workingConnections = proposed;
            ((User) user).getContextOpinionSession().invalidate();
            accepted = true;
        } finally { if (!accepted) candidate.closeLayer(); }
    }

    @Override
    public synchronized IContextFederation.QueryResult executeIsolatedQuery(
            IMind sourceMind,
            String targetLocator,
            String querySource) throws Exception {
        requireOpen();
        ((User) user).getContextOpinionSession().invalidate();
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
                activeLocation()
                        .toAbsolutePath().normalize();
        RevisionRef sourceRef =
                new RevisionRef(
                        getContextId(),
                        getRevision());

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
        Mind root = connection.layer();
        Mind work = Mind.ephemeralChild(root);
        try { return projectLocalQuery(work, targetRef, querySource); }
        finally { root.discardEphemeral(work); }
    }

    private IContextFederation.QueryResult projectLocalQuery(
            Mind mind,
            RevisionRef source,
            String querySource) throws Exception {
        return projectLocalQuery(mind, source, querySource, new LinkedList<ITerm>());
    }

    private IContextFederation.QueryResult projectLocalQuery(Mind mind, RevisionRef source,
            String querySource, Queue<ITerm> externals) throws Exception {
        return projectLocalQuery(mind, source, querySource, externals, false);
    }

    private IContextFederation.QueryResult projectLocalQuery(Mind mind, RevisionRef source,
            String querySource, Queue<ITerm> externals, boolean optimizeHypotheses) throws Exception {
        Boolean answer = mind.queryCanonical(querySource, externals, false);
        if (answer == null && optimizeHypotheses && !mind.getHypothesis().isEmpty())
            mind.optimizeHypothesis();

        ArrayList<IContextFederation.ValueRow> values =
                new ArrayList<IContextFederation.ValueRow>();
        if (answer != null) for (Map<String, ITerm> row : mind.getValues()) {
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
                            ((Hypothesis) hypothesis).toAssertionString(mind)));
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
                        activeLocation(),
                        workingConnections,
                        getRevision(),
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
                hypotheses,
                result.getCausalSteps());
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
        if (historical!=null && locator.trim().equals(storageName)) return activeLocation().toAbsolutePath().normalize();
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
                getContextId(),
                getRevision());
        return ConnectionStore.read(
                activeLocation(), sourceRef);
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
