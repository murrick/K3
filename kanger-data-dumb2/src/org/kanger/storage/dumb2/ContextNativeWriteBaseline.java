/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;

/** Native-only settled source baseline with all physical trusted members; no write authority. */
final class ContextNativeWriteBaseline implements AutoCloseable {
    private final DB provider;private final Mind source;private final Thread owner=Thread.currentThread();
    private final int rows;private final RevisionRef revision;
    final ContextProductionInventory x;final List<ContextProductionInventory> foreign;
    final List<ContextConnection> topology;
    private ContextPendingProductionSnapshotSession pending;private Mind proposed;
    private boolean closed,failed;
    static ContextNativeWriteBaseline open(DB db,Mind source,int rows)throws Exception{synchronized(db){return new ContextNativeWriteBaseline(db,source,rows);}}
    private ContextNativeWriteBaseline(DB db,Mind source,int rows)throws Exception{
        if(rows<1||rows>10000)throw new IllegalArgumentException("Bounded complete native baseline required");
        provider=db;this.source=source;this.rows=rows;requireOwner();DmzCandidateSession.requireSettledPhysicalQ(source);
        revision=new RevisionRef(db.getContextId(),db.getRevision());topology=Collections.unmodifiableList(new ArrayList<>(db.dmzTopology()));
        Set<UUID> ids=new HashSet<>();ids.add(revision.getContextId());for(ContextConnection c:topology)if(!ids.add(c.getTarget().getContextId()))throw new IllegalStateException("Distinct physical native baseline members required");
        x=ContextProductionInventory.inspect(source,revision.getContextId(),revision.getRevision(),true,Collections.emptyList(),rows);
        int remaining=rows-x.rows.size();List<ContextProductionInventory> views=new ArrayList<>();for(ContextConnection c:topology){ContextProductionInventory view=inventory(c,remaining);remaining-=view.rows.size();views.add(view);}foreign=Collections.unmodifiableList(views);requireCurrent();
    }
    private ContextProductionInventory inventory(ContextConnection c,int bound)throws Exception{return ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),bound);}
    private void requireOwner()throws Exception{if(closed||failed||Thread.currentThread()!=owner||source==null||provider.isClosed()||provider.isReadOnly()||((User)source.getUser()).getData()!=provider||source.getUser().getCurrentMind()!=source)throw new IllegalStateException("Active owning writable native baseline required");}
    private void current(Mind child)throws Exception{
        requireOwner();if(child==null)DmzCandidateSession.requireSettledPhysicalQ(source);else source.requirePendingPhysicalChild(child);
        List<ContextConnection> now=provider.dmzTopology();if(!revision.getContextId().equals(provider.getContextId())||revision.getRevision()!=provider.getRevision()||!topology.equals(now))throw new IllegalStateException("Native baseline source revision/topology changed");
        if(!x.fingerprint.equals(ContextProductionInventory.inspect(source,revision.getContextId(),revision.getRevision(),true,Collections.emptyList(),rows).fingerprint))throw new IllegalStateException("Native baseline source inventory changed");
        for(int i=0;i<topology.size();i++)if(topology.get(i)!=now.get(i)||!foreign.get(i).fingerprint.equals(inventory(now.get(i),foreign.get(i).rows.size()).fingerprint))throw new IllegalStateException("Native baseline exact initialized physical member changed");
        runtime();
    }
    /** Qualified cache may reuse equivalent knowledge, but every original member must match. */
    private void runtime()throws Exception{
        Map<UUID,ContextConnection> expected=new LinkedHashMap<>();for(ContextConnection c:topology)expected.put(c.getTarget().getContextId(),c);
        Set<UUID> seen=new HashSet<>();for(ContextConnection route:provider.productionExecutionTopology()){
            List<ContextConnection> members=route.commune()==null?Collections.singletonList(route):route.commune().members();
            for(ContextConnection member:members){ContextConnection actual=expected.get(member.getTarget().getContextId());if(actual==null||!seen.add(member.getTarget().getContextId())||!actual.getTargetLocation().equals(member.getTargetLocation())||!actual.getTarget().equals(member.getTarget())||!actual.getInitialization().equals(member.getInitialization())||!actual.getCertificate().getSemanticVersion().equals(member.getCertificate().getSemanticVersion())||!Objects.equals(route.getTrustGroup(),actual.getTrustGroup())||!Objects.equals(route.getTrustGroup(),member.getTrustGroup()))throw new IllegalStateException("Different native baseline qualified runtime member mapping");}
        }
        if(!seen.equals(expected.keySet()))throw new IllegalStateException("Incomplete native baseline qualified runtime members");
    }
    void requireCurrent()throws Exception{synchronized(provider){current(proposed);if(pending!=null)pending.requireCurrent();}}
    /** ONE exact prepared child, with a live owned lease; not a detached/scalar admission receipt. */
    ContextPendingProductionSnapshotSession bind(Mind child)throws Exception{synchronized(provider){
        try{current(child);if(pending!=null){if(proposed!=child)throw new IllegalStateException("Native baseline already bound to another child");pending.requireCurrent();return pending;}
            ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(provider,child,rows);
            try{lease.requireNativeBaseline(x,foreign,topology);current(child);pending=lease;proposed=child;return lease;}catch(Exception failure){lease.close();throw failure;}
        }catch(Exception failure){failed=true;throw failure;}
    }}
    @Override public void close(){synchronized(provider){closed=true;if(pending!=null)pending.close();}}
}
