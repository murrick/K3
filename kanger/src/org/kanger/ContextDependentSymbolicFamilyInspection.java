/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Read-only model/receiving-round composition of fresh snapshots. Provider and admission authority remain external. */
public final class ContextDependentSymbolicFamilyInspection {
    private ContextDependentSymbolicFamilyInspection() {}
    public static final class Member {
        public final Mind actual;public final ContextDependentSymbolicCacheSnapshot.Snapshot snapshot;
        public Member(Mind actual,ContextDependentSymbolicCacheSnapshot.Snapshot snapshot){if(actual==null||snapshot==null)throw new IllegalArgumentException("Actual checked foreign member required");this.actual=actual;this.snapshot=snapshot;}
    }
    public static ContextDependentModelSearchInspection.Result inspect(Mind x,ContextDependentSymbolicCacheSnapshot.Snapshot local,List<Member> supplied)throws Exception{
        if(x==null||local==null||supplied==null||supplied.size()>64||supplied.contains(null)||!local.inventory.localX)throw new IllegalArgumentException("Actual checked X and bounded independent member manifest required");List<Member> members=new ArrayList<>(supplied);Set<UUID> identities=new HashSet<>();identities.add(local.inventory.context);Set<Mind> runtimes=Collections.newSetFromMap(new IdentityHashMap<Mind,Boolean>());runtimes.add(x);
        local.requireCurrent(x,local.inventory);for(Member member:members){if(member.snapshot.inventory.localX||!identities.add(member.snapshot.inventory.context)||!runtimes.add(member.actual))throw new IllegalArgumentException("Distinct foreign member identities required");member.snapshot.requireCurrent(member.actual,member.snapshot.inventory);}
        try{
            ContextDependentCutInspection.Member root=new ContextDependentCutInspection.Member(x,local.diagnosticPrimaryView(x));List<ContextDependentCutInspection.Cut> cuts=new ArrayList<>();int rows=local.inventory.rows.size();
            for(Member member:members){rows+=member.snapshot.inventory.rows.size();if(rows>1000)throw new IllegalStateException("Whole symbolic family physical-row bound");cuts.add(new ContextDependentCutInspection.Cut(null,Collections.singletonList(new ContextDependentCutInspection.Member(member.actual,member.snapshot.diagnosticPrimaryView(member.actual)))));}
            return ContextDependentAutomaticInspection.inspect(root,cuts,
                    new ContextDependentIterationInspection.Request(new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000),10000,8),
                    new ContextDependentModelSearchInspection.Limits(30,100000,100000,20000,64000000,64000000,64000000,64000000,64000000),
                    new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,64000000,64000000,64000000,64000000));
        }finally{local.requireCurrent(x,local.inventory);for(Member member:members)member.snapshot.requireCurrent(member.actual,member.snapshot.inventory);}
    }
}
