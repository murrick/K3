/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitLedger.Row;
import org.kanger.ContextUniversalUnitLedger.Step;
import org.kanger.ContextUniversalUnitProof.Evidence;
import org.kanger.ContextUniversalUnitProof.Proof;

/** Complete immutable family barriers for bounded private unit closure only. Never write admission. */
public final class ContextUniversalUnitRounds {
    public static final class Frontier {
        public final UUID identity=UUID.randomUUID(),previous;
        public final Snapshot x;
        public final List<Snapshot> cuts;
        public final List<Row> products;
        private Frontier(Snapshot x,List<Snapshot> cuts,List<Row> products,UUID previous) {
            this.x=x; this.cuts=Collections.unmodifiableList(new ArrayList<>(cuts));
            this.products=Collections.unmodifiableList(new ArrayList<>(products)); this.previous=previous;
        }
    }
    public static final class Receipt {
        public final boolean verified,includesCompounds,includesFactoring;
        public final String reason;
        public final Frontier input;
        public final Snapshot target;
        public final List<Row> products;
        public final List<Proof> oppositions;
        public final Step closureStep;
        private Receipt(boolean verified,String reason,Frontier input,Snapshot target,List<Row> products,List<Proof> oppositions) {
            this(verified,reason,input,target,products,oppositions,false);
        }
        private Receipt(boolean verified,String reason,Frontier input,Snapshot target,List<Row> products,List<Proof> oppositions,boolean compounds) {
            this(verified,reason,input,target,products,oppositions,compounds,null);
        }
        private Receipt(boolean verified,String reason,Frontier input,Snapshot target,List<Row> products,List<Proof> oppositions,boolean compounds,Step closureStep) {
            this(verified,reason,input,target,products,oppositions,compounds,closureStep,false);
        }
        private Receipt(boolean verified,String reason,Frontier input,Snapshot target,List<Row> products,List<Proof> oppositions,boolean compounds,Step closureStep,boolean factoring) {
            includesFactoring=factoring;
            includesCompounds=compounds; this.closureStep=closureStep;
            this.verified=verified; this.reason=reason; this.input=input; this.target=target;
            this.products=Collections.unmodifiableList(new ArrayList<>(products));
            this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    public enum Status { MERGED, COLLISION, INCOMPLETE }
    public static final class Barrier {
        public final Status status;
        public final String reason;
        public final Frontier frontier;
        public final boolean changed;
        public final List<Proof> oppositions;
        private Barrier(Status status,String reason,Frontier frontier,boolean changed,List<Proof> oppositions) {
            this.status=status; this.reason=reason; this.frontier=frontier; this.changed=changed;
            this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    public static Frontier start(Snapshot x,List<Snapshot> foreign,int maximumWork) {
        return startBound(x,foreign,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Frontier startBound(Snapshot x,List<Snapshot> foreign,Runnable work) {
        ContextProductionFamilyAssembly.identity(x,true,work);
        List<Snapshot> cuts=new ArrayList<>(); cuts.add(x);
        List<Snapshot> siblings=new ArrayList<>(foreign);
        if(siblings.size()>15) throw new IllegalStateException("Unit family source limit");
        Set<UUID> seen=new HashSet<>(); seen.add(x.inventory.context);
        for(Snapshot cut:siblings) {
            work.run(); ContextProductionFamilyAssembly.identity(cut,false,work);
            if(!seen.add(cut.inventory.context)) throw new IllegalStateException("Repeated unit family context");
            cuts.add(cut);
        }
        for(Snapshot cut:cuts) ContextUniversalUnitProof.declarations(x,cut,work);
        return new Frontier(x,cuts,Collections.emptyList(),null);
    }
    /** Close only private unit inferences; every cut sees the same immutable common carriers. */
    public static Receipt inspect(Frontier input,Snapshot target,int maximumLocalRounds,int maximumDepth,int maximumWork) {
        return inspect(input,target,maximumLocalRounds,maximumDepth,maximumWork,false,false);
    }
    public static Receipt inspectCombined(Frontier input,Snapshot target,int maximumLocalRounds,int maximumDepth,int maximumWork) {
        return inspect(input,target,maximumLocalRounds,maximumDepth,maximumWork,true,false);
    }
    public static Receipt inspectFactored(Frontier input,Snapshot target,int maximumLocalRounds,int maximumDepth,int maximumWork) {
        return inspect(input,target,maximumLocalRounds,maximumDepth,maximumWork,true,true);
    }
    private static Receipt inspect(Frontier input,Snapshot target,int maximumLocalRounds,int maximumDepth,int maximumWork,boolean compounds,boolean factoring) {
        if(maximumLocalRounds<1 || maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded unit round required");
        return inspectBound(input,target,maximumLocalRounds,maximumDepth,ContextGroundProductionRounds.budget(maximumWork),compounds,factoring);
    }
    static Receipt inspectBound(Frontier input,Snapshot target,int maximumLocalRounds,int maximumDepth,Runnable work,boolean compounds,boolean factoring) {
        try {
            if(input==null || !input.cuts.contains(target)) throw new IllegalStateException("Exact unit family cut required");
            ContextUniversalUnitLedger ledger=ContextUniversalUnitLedger.openShared(input.x,target,input.products,maximumDepth,work);
            for(int round=0;round<maximumLocalRounds;round++) {
                Step step=factoring?ContextUniversalUnitLedger.stepFactoredBound(ledger,maximumDepth,work):compounds?ContextUniversalUnitLedger.stepCombinedBound(ledger,maximumDepth,work):ContextUniversalUnitLedger.stepBound(ledger,maximumDepth,work);
                if(step.status==ContextUniversalUnitLedger.Status.INCOMPLETE) throw new IllegalStateException(step.reason);
                if(step.status==ContextUniversalUnitLedger.Status.OPPOSITION) {
                    List<Proof> relevant=new ArrayList<>();
                    for(Proof p:step.oppositions) { work.run(); if(p.dependsOnX) relevant.add(p); }
                    if(relevant.isEmpty()) throw new IllegalStateException("Foreign-only private inconsistency has no X-dependent family proof");
                    return new Receipt(true,"Audited X-dependent private unit opposition",input,target,Collections.emptyList(),relevant,compounds,null,factoring);
                }
                ledger=step.next;
                if(!step.changed) {
                    List<Row> exported=new ArrayList<>();
                    for(Row row:ledger.rows) {
                        work.run(); if(row.support.depth>0 && row.support.sources.stream().anyMatch(s -> s.localX)) exported.add(row);
                    }
                    return new Receipt(true,factoring?"Private resolution/factoring recipes closed; family admission unknown":compounds?"Private binary recipes closed; factoring/family admission unknown":"Private unit recipes closed; compound/family admission unknown",input,target,exported,Collections.emptyList(),compounds,step,factoring);
                }
            }
            throw new IllegalStateException("Private unit local round limit");
        } catch(Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    public static Barrier merge(Frontier input,List<Receipt> receipts,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded unit barrier required");
        return mergeBound(input,receipts,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Barrier mergeBound(Frontier input,List<Receipt> receipts,int maximumDepth,Runnable work) {
        try {
            if(input==null) throw new IllegalStateException("Sealed unit frontier required");
            for(Row row:input.products) { work.run(); if(row.support.depth>maximumDepth) throw new IllegalStateException("Carried unit depth limit"); }
            List<Receipt> outputs=new ArrayList<>(receipts);
            if(outputs.size()!=input.cuts.size()) throw new IllegalStateException("Incomplete unit family barrier");
            Set<Snapshot> seen=new HashSet<>();
            for(Receipt r:outputs) {
                work.run(); if(r==null || !r.verified || r.input!=input || !input.cuts.contains(r.target) || !seen.add(r.target))
                    throw new IllegalStateException("Unit receipt scope/input/evidence mismatch");
                for(Row row:r.products) {
                    work.run(); if(row.support.depth>maximumDepth || row.support.depth<=0 || row.support.sources.stream().noneMatch(s -> s.localX))
                        throw new IllegalStateException("Unqualified or over-depth unit carrier");
                }
                for(Proof p:r.oppositions) { work.run(); if(p.support.depth>maximumDepth) throw new IllegalStateException("Opposition cumulative depth limit"); }
            }
            List<Proof> opposed=new ArrayList<>();
            for(Receipt r:outputs) { work.run(); opposed.addAll(r.oppositions); }
            if(!opposed.isEmpty()) return new Barrier(Status.COLLISION,"Complete family has proved X-dependent unit opposition",null,false,opposed);
            List<Row> pool=new ArrayList<>();
            for(Row row:input.products) ContextUniversalUnitLedger.add(pool,row.clause,row.support,maximumDepth,work);
            boolean changed=false;
            for(Receipt r:outputs) for(Row row:r.products) {
                work.run(); if(row.support.depth<=0 || row.support.sources.stream().noneMatch(s -> s.localX))
                    throw new IllegalStateException("Unqualified family unit carrier");
                changed |= ContextUniversalUnitLedger.add(pool,row.clause,row.support,maximumDepth,work);
            }
            // Authenticate direct shared opposition in one sealed self ledger; no sibling raw facts enter it.
            ContextUniversalUnitLedger shared=ContextUniversalUnitLedger.openShared(input.x,input.x,pool,maximumDepth,work);
            for(int i=0;i<pool.size();i++) for(int j=i+1;j<pool.size();j++) {
                Row a=pool.get(i),b=pool.get(j); work.run();
                if(a.clause.literals.size()!=1 || b.clause.literals.size()!=1) continue;
                Atom left=a.clause.literals.get(0),right=b.clause.literals.get(0);
                if(left.positive==right.positive || !left.predicate.equals(right.predicate) || left.arguments.size()!=right.arguments.size()) continue;
                if(!ContextUniversalCompositionCoverage.unifiable(left,right,work)) continue;
                Evidence e=ContextUniversalUnitProof.proveLedger(shared,a.clause,0,b.clause,0,maximumDepth,work);
                if(!e.proved) throw new IllegalStateException(e.reason);
                for(Proof p:e.proofs) { work.run(); if(!p.contradiction || !p.dependsOnX) throw new IllegalStateException("Invalid shared unit opposition"); opposed.add(p); }
            }
            if(!opposed.isEmpty()) return new Barrier(Status.COLLISION,"Opposite qualified shared unit schemas",null,false,opposed);
            return new Barrier(Status.MERGED,"Unit family barrier only; compound saturation/admission unknown",new Frontier(input.x,input.cuts,pool,input.identity),changed,Collections.emptyList());
        } catch(Exception failure) { return new Barrier(Status.INCOMPLETE,failure.getMessage(),null,false,Collections.emptyList()); }
    }
}
