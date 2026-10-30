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
    private CommuneRuntimeCache communeCache = new CommuneRuntimeCache();
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
            try {
                activateConnections(publishedConnections());
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
        try {
            activateConnections(publishedConnections());
            for (ContextConnection connection : workingConnections.getConnections())
                if (!connection.getInitialization().isEmpty()) connection.layer();
        } catch (Exception failure) { close(); throw failure; }
    }

    private final java.util.Set<ContextConnection> retiredLayers =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ContextConnection, Boolean>());
    private void activateConnections(ConnectionVector candidate) throws Exception {
        candidate.prepareCommunes(communeCache);
        workingConnections = candidate;
    }

    private void closeCommuneCache() throws Exception {
        communeCache.close();
        communeCache = new CommuneRuntimeCache();
    }

    private void closeConnectionLayers() throws Exception {
        retiredLayers.addAll(workingConnections.getConnections());
        for (ContextConnection connection : retiredLayers) connection.closeLayer();
        retiredLayers.clear();
    }

    @Override
    public synchronized void collectConnectionState() throws Exception {
        requireOpen();
        // Mind calls this only at root quiescence. Nested rollback checkpoints
        // therefore retain every layer until the last child has settled.
        java.util.Set<ContextConnection> active = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<ContextConnection, Boolean>());
        active.addAll(workingConnections.getConnections());
        active.addAll(workingConnections.retainCommunes(communeCache));
        java.util.Set<ConnectionRuntime> live = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<ConnectionRuntime, Boolean>());
        for (ContextConnection connection : active)
            if (connection.runtime() != null) live.add(connection.runtime());
        java.util.Iterator<ContextConnection> iterator = retiredLayers.iterator();
        while (iterator.hasNext()) {
            ContextConnection connection = iterator.next();
            if (!active.contains(connection)) connection.retireLayer(live);
            iterator.remove();
        }
    }

    private void collectAfterTopologyChange() throws Exception {
        IMind current = user.getCurrentMind();
        if (!(current instanceof Mind) || current.getTransactionLevel() != 0) return;
        try {
            // U0 can still own hidden children. Use the existing reservation
            // guard rather than inferring quiescence from the visible level.
            ((Mind) current).requirePublicationQuiescence();
        } catch (IllegalStateException liveChildren) {
            return;
        }
        collectConnectionState();
    }

    private void collectAfterRejectedTopologyChange(Exception rejection) {
        try {
            collectAfterTopologyChange();
        } catch (Exception cleanup) {
            rejection.addSuppressed(cleanup);
        }
    }

    @Override
    public synchronized void close() throws Exception {
        closeConnectionLayers();
        if (historical!=null) {
            for (IBase base : bases.values()) if (base instanceof SnapshotRuntimeBase) ((SnapshotRuntimeBase)base).close();
            historical.close(); historical=null; historicalLocation=null;
            closeCommuneCache();
            bases.clear(); activateConnections(ConnectionVector.empty()); storageName="";
            return;
        }
        if (context == null) {
            closeCommuneCache();
            bases.clear();
            activateConnections(ConnectionVector.empty());
            storageName = "";
            return;
        }

        /*
         * ContextStore.close() keeps the Context open if flush fails. Mirror
         * that failure atomicity here: only clear the IData generation after
         * the physical owner has actually closed.
         */
        context.close();
        closeCommuneCache();
        bases.clear();
        activateConnections(ConnectionVector.empty());
        context = null;
        storageName = "";
    }

    /** Historical diagnostic on one freshly acquired complete effective family. */
    public synchronized org.kanger.ContextProductionCandidate.Evaluation qualifyProductionCandidate(
            Mind source,String statement,int rows,int rounds,int depth,int work,int events) throws Exception {
        try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(this,source,rows)) {
            return lease.qualifyCandidate(statement,rounds,depth,work,events);
        }
    }

    /** Read-only scoped ground candidate diagnostic from fresh owned views; never permission to publish. */
    public synchronized org.kanger.ContextGroundClauseCandidate.Evaluation qualifyGroundProductionCandidate(
            Mind source,String statement,int rows,int rounds,int localRounds,int depth,int work,int events,int probes,int models) throws Exception {
        return qualifyGroundProductionCandidate(source,statement,rows,rounds,localRounds,depth,work,events,probes,models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    public synchronized org.kanger.ContextGroundClauseCandidate.Evaluation qualifyGroundProductionCandidate(
            Mind source,String statement,int rows,int rounds,int localRounds,int depth,int work,int events,int probes,int models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) throws Exception {
        if(limits==null) throw new IllegalArgumentException("Ledger limits required");
        try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(this,source,rows)) {
            return lease.qualifyGroundCandidate(statement,rounds,localRounds,depth,work,events,probes,models,limits);
        }
    }

    /** Read-only flat universal diagnostic from exact current pinned initialized views. No write authority. */
    public synchronized org.kanger.ContextUniversalCandidate.Evaluation qualifyUniversalProductionCandidate(
            Mind source,String statement,int rows,int rounds,int localRounds,int depth,int instances,int models,
            int preparationWork,int preparationEvents,int inspectionWork) throws Exception {
        try (ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(this,source,rows)) {
            return lease.qualifyUniversalCandidate(statement,rounds,localRounds,depth,instances,models,preparationWork,preparationEvents,inspectionWork);
        }
    }

    @Override public synchronized org.kanger.ContextUniversalCandidateAudit.Audit inspectUniversalCandidate(
            org.kanger.interfaces.IMind source,org.kanger.ContextUniversalCandidateRequest request) throws Exception {
        if(!(source instanceof Mind) || request==null) throw new IllegalArgumentException("Actual candidate source/request required");
        try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(this,(Mind)source,request.limit("rows"))) {
            return lease.inspectUniversalCandidate(request);
        }
    }

    public enum ProductionWriteStatus { PUBLISHED, COLLISION, INCOMPLETE, NATIVE_REJECTED }
    /** Trial Sources retain their staged identity; before/after address actual physical revisions. */
    public static final class ProductionWriteResult {
        public final ProductionWriteStatus status;
        public final org.kanger.ContextProductionCandidate.Evaluation trial;
        public final RevisionRef before,after;
        private ProductionWriteResult(ContextProductionSnapshotSession.WriteResult result) {
            status = ProductionWriteStatus.valueOf(result.status.name()); trial = result.trial;
            before = result.before; after = result.after;
        }
    }
    /** Explicit single-assertion authoring. INCOMPLETE never falls back to unqualified writing. */
    public synchronized ProductionWriteResult writeProductionCandidate(
            Mind source,String statement,int rows,int rounds,int depth,int work,int events,boolean logging) throws Exception {
        requireInitialized(); requireWritable();
        try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(this,source,rows)) {
            return new ProductionWriteResult(lease.writeCandidate(statement,rounds,depth,work,events,logging));
        }
    }

    /** Scoped ground trial and actual physical settlement references; never a reusable write capability. */
    public static final class GroundProductionWriteResult {
        public final ProductionWriteStatus status;
        public final org.kanger.ContextGroundClauseCandidate.Evaluation trial;
        public final RevisionRef before,after;
        private GroundProductionWriteResult(ContextProductionSnapshotSession.GroundWriteResult result) {
            status = ProductionWriteStatus.valueOf(result.status.name()); trial = result.trial;
            before = result.before; after = result.after;
        }
    }
    /** Explicit ground-only authoring; every call reacquires and requalifies, with no incomplete fallback. */
    public synchronized GroundProductionWriteResult writeGroundProductionCandidate(
            Mind source,String statement,int rows,int rounds,int localRounds,int depth,int work,int events,int probes,int models,boolean logging) throws Exception {
        return writeGroundProductionCandidate(source,statement,rows,rounds,localRounds,depth,work,events,probes,models,logging,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    public synchronized GroundProductionWriteResult writeGroundProductionCandidate(
            Mind source,String statement,int rows,int rounds,int localRounds,int depth,int work,int events,int probes,int models,boolean logging,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) throws Exception {
        if(limits==null) throw new IllegalArgumentException("Ledger limits required");
        requireInitialized(); requireWritable();
        try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(this,source,rows)) {
            return new GroundProductionWriteResult(lease.writeGroundCandidate(statement,rounds,localRounds,depth,work,events,probes,models,logging,limits));
        }
    }

    /** Fresh scoped universal trial/audit plus actual physical settlement references. */
    public static final class UniversalProductionWriteResult {
        public final ProductionWriteStatus status;
        public final org.kanger.ContextUniversalCandidate.Evaluation trial;
        public final org.kanger.ContextUniversalCandidateAudit.Audit audit;
        public final RevisionRef before,after;
        private UniversalProductionWriteResult(ContextProductionSnapshotSession.UniversalWriteResult result) {
            status=ProductionWriteStatus.valueOf(result.status.name()); trial=result.trial; audit=result.audit;
            before=result.before; after=result.after;
        }
    }
    /** Explicit flat universal writer; no historical receipt input or incomplete fallback. */
    public synchronized UniversalProductionWriteResult writeUniversalProductionCandidate(
            Mind source,String statement,int rows,int rounds,int localRounds,int depth,int instances,int models,
            int preparationWork,int preparationEvents,int inspectionWork,int auditWork,boolean logging) throws Exception {
        requireInitialized(); requireWritable();
        try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(this,source,rows)) {
            return new UniversalProductionWriteResult(lease.writeUniversalCandidate(statement,rounds,localRounds,depth,
                    instances,models,preparationWork,preparationEvents,inspectionWork,auditWork,logging));
        }
    }

    @Override public synchronized org.kanger.ContextUniversalCandidatePublication publishUniversalCandidate(
            org.kanger.interfaces.IMind source,org.kanger.ContextUniversalCandidateRequest request) throws Exception {
        if(!(source instanceof Mind) || request==null) throw new IllegalArgumentException("Actual candidate source/request required");
        UniversalProductionWriteResult written=writeUniversalProductionCandidate((Mind)source,request.statement,request.limit("rows"),
                request.limit("rounds"),request.limit("local"),request.limit("depth"),request.limit("instances"),request.limit("models"),
                request.limit("preparation"),request.limit("events"),request.limit("work"),request.limit("audit"),false);
        return new org.kanger.ContextUniversalCandidatePublication(org.kanger.ContextUniversalCandidatePublication.Status.valueOf(written.status.name()),
                written.trial,written.audit,new org.kanger.interfaces.IContextResults.Revision(written.before.getContextId(),written.before.getRevision()),
                new org.kanger.interfaces.IContextResults.Revision(written.after.getContextId(),written.after.getRevision()));
    }

    /** Explicit one-assertion native/full-family writer. No historical verdict or default hook switch.
     * Returns the ordinary native acceptance boolean; typed qualification failures propagate. */
    public synchronized boolean writeNativeProductionCandidate(Mind source,String statement,org.kanger.ContextNativeCandidateInspection.Limits limits,int familyWork,int familyAudit,int models,boolean logging) throws Exception {
        requireInitialized();requireWritable();
        if(statement==null||!statement.startsWith("!")||limits==null||familyWork<1||familyAudit<1||models<1)throw new IllegalArgumentException("One assertion and explicit native/full-family bounds required");
        org.kanger.compiler.Token token=org.kanger.enums.Tools.extractLine(statement,null);
        if(token==null||org.kanger.enums.Tools.extractLine(statement,token)!=null)throw new IllegalArgumentException("Exactly one assertion required");
        ContextPendingNativeWrite attempt=beginNativeProductionWrite(source,limits,familyWork,familyAudit,models);
        try{return Boolean.TRUE.equals(source.query(statement,null,logging));}finally{endNativeWrite(attempt);}
    }
    synchronized ContextPendingNativeWrite beginNativeProductionWrite(Mind source,org.kanger.ContextNativeCandidateInspection.Limits limits,int familyWork,int familyAudit,int models) throws Exception {
        if(pendingNativeWrite!=null)throw new IllegalStateException("Nested native write attempt");
        if(limits==null||familyWork<1||familyAudit<1||models<1)throw new IllegalArgumentException("Explicit native/full-family bounds required");
        return pendingNativeWrite=new ContextPendingBranchWrite(this,source,limits,familyWork,familyAudit,models);
    }

    private ContextPendingNativeWrite pendingNativeWrite;
    synchronized ContextPendingWitnessWrite beginWitnessWrite(Mind source,int rows,int rounds,int depth,int work,int events) throws Exception {
        if(pendingNativeWrite!=null) throw new IllegalStateException("Nested native write attempt");
        ContextPendingWitnessWrite attempt=new ContextPendingWitnessWrite(this,source,rows,rounds,depth,work,events);
        pendingNativeWrite=attempt;return attempt;
    }
    synchronized void endWitnessWrite(ContextPendingWitnessWrite attempt) {endNativeWrite(attempt);}
    synchronized ContextPendingNativeWrite beginGroundWrite(Mind source,int rows,int rounds,int local,int depth,int work,int events,int probes,int models) throws Exception {
        return beginGroundWrite(source,rows,rounds,local,depth,work,events,probes,models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    synchronized ContextPendingNativeWrite beginGroundWrite(Mind source,int rows,int rounds,int local,int depth,int work,int events,int probes,int models,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) throws Exception {
        if(limits==null) throw new IllegalArgumentException("Ledger limits required");
        if(pendingNativeWrite!=null) throw new IllegalStateException("Nested native write attempt");
        return pendingNativeWrite=ContextPendingClauseWrite.ground(this,source,rows,rounds,local,depth,work,events,probes,models,limits);
    }
    synchronized ContextPendingNativeWrite beginUniversalWrite(Mind source,int rows,int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int audit) throws Exception {
        if(pendingNativeWrite!=null) throw new IllegalStateException("Nested native write attempt");
        return pendingNativeWrite=ContextPendingClauseWrite.universal(this,source,rows,rounds,local,depth,instances,models,preparation,events,work,audit);
    }
    synchronized void endNativeWrite(ContextPendingNativeWrite attempt) {
        if(pendingNativeWrite!=attempt) throw new IllegalStateException("Wrong native write attempt");
        try {attempt.close();} finally {pendingNativeWrite=null;}
    }
    synchronized boolean writeWitnessNative(Mind source,String statement,int rows,int rounds,int depth,int work,int events,boolean logging) throws Exception {
        ContextPendingWitnessWrite attempt=beginWitnessWrite(source,rows,rounds,depth,work,events);
        try {return Boolean.TRUE.equals(source.query(statement,null,logging));}
        finally {endWitnessWrite(attempt);}
    }

    synchronized boolean writeGroundNative(Mind source,String statement,int rows,int rounds,int local,int depth,int work,int events,int probes,int models,boolean logging) throws Exception {
        return writeGroundNative(source,statement,rows,rounds,local,depth,work,events,probes,models,logging,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    synchronized boolean writeGroundNative(Mind source,String statement,int rows,int rounds,int local,int depth,int work,int events,int probes,int models,boolean logging,org.kanger.ContextOrdinaryResidualRounds.LedgerLimits limits) throws Exception {
        if(limits==null) throw new IllegalArgumentException("Ledger limits required");
        ContextPendingNativeWrite attempt=beginGroundWrite(source,rows,rounds,local,depth,work,events,probes,models,limits);
        try {return Boolean.TRUE.equals(source.query(statement,null,logging));} finally {endNativeWrite(attempt);}
    }
    synchronized boolean writeUniversalNative(Mind source,String statement,int rows,int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int audit,boolean logging) throws Exception {
        ContextPendingNativeWrite attempt=beginUniversalWrite(source,rows,rounds,local,depth,instances,models,preparation,events,work,audit);
        try {return Boolean.TRUE.equals(source.query(statement,null,logging));} finally {endNativeWrite(attempt);}
    }

    /** Strict pending-state decision adapter; the default validation hook is not redirected yet. */
    synchronized void validatePendingUniversalCommit(Mind proposed) throws Exception {
        java.util.Map<String,Integer> d=org.kanger.ContextUniversalCandidateRequest.DEFAULTS;
        validatePendingUniversalCommit(proposed,d.get("rows"),d.get("rounds"),d.get("local"),d.get("depth"),d.get("instances"),d.get("models"),d.get("preparation"),d.get("events"),d.get("work"),d.get("audit"));
    }
    synchronized void validatePendingUniversalCommit(Mind proposed,int rows,int rounds,int local,int depth,int instances,int models,
            int preparation,int events,int work,int auditWork) throws Exception {
        if(rows<1 || rows>10000 || rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || auditWork<1)
            throw new IllegalArgumentException("Bounded pending commit qualification required");
        if(pendingNativeWrite!=null) {pendingNativeWrite.validate(proposed);return;}
        ContextPendingProductionSnapshotSession.Result result;
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(this,proposed,rows)) {
            result=lease.inspect(rounds,local,depth,instances,models,preparation,events,work,auditWork);
            lease.requireCurrent();
        } catch(IllegalStateException unsupported) {
            org.kanger.exception.ContextProductionCommitException failure=org.kanger.exception.ContextProductionCommitException.incomplete("Proposed Context qualification incomplete: "+unsupported.getMessage(),null);
            failure.initCause(unsupported);throw failure;
        }
        requirePendingUniversalResult(result);
    }
    static void requirePendingUniversalResult(ContextPendingProductionSnapshotSession.Result result) throws Exception {
        if(!result.audit.verified || result.inspection.status==org.kanger.ContextUniversalFamilyInspection.Status.INCOMPLETE)
            throw org.kanger.exception.ContextProductionCommitException.incomplete("Proposed Context qualification incomplete: "+(result.inspection.status==org.kanger.ContextUniversalFamilyInspection.Status.INCOMPLETE?result.inspection.reason:result.audit.reason),result.audit);
        if(result.inspection.status==org.kanger.ContextUniversalFamilyInspection.Status.COLLISION)
            throw org.kanger.exception.ContextProductionCommitException.collision(result.audit);
        if(result.inspection.status!=org.kanger.ContextUniversalFamilyInspection.Status.CONSISTENT)
            throw org.kanger.exception.ContextProductionCommitException.incomplete("No complete proposed Context consistency verdict",result.audit);
    }

    /** Whole-family engine policy. Native default route remains unchanged until its compatibility gate. */
    synchronized void validatePendingProductionCommit(Mind proposed) throws Exception {
        java.util.Map<String,Integer> d=org.kanger.ContextUniversalCandidateRequest.DEFAULTS;
        validatePendingProductionCommit(proposed,d.get("rows"),d.get("rounds"),d.get("local"),d.get("depth"),d.get("instances"),d.get("models"),d.get("preparation"),d.get("events"),d.get("work"),d.get("audit"),100000);
    }
    synchronized void validatePendingProductionCommit(Mind proposed,int rows,int rounds,int local,int depth,int instances,int models,
            int preparation,int events,int work,int audit,int probes) throws Exception {
        if(rows<1 || rows>10000 || rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || audit<1 || probes<1)
            throw new IllegalArgumentException("Bounded whole-family policy required");
        if(pendingNativeWrite!=null) {pendingNativeWrite.validate(proposed);return;}
        ContextPendingProductionSnapshotSession.Decision decision;
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(this,proposed,rows)) {
            decision=lease.inspectAutomatic(rounds,local,depth,instances,models,preparation,events,work,audit,probes);
            lease.requireCurrent();
        } catch(IllegalStateException unsupported) {
            org.kanger.exception.ContextProductionCommitException failure=org.kanger.exception.ContextProductionCommitException.incomplete("Pending whole-family policy incomplete: "+unsupported.getMessage(),null);
            failure.initCause(unsupported);throw failure;
        }
        switch(decision.engine) {
            case UNIVERSAL: requirePendingUniversalResult(decision.universal);return;
            case GROUND:
                if(decision.ground.status!=org.kanger.ContextGroundClauseFamily.Status.GROUND_FIXED_POINT)
                    throw new org.kanger.exception.ContextGroundProductionCommitException("Pending ground family: "+decision.ground.reason,decision.ground);
                return;
            case WITNESS:
                if(decision.witness.status!=org.kanger.ContextProductionFamilyAssembly.Status.FIXED_POINT)
                    throw new org.kanger.exception.ContextWitnessProductionCommitException("Pending witness family: "+decision.witness.reason,decision.witness);
                return;
            default: throw new IllegalStateException("No complete pending engine decision");
        }
    }

    /** Fresh owner/currentness-guarded native diagnostic, never a writer/commit decision. */
    public synchronized org.kanger.ContextNativeCandidateInspection.Result inspectNativeProductionCandidate(Mind source,String statement,org.kanger.ContextNativeCandidateInspection.Limits limits) throws Exception {
        if(limits==null)throw new IllegalArgumentException("Explicit native diagnostic limits required");
        try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(this,source,limits.rows)) {
            return lease.inspectNativeCandidate(statement,limits);
        }
    }

    /** Fresh owner/currentness-guarded signed diagnostic; not a writer or commit decision. */
    public synchronized org.kanger.ContextSignedCandidateInspection.Result inspectSignedProductionCandidate(Mind source,String statement,org.kanger.ContextSignedCandidateInspection.Limits limits) throws Exception {
        if(limits==null)throw new IllegalArgumentException("Explicit signed diagnostic limits required");
        try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(this,source,limits.rows)) {
            return lease.inspectSignedCandidate(statement,limits);
        }
    }

    /** Read-only whole proposed-child diagnostic, guarded by actual pending/rollback ownership. */
    public synchronized org.kanger.ContextNativeBranchInspection.Result inspectNativePendingProduction(Mind proposed,org.kanger.ContextNativeCandidateInspection.Limits limits) throws Exception {
        if(limits==null)throw new IllegalArgumentException("Explicit native pending limits required");
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(this,proposed,limits.rows)) {
            return lease.inspectNative(limits);
        }
    }

    /** Fresh actual-child signed diagnostic only; no settlement or historical-result input. */
    public synchronized org.kanger.ContextSignedBranchInspection.Result inspectSignedPendingProduction(Mind proposed,org.kanger.ContextSignedCandidateInspection.Limits limits) throws Exception {
        if(limits==null)throw new IllegalArgumentException("Explicit signed pending limits required");
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(this,proposed,limits.rows)) {
            return lease.inspectSigned(limits);
        }
    }

    /** Explicit conservative opt-in guard: native refusal FIRST, existing complete family gate SECOND.
     * No historical result input; default validateCommit and existing writer selection stay unchanged. */
    synchronized void validatePendingNativeProductionCommit(Mind proposed,org.kanger.ContextNativeCandidateInspection.Limits limits,int familyWork,int familyAudit,int models) throws Exception {
        if(limits==null||familyWork<1||familyAudit<1||models<1)throw new IllegalArgumentException("Explicit bounded native/full-family gates required");
        String phase="pending-capture";
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(this,proposed,limits.rows)) {
            requireNativePendingFamily(lease,limits,familyWork,familyAudit,models);

        } catch(IllegalStateException unsupported) {
            org.kanger.exception.ContextNativeCommitException failure=org.kanger.exception.ContextNativeCommitException.incomplete(phase,unsupported.getMessage());failure.initCause(unsupported);throw failure;
        }
    }

    /** Shared same-lease decision for explicit writer and pending diagnostic adapter. */
    static void requireNativePendingFamily(ContextPendingProductionSnapshotSession lease,org.kanger.ContextNativeCandidateInspection.Limits limits,int familyWork,int familyAudit,int models) throws Exception {
        String phase="native-qualification";ContextPendingProductionSnapshotSession.Decision decision;
        try {
            phase="native-qualification";org.kanger.ContextNativePendingGuard.Result nativeResult=lease.qualifyNative(limits);lease.requireCurrent();
            if(nativeResult.status==org.kanger.ContextNativePendingGuard.Status.INCOMPLETE)throw org.kanger.exception.ContextNativeCommitException.incomplete(nativeResult.phase,nativeResult.reason);
            if(nativeResult.status==org.kanger.ContextNativePendingGuard.Status.REFUTED)throw org.kanger.exception.ContextNativeCommitException.conflict(nativeResult.evidence);
            if(nativeResult.status!=org.kanger.ContextNativePendingGuard.Status.SCOPED_READY)throw org.kanger.exception.ContextNativeCommitException.incomplete(phase,"No complete native scoped verdict");
            phase="full-family-qualification";java.util.Map<String,Integer> d=org.kanger.ContextUniversalCandidateRequest.DEFAULTS;
            decision=lease.inspectAutomatic(d.get("rounds"),d.get("local"),limits.depth,d.get("instances"),models,limits.preparation,limits.events,familyWork,familyAudit,100000);lease.requireCurrent();
        } catch(IllegalStateException unsupported) {
            org.kanger.exception.ContextNativeCommitException failure=org.kanger.exception.ContextNativeCommitException.incomplete(phase,unsupported.getMessage());failure.initCause(unsupported);throw failure;
        }
        // Existing full-family verdict contracts remain authoritative for the positive path.
        switch(decision.engine) {
            case UNIVERSAL: requirePendingUniversalResult(decision.universal);return;
            case GROUND: if(decision.ground.status!=org.kanger.ContextGroundClauseFamily.Status.GROUND_FIXED_POINT)throw new org.kanger.exception.ContextGroundProductionCommitException("Pending ground family: "+decision.ground.reason,decision.ground);return;
            case WITNESS: if(decision.witness.status!=org.kanger.ContextProductionFamilyAssembly.Status.FIXED_POINT)throw new org.kanger.exception.ContextWitnessProductionCommitException("Pending witness family: "+decision.witness.reason,decision.witness);return;
            default: throw new IllegalStateException("No complete full-family engine decision");
        }
    }

    private boolean validatingCommit;

    @Override
    public synchronized void validateCommit(IMind proposed) throws Exception {
        if(pendingNativeWrite!=null) pendingNativeWrite.validate((Mind)proposed);
        if (validatingCommit || isClosed() || isReadOnly() || workingConnections.isEmpty()) return;
        validatingCommit = true;
        try {
            validateEffectiveConnections((Mind) proposed, workingConnections);
            if(pendingNativeWrite!=null) pendingNativeWrite.validate((Mind)proposed);
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
        connections.prepareCommunes(communeCache);
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
            activateConnections(context.getQualifiedWorkingConnections());
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
            activateConnections(publishedConnections());
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

            connection = connection.withTrustGroup(request.getTrustGroup());
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

        retiredLayers.addAll(prepared.getConnections());
        prepared.prepareCommunes(communeCache);
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
                vector = vector.with(connection.withTrustGroup(dependency.getTrustGroup()));
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
        activateConnections(vector);
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
                        .getRevision(), connection.getInitialization(), connection.getTrustGroup());
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
                        c.getCertificate().getSemanticVersion(), c.getInitialization(), c.getTrustGroup()));
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
            activateConnections(publishedConnections());
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
        activateConnections(requalifyWorkingConnections(saved.vector));
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
        activateConnections(publishedConnections());
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
        if(number!=null && !addressed)
            throw new CommandErrorException("Rule IDs require a Context locator; use ctx ask <locator> rules ...");
        CommuneRuntime communeAddress=addressed?addressedCommune(locator):null;
        if(communeAddress!=null) {
            Mind root=communeAddress.mind();
            IContextFederation.RuleBlock block=org.kanger.ContextRuleInspection.inspect(root,locator,
                    localOpinionRevision(root,communeAddress.members().get(0).getTarget()),false,selection,number);
            return Collections.singletonList(new IContextFederation.RuleBlock(block.locator,block.revision,
                    false,block.rules,communeAddress.members().stream().anyMatch(c->!c.getInitialization().isEmpty())));
        }
        Path selected=addressed?resolveFederationLocator(locator):null;
        if(!addressed || local.equals(selected)) {
            result.add(org.kanger.ContextRuleInspection.inspect((Mind)source,sourceLocator(),
                    new IContextFederation.Revision(getContextId(),getRevision()),!isReadOnly(),selection,number));
        }
        for(ContextConnection c:workingConnections.getConnections()) {
            if(addressed && !c.getTargetLocation().toAbsolutePath().normalize().equals(selected)) continue;
            IContextFederation.RuleBlock block = org.kanger.ContextRuleInspection.inspect(c.layer(),
                    displayFederationLocator(c.getTargetLocation()), projectRevision(c.getTarget()), false, selection, number);
            result.add(new IContextFederation.RuleBlock(block.locator, block.revision, block.working, block.rules,
                    !c.getInitialization().isEmpty()));
        }
        if (!addressed && (selection == IContextFederation.RuleSelection.ALL
                || selection == IContextFederation.RuleSelection.BASE
                || selection == IContextFederation.RuleSelection.PRODUCED)) {
            for (ContextConnection participant : workingConnections.executionConnections(communeCache)) {
                CommuneRuntime commune = participant.commune();
                if (commune == null) continue;
                IContextFederation.RuleBlock block = org.kanger.ContextRuleInspection.inspect(commune.mind(),
                        "trust " + participant.getTrustGroup(), localOpinionRevision(commune.mind(), participant.getTarget()),
                        false, selection, null);
                result.add(new IContextFederation.RuleBlock(block.locator, block.revision, false, block.rules,
                        commune.members().stream().anyMatch(member -> !member.getInitialization().isEmpty())));
            }
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
        return connectContext(targetLocator, null);
    }

    @Override
    public synchronized IContextFederation.Connection connectContext(String targetLocator, String trustGroup) throws Exception {
        org.kanger.TrustGroups.validate(trustGroup);
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
        connection = connection.withTrustGroup(trustGroup);
        retiredLayers.add(connection);
        try {
            ConnectionVector candidate = workingConnections.with(connection);
            candidate.prepareCommunes(communeCache);
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
            activateConnections(candidate);
            ((User) user).getContextOpinionSession().invalidate();
            collectAfterTopologyChange();
            return projectConnection(
                    new RevisionRef(
                            getContextId(),
                            getRevision()),
                    connection);
        } catch (Exception rejection) {
            collectAfterRejectedTopologyChange(rejection);
            throw rejection;
        }
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
        activateConnections(workingConnections.without(targetContextId));
        ((User) user).getContextOpinionSession().invalidate();
        collectAfterTopologyChange();
    }

    @Override
    public synchronized IContextFederation.Connection switchContextCurrent(String targetLocator) throws Exception {
        requireWritable();
        ContextConnection connection=connectedContext(resolveFederationLocator(targetLocator),targetLocator);
        long revision;
        try (ContextSnapshot current=ContextSnapshot.open(connection.getTargetLocation())) {
            if (!connection.getTarget().getContextId().equals(current.getContextId()))
                throw new StorageLifecycleException(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                        "Target CURRENT identity mismatch for " + targetLocator);
            revision=current.getRevision();
        }
        return switchContextRevision(connection.getTarget().getContextId(),revision);
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
        retiredLayers.add(connection);
        retiredLayers.addAll(workingConnections.getConnections());
        activateConnections(workingConnections.with(connection));
        ((User) user).getContextOpinionSession().invalidate();
        collectAfterTopologyChange();
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
                certificate.getSemanticVersion(), connection.getInitialization(), connection.getTrustGroup());
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
    public synchronized Boolean continueWholeQuery(IMind sourceMind, String querySource,
            Queue<ITerm> externals, boolean logging) throws Exception {
        requireOpen();
        List<ContextConnection> participants = workingConnections.executionConnections(communeCache);
        if (participants.stream().noneMatch(c -> c.commune() != null)) return null;
        List<Mind> roots = new ArrayList<>();
        roots.add((Mind) sourceMind);
        for (ContextConnection connection : participants)
            roots.add(connection.commune() == null ? connection.layer() : connection.commune().mind());
        Boolean answer = null;
        List<Map<String, org.kanger.SemanticTermSnapshot>> rows = new ArrayList<>();
        for (Mind root : roots) {
            Mind candidate = Mind.ephemeralChild(root);
            try {
                Queue<ITerm> parameters = new LinkedList<>();
                for (ITerm parameter : externals) parameters.add(candidate.getTerms().projectSemantic(
                        org.kanger.SemanticTermSnapshot.capture(parameter).materialize()));
                Boolean local = candidate.queryCanonical(querySource, parameters, false);
                if (local == null) continue;
                // Whole proofs from independent participants must agree.
                if (answer != null && !answer.equals(local)) return null;
                answer = local;
                for (Map<String, ITerm> row : candidate.getValues()) {
                    Map<String, org.kanger.SemanticTermSnapshot> detached = new LinkedHashMap<>();
                    for (Map.Entry<String, ITerm> value : row.entrySet())
                        detached.put(value.getKey(), org.kanger.SemanticTermSnapshot.capture(value.getValue()));
                    rows.add(detached);
                }
            } finally { root.discardEphemeral(candidate); }
        }
        if (answer == null) return null;
        Mind source = (Mind) sourceMind;
        Mind presentation = Mind.ephemeralChild(source);
        try {
            presentation.setQueryPass(answer ? org.kanger.enums.QueryPass.CHECKTRUE
                    : org.kanger.enums.QueryPass.CHECKFALSE);
            Queue<ITerm> parameters = new LinkedList<>();
            for (ITerm parameter : externals) parameters.add(presentation.getTerms().projectSemantic(
                    org.kanger.SemanticTermSnapshot.capture(parameter).materialize()));
            org.kanger.interfaces.IRule query = (org.kanger.interfaces.IRule) presentation.compileLine(
                    answer ? querySource : "!" + querySource.substring(1), true, parameters);
            presentation.getValues().clear();
            presentation.getSolutions().clear();
            for (Map<String, org.kanger.SemanticTermSnapshot> row : rows) {
                List<org.kanger.units.TValue> values = new ArrayList<>();
                for (org.kanger.units.TVariable variable : presentation.getTVars()) {
                    org.kanger.SemanticTermSnapshot value = row.get(variable.getName(presentation).toString());
                    if (variable.getRuleId() == query.getId() && value != null)
                        values.add(presentation.getTValues().add(variable,
                                presentation.getTerms().projectSemantic(value.materialize())));
                }
                presentation.getValues().add(values);
            }
            source.release(presentation);
            presentation = null;
        } finally { if (presentation != null) source.discardEphemeral(presentation); }
        return answer;
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
        // Explicit locators inspect physical members. The default view uses
        // the same epistemic participants as the main federative query.
        List<ContextConnection> participants = selected == null
                ? workingConnections.executionConnections(communeCache) : workingConnections.getConnections();
        for (ContextConnection connection : participants) {
            if (selected != null && !connection.getTargetLocation().toAbsolutePath().normalize().equals(selected)) continue;
            matched = true;
            CommuneRuntime commune = connection.commune();
            IContextFederation.Opinion opinion = localOpinion(commune == null ? connection.layer() : commune.mind(),
                    commune == null ? displayFederationLocator(connection.getTargetLocation())
                            : "trust " + connection.getTrustGroup(), connection.getTarget(), false,
                    querySource, parameters, commune == null ? !connection.getInitialization().isEmpty()
                            : commune.members().stream().anyMatch(member -> !member.getInitialization().isEmpty()));
            if (opinion.isMeaningful()) result.put(opinion.getLocator(), opinion);
        }
        if (!matched) throw new CommandErrorException("No direct Context connection exists for locator " + locator);
        return Collections.unmodifiableMap(result);
    }

    @Override
    public synchronized org.kanger.interfaces.DmzSourcePlan prepareDmzSources(IMind sourceMind,
            org.kanger.interfaces.DmzSourcePlan.Request request) throws Exception {
        requireOpen();
        if(!(sourceMind instanceof Mind) || sourceMind.getUser()!=user || user.getCurrentMind()!=sourceMind)
            throw new CommandErrorException("DMZ source preflight requires the active storage Mind");
        if(request==null)throw new IllegalArgumentException("Explicit DMZ source request required");
        if(!request.expectedQ.getContextId().equals(getContextId()) || request.expectedQ.getRevision()!=getRevision())
            throw new CommandErrorException("DMZ Q revision mismatch");
        org.kanger.interfaces.DmzSourcePlan result=DmzSourcePreflight.acquire((Mind)sourceMind,sourceLocator(),request,
                name -> location(name),dmzTopology(),false).plan;
        if(user.getCurrentMind()!=sourceMind || !request.expectedQ.getContextId().equals(getContextId())
                || request.expectedQ.getRevision()!=getRevision())throw new CommandErrorException("DMZ Q boundary changed during preflight");
        return result;
    }

    @Override
    public synchronized org.kanger.interfaces.DmzSourcesQuery.Result executeDmzSources(IMind sourceMind,
            org.kanger.interfaces.DmzSourcesQuery.Request request) throws Exception {
        if(request==null)throw new IllegalArgumentException("Explicit DMZ execution request required");
        if(!(sourceMind instanceof Mind))throw new CommandErrorException("DMZ execution requires a storage Mind");
        try(DmzSourceSession session=openDmzCandidateSession((Mind)sourceMind,request.sources,request.limits.preparation)) {
            return DmzSourceQueryProjection.execute(session,request);
        }
    }

    @Override public synchronized org.kanger.interfaces.IContextResults.Revision resolveDmzRevision(String locator,long revision) throws Exception {
        requireOpen();
        try(ContextSnapshot snapshot=ContextSnapshot.open(resolveFederationLocator(locator),revision)) {
            return new org.kanger.interfaces.IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());
        }
    }

    @Override public boolean supportsDmzSources() { return true; }
    @Override public synchronized org.kanger.interfaces.DmzSourcesQuery.Result executeConnectedDmz(IMind sourceMind,String query) throws Exception {
        requireOpen();
        List<org.kanger.interfaces.DmzSourcePlan.Selection> sources=new ArrayList<>();
        for(ContextConnection connection:workingConnections.getConnections())if(connection.getTrustGroup()==null)
            sources.add(new org.kanger.interfaces.DmzSourcePlan.Selection(displayFederationLocator(connection.getTargetLocation()),
                    new org.kanger.interfaces.IContextResults.Revision(connection.getTarget().getContextId(),connection.getTarget().getRevision())));
        org.kanger.interfaces.DmzSourcesQuery.Request request=new org.kanger.interfaces.DmzSourcesQuery.Request(
                new org.kanger.interfaces.DmzSourcePlan.Request(new org.kanger.interfaces.IContextResults.Revision(getContextId(),getRevision()),sources,10000),
                org.kanger.interfaces.DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,query,
                new org.kanger.interfaces.DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,1000));
        if(!(sourceMind instanceof Mind))throw new CommandErrorException("Active storage Mind required");
        try(DmzSourceSession session=openDmzCandidateSession((Mind)sourceMind,request.sources,request.limits.preparation,true)) {
            return DmzSourceQueryProjection.execute(session,request);
        }
    }
    synchronized List<ContextConnection> productionExecutionTopology() throws Exception {
        return workingConnections.executionConnections(communeCache);
    }
    synchronized List<ContextConnection> dmzTopology() { return new ArrayList<ContextConnection>(workingConnections.getConnections()); }
    /** Internal operation-local owner; never returned by public federation dispatch. */
    synchronized DmzSourceSession openDmzCandidateSession(Mind sourceMind,
            org.kanger.interfaces.DmzSourcePlan.Request request,int preparationBudget) throws Exception {
        return openDmzCandidateSession(sourceMind,request,preparationBudget,false);
    }
    private synchronized DmzSourceSession openDmzCandidateSession(Mind sourceMind,
            org.kanger.interfaces.DmzSourcePlan.Request request,int preparationBudget,boolean connectedViews) throws Exception {
        requireOpen();
        if(sourceMind==null || sourceMind.getUser()!=user || user.getCurrentMind()!=sourceMind
                || !sourceMind.isStorageUsed() || !(user instanceof User) || ((User)user).getData()!=this)
            throw new CommandErrorException("DMZ session requires this provider's active storage Mind");
        if(request==null)throw new IllegalArgumentException("Explicit DMZ source request required");
        if(preparationBudget<1 || preparationBudget>10000)throw new IllegalArgumentException("Bounded DMZ preparation required");
        if(!request.expectedQ.getContextId().equals(getContextId()) || request.expectedQ.getRevision()!=getRevision())
            throw new CommandErrorException("DMZ Q revision mismatch");
        org.kanger.DmzCandidateSession.requireSettledPhysicalQ(sourceMind);
        DmzSourcePreflight.Inputs inputs=DmzSourcePreflight.acquire(sourceMind,sourceLocator(),request,name -> location(name),dmzTopology(),connectedViews);
        return DmzSourceSession.open(this,sourceMind,inputs,preparationBudget);
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
                solutions.add(org.kanger.ContextProofProjection.solution(work, solution, localOpinionRevision(root, ref), query));
            return new IContextFederation.Opinion(locator, localOpinionRevision(root, ref), working, answer, solutions, configured);
        } finally { root.discardEphemeral(work); }
    }

    private IContextFederation.Revision localOpinionRevision(Mind root, RevisionRef ref) {
        return new IContextFederation.Revision(ref.getContextId(), ref.getRevision(),
                root.getCommuneName(), root.getCommuneMembers());
    }

    @Override
    public synchronized void applyConnectionCommand(IMind sourceMind, String locator, String command) throws Exception {
        requireWritable(); requireOpen();
        if (sourceMind != user.getCurrentMind() || sourceMind.getUser() != user)
            throw new CommandErrorException("Connection editing requires the current X Mind");
        if(addressedCommune(locator)!=null)
            throw new CommandErrorException("Commune views are read-only; address a physical member for connection edits");
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
                original.getCertificate(), commands, original.getTrustGroup());
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
            activateConnections(proposed);
            ((User) user).getContextOpinionSession().invalidate();
            accepted = true;
            collectAfterTopologyChange();
        } catch (Exception rejection) {
            collectAfterRejectedTopologyChange(rejection);
            throw rejection;
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

        CommuneRuntime commune=addressedCommune(targetLocator);
        if(commune!=null) {
            Mind root=commune.mind();
            Mind work=Mind.ephemeralChild(root);
            try { return projectLocalQuery(work,commune.members().get(0).getTarget(),querySource); }
            finally { root.discardEphemeral(work); }
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
                localOpinionRevision(mind, source);
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
                            projectFederativeRevision(
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
            result.add(projectFederativeRevision(ref));
        }
        return result;
    }

    private IContextFederation.Revision projectFederativeRevision(RevisionRef ref) {
        ContextConnection c = workingConnections.find(ref.getContextId());
        if (c == null || c.getTrustGroup() == null) return projectRevision(ref);
        List<IContextFederation.Revision> members = new ArrayList<>();
        for (ContextConnection member : workingConnections.getConnections())
            if (c.getTrustGroup().equals(member.getTrustGroup())) members.add(projectRevision(member.getTarget()));
        return new IContextFederation.Revision(ref.getContextId(), ref.getRevision(), c.getTrustGroup(), members);
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

    /** Literal group names address the existing qualified execution view, never a new union. */
    private CommuneRuntime addressedCommune(String locator) throws Exception {
        if(locator==null)return null;
        boolean grouped=workingConnections.getConnections().stream().anyMatch(c->locator.equals(c.getTrustGroup()));
        if(!grouped)return null;
        Path physical=resolveFederationLocator(locator);
        if(physical.equals(activeLocation().toAbsolutePath().normalize())
                || workingConnections.getConnections().stream().anyMatch(c->physical.equals(c.getTargetLocation().toAbsolutePath().normalize())))
            throw new CommandErrorException("Ambiguous Context/commune locator "+locator+"; use a path such as ./"+locator+" for the physical Context");
        for(ContextConnection participant:workingConnections.executionConnections(communeCache))
            if(locator.equals(participant.getTrustGroup()) && participant.commune()!=null)return participant.commune();
        throw new CommandErrorException("Qualified commune view unavailable: "+locator);
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
