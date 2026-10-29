/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.interfaces.IMind;

/** Provider-owned read-only pending-child lease. Never reserves, settles or writes. */
final class ContextPendingProductionSnapshotSession implements AutoCloseable {
    static final class Result {
        final ContextUniversalFamilyInspection.Result inspection;
        final ContextUniversalFamilyAudit.Audit audit;
        private Result(ContextUniversalFamilyInspection.Result inspection,ContextUniversalFamilyAudit.Audit audit) {this.inspection=inspection;this.audit=audit;}
    }
    enum Engine { GROUND, UNIVERSAL, WITNESS }
    /** A detached diagnostic; no later native write may reuse it as authority. */
    static final class Decision {
        final Engine engine;
        final ContextGroundClauseFamily.Evaluation ground;
        final ContextProductionFamilyAssembly.Evaluation witness;
        final Result universal;
        private Decision(Engine engine,ContextGroundClauseFamily.Evaluation ground,ContextProductionFamilyAssembly.Evaluation witness,Result universal) {
            this.engine=engine;this.ground=ground;this.witness=witness;this.universal=universal;
        }
    }
    private final DB provider;
    private final Mind parent,child;
    private Mind retained;
    private ContextProductionInventory retainedInventory;
    private final IMind active;
    private final Thread owner=Thread.currentThread();
    private final int rows;
    private final RevisionRef source;
    private final List<ContextConnection> topology;
    private final ContextProductionInventory parentInventory,childInventory;
    private final List<ContextProductionInventory> foreign;
    private boolean closed;
    static ContextPendingProductionSnapshotSession open(DB db,Mind proposed,int rows) throws Exception {
        synchronized(db) {return new ContextPendingProductionSnapshotSession(db,proposed,rows);}
    }
    private ContextPendingProductionSnapshotSession(DB db,Mind proposed,int rows) throws Exception {
        if(rows<1 || rows>10000 || proposed==null || !(proposed.getNext() instanceof Mind)) throw new IllegalArgumentException("Bounded actual pending child required");
        provider=db;child=proposed;parent=(Mind)child.getNext();this.rows=rows;active=child.getUser().getCurrentMind();
        retained=parent.requirePendingValidationChild(child);
        requireOwner();source=new RevisionRef(db.getContextId(),db.getRevision());
        topology=Collections.unmodifiableList(new ArrayList<>(db.dmzTopology()));
        Set<UUID> ids=new HashSet<>();ids.add(source.getContextId());
        for(ContextConnection c:topology) if(!ids.add(c.getTarget().getContextId())) throw new IllegalStateException("Distinct initialized pending member contexts required");
        parentInventory=inventory(parent,rows);childInventory=inventory(child,rows);
        retainedInventory=retained==null?null:inventory(retained,rows);
        int remaining=rows-childInventory.rows.size();List<ContextProductionInventory> views=new ArrayList<>();
        for(ContextConnection c:topology) {ContextProductionInventory i=ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),remaining);remaining-=i.rows.size();views.add(i);}
        foreign=Collections.unmodifiableList(views);
        // Reuse the provider's qualified shared native runtime, not raw independent member cuts.
        db.productionExecutionTopology();requireCurrent();
    }
    private void requireOwner() throws Exception {
        if(closed || Thread.currentThread()!=owner || provider.isClosed() || provider.isReadOnly() || ((User)child.getUser()).getData()!=provider
                || child.getUser().getCurrentMind()!=active) throw new IllegalStateException("Active owning writable pending-child lease required");
        if(parent.requirePendingValidationChild(child)!=retained) throw new IllegalStateException("Pending validation ownership scope changed");
    }
    private ContextProductionInventory inventory(Mind mind,int limit) throws Exception {
        return ContextProductionInventory.inspect(mind,source.getContextId(),source.getRevision(),true,Collections.emptyList(),limit);
    }
    void requireCurrent() throws Exception {
        synchronized(provider) {
            requireOwner();
            if(retained!=null && !retainedInventory.fingerprint.equals(inventory(retained,rows).fingerprint)) throw new IllegalStateException("Retained rollback child inventory changed");
            List<ContextConnection> current=provider.dmzTopology();
            if(!source.getContextId().equals(provider.getContextId()) || source.getRevision()!=provider.getRevision() || !topology.equals(current)) throw new IllegalStateException("Pending physical source revision/topology changed");
            if(!parentInventory.fingerprint.equals(inventory(parent,rows).fingerprint) || !childInventory.fingerprint.equals(inventory(child,rows).fingerprint)) throw new IllegalStateException("Pending parent/child inventory changed");
            for(int n=0;n<topology.size();n++) {ContextConnection c=topology.get(n);
                if(c!=current.get(n)) throw new IllegalStateException("Pending initialized connection instance replaced");
                ContextProductionInventory old=foreign.get(n);
                if(!old.fingerprint.equals(ContextProductionInventory.inspect(c.layer(),old.context,old.revision,false,c.getInitialization(),old.rows.size()).fingerprint)) throw new IllegalStateException("Pending initialized foreign inventory changed");}
        }
    }
    void requireBaseline(ContextProductionSnapshotSession.Inventory baseline,List<ContextConnection> connections) throws Exception {
        requireNativeBaseline(baseline.x,baseline.foreign,connections);
    }
    void requireNativeBaseline(ContextProductionInventory parent,List<ContextProductionInventory> members,List<ContextConnection> connections) throws Exception {
        synchronized(provider) {
            requireCurrent();
            if(!parentInventory.fingerprint.equals(parent.fingerprint) || foreign.size()!=members.size() || topology.size()!=connections.size())
                throw new IllegalStateException("Native write baseline changed before native validation");
            for(int i=0;i<foreign.size();i++) if(topology.get(i)!=connections.get(i) || !foreign.get(i).fingerprint.equals(members.get(i).fingerprint))
                throw new IllegalStateException("Native write initialized cut changed before native validation");
        }
    }
    private List<Snapshot> bindForeign(int work,int events) throws Exception {
        List<Snapshot> members=new ArrayList<>();
        for(int n=0;n<topology.size();n++) {
            ContextProductionNativeBinding.Result f=ContextProductionNativeBinding.bind(topology.get(n).layer(),foreign.get(n),work,events);
            if(!f.bound)throw new IllegalStateException("Pending foreign binding incomplete: "+f.reason);
            members.add(f.snapshot);
        }
        Map<String,List<Snapshot>> groups=new LinkedHashMap<>();
        for(int n=0;n<topology.size();n++)if(topology.get(n).getTrustGroup()!=null)
            groups.computeIfAbsent(topology.get(n).getTrustGroup(),k->new ArrayList<>()).add(members.get(n));
        Set<String> emitted=new HashSet<>();List<Snapshot> cuts=new ArrayList<>();
        for(int n=0;n<topology.size();n++) {
            String group=topology.get(n).getTrustGroup();
            if(group==null)cuts.add(members.get(n));
            else if(emitted.add(group))cuts.add(ContextProductionNativeBinding.trusted(group,groups.get(group),work));
        }
        return cuts;
    }
    /** Check original member inventories against the provider's qualified execution cuts, not routing aliases. */
    private void requireNativeCuts(List<Snapshot> cuts) throws Exception {
        Map<UUID,ContextProductionInventory> expected=new LinkedHashMap<>();Map<UUID,ContextConnection> connections=new LinkedHashMap<>();
        for(int i=0;i<topology.size();++i){expected.put(topology.get(i).getTarget().getContextId(),foreign.get(i));connections.put(topology.get(i).getTarget().getContextId(),topology.get(i));}
        Set<UUID> seen=new HashSet<>();for(Snapshot cut:cuts)for(Snapshot member:(cut.trustedGroup==null?Collections.singletonList(cut):cut.members)) {
            ContextConnection c=connections.get(member.inventory.context);
            if(c==null||member.inventory!=expected.get(member.inventory.context)||!seen.add(member.inventory.context)||!Objects.equals(c.getTrustGroup(),cut.trustedGroup))throw new IllegalStateException("Different actual initialized native member/cut manifest");
        }
        if(!seen.equals(expected.keySet()))throw new IllegalStateException("Incomplete native member inventory");
        List<ContextConnection> execution=provider.productionExecutionTopology();Set<UUID> routed=new HashSet<>();
        for(ContextConnection c:execution) {
            List<ContextConnection> members=c.commune()==null?Collections.singletonList(c):c.commune().members();
            for(ContextConnection member:members) {
                ContextConnection actual=connections.get(member.getTarget().getContextId());
                // Commune cache intentionally reuses equivalent pinned knowledge across recertified connections.
                if(actual==null||!routed.add(member.getTarget().getContextId())||!actual.getTargetLocation().equals(member.getTargetLocation())||!actual.getTarget().equals(member.getTarget())||!actual.getInitialization().equals(member.getInitialization())||!actual.getCertificate().getSemanticVersion().equals(member.getCertificate().getSemanticVersion())||!Objects.equals(c.getTrustGroup(),actual.getTrustGroup())||!Objects.equals(c.getTrustGroup(),member.getTrustGroup()))throw new IllegalStateException("Different qualified native runtime member/pin mapping");
            }
        }
        if(!routed.equals(expected.keySet()))throw new IllegalStateException("Incomplete qualified native runtime mapping");
    }
    /** Read-only ACTUAL prepared child, including whole batches; never stage/append it again. */
    ContextNativeBranchInspection.Result inspectNative(ContextNativeCandidateInspection.Limits limits) throws Exception {
        synchronized(provider) {
            requireCurrent();if(limits==null||limits.rows!=rows)throw new IllegalArgumentException("Native pending row bound must match owning lease");
            List<Snapshot> cuts=null;
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,limits.preparation,limits.events);
                if(!x.bound)throw new IllegalStateException("Pending native whole-X binding incomplete: "+x.reason);
                cuts=bindForeign(limits.preparation,limits.events);requireNativeCuts(cuts);
                return ContextNativeBranchInspection.inspect(x.snapshot,cuts,limits);
            } finally {requireCurrent();if(cuts!=null)requireNativeCuts(cuts);}
        }
    }
    /** Sealed fresh native refusal gate; SCOPED_READY still requires full-family eligibility. */
    ContextNativePendingGuard.Result qualifyNative(ContextNativeCandidateInspection.Limits limits) throws Exception {
        synchronized(provider) {
            requireCurrent();if(limits==null||limits.rows!=rows)throw new IllegalArgumentException("Native pending row bound must match owning lease");
            List<Snapshot> cuts=null;
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,limits.preparation,limits.events);
                if(!x.bound)throw new IllegalStateException("Pending native binding incomplete: "+x.reason);
                cuts=bindForeign(limits.preparation,limits.events);requireNativeCuts(cuts);
                return ContextNativePendingGuard.inspect(x.snapshot,cuts,limits);
            } finally {requireCurrent();if(cuts!=null)requireNativeCuts(cuts);}
        }
    }
    ContextProductionFamilyAssembly.Evaluation inspectWitness(int rounds,int depth,int work,int events) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,work,events);
                if(!x.bound) throw new IllegalStateException("Pending witness X binding incomplete: "+x.reason);
                List<Snapshot> bound=bindForeign(work,events);
                ContextProductionFamilyAssembly.Result family=ContextProductionFamilyAssembly.assemble(x.snapshot,bound,work);
                if(!family.assembled) throw new IllegalStateException("Pending witness family incomplete: "+family.reason);
                return family.artifact.evaluate(rounds,depth,work,events);
            } finally {requireCurrent();}
        }
    }
    ContextGroundClauseFamily.Evaluation inspectGround(int rounds,int local,int depth,int work,int events,int probes,int models) throws Exception {
        return inspectGround(rounds,local,depth,work,events,probes,models,ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    ContextGroundClauseFamily.Evaluation inspectGround(int rounds,int local,int depth,int work,int events,int probes,int models,ContextOrdinaryResidualRounds.LedgerLimits ledgerLimits) throws Exception {
        if(ledgerLimits==null) throw new IllegalArgumentException("Explicit ground ledger limits required");
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,work,events);
                if(!x.bound) throw new IllegalStateException("Pending ground X binding incomplete: "+x.reason);
                List<Snapshot> bound=bindForeign(work,events);
                return ContextGroundClauseFamily.evaluate(x.snapshot,bound,rounds,local,depth,work,events,probes,models,ledgerLimits);
            } finally {requireCurrent();}
        }
    }
    Result inspect(int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int auditWork) throws Exception {
        if(rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || auditWork<1) throw new IllegalArgumentException("Bounded pending inspection required");
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,preparation,events);
                if(!x.bound) throw new IllegalStateException("Pending X binding incomplete: "+x.reason);
                List<Snapshot> bound=bindForeign(preparation,events);
                ContextUniversalFamilyInspection.Result result=ContextUniversalFamilyInspection.inspect(x.snapshot,bound,rounds,local,depth,instances,models,work);
                ContextUniversalFamilyAudit.Audit checked=ContextUniversalFamilyAudit.audit(result,x.snapshot,bound,depth,instances,auditWork);
                return new Result(result,checked);
            } finally {requireCurrent();}
        }
    }
    Decision inspectAutomatic(int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int audit,int probes) throws Exception {
        if(rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || audit<1 || probes<1)
            throw new IllegalArgumentException("Bounded automatic pending qualification required");
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,preparation,events);
                if(!x.bound) throw new IllegalStateException("Pending X binding incomplete: "+x.reason);
                List<Snapshot> bound=bindForeign(preparation,events);
                List<Snapshot> complete=new ArrayList<>(bound);complete.add(x.snapshot);
                boolean witnesses=false,variables=false;int remaining=work;
                for(Snapshot snapshot:complete) for(ContextProductionNativeBinding.Entry entry:snapshot.primary) {
                    if(--remaining<0) throw new IllegalStateException("Pending engine selection work limit");
                    witnesses|=entry.kind==ContextProductionNativeBinding.Kind.WITNESS_BUNDLE;
                    for(ContextProductionCapture.Clause clause:entry.clauses) {
                        if(--remaining<0) throw new IllegalStateException("Pending engine selection work limit");
                        variables|=!clause.variables.isEmpty();
                    }
                }
                if(witnesses) {
                    ContextProductionFamilyAssembly.Result family=ContextProductionFamilyAssembly.assemble(x.snapshot,bound,work);
                    if(!family.assembled) throw new IllegalStateException("Pending witness family incomplete: "+family.reason);
                    return new Decision(Engine.WITNESS,null,family.artifact.evaluate(rounds,depth,work,events),null);
                }
                if(!variables) return new Decision(Engine.GROUND,ContextGroundClauseFamily.evaluate(x.snapshot,bound,rounds,local,depth,work,events,probes,models),null,null);
                ContextUniversalFamilyInspection.Result inspected=ContextUniversalFamilyInspection.inspect(x.snapshot,bound,rounds,local,depth,instances,models,work);
                ContextUniversalFamilyAudit.Audit checked=ContextUniversalFamilyAudit.audit(inspected,x.snapshot,bound,depth,instances,audit);
                return new Decision(Engine.UNIVERSAL,null,null,new Result(inspected,checked));
            } finally {requireCurrent();}
        }
    }
    @Override public void close() {synchronized(provider) {closed=true;}}
}
