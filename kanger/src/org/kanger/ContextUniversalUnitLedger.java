/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.Frontier;
import org.kanger.ContextOrdinaryResidualRounds.Product;
import org.kanger.ContextUniversalUnitProof.Evidence;
import org.kanger.ContextUniversalUnitProof.Proof;

/** Sealed private cut ledger. This is not the shared family Frontier or storage. */
public final class ContextUniversalUnitLedger {
    public final UUID identity=UUID.randomUUID(),previous;
    public final Snapshot x,target;
    public final Frontier input;
    public final List<Row> rows;
    public static final class Row {
        public final Clause clause;
        public final Support support;
        private Row(Clause clause,Support support) { this.clause=clause; this.support=support; }
    }
    private ContextUniversalUnitLedger(Snapshot x,Snapshot target,Frontier input,List<Row> rows,UUID previous) {
        this.x=x; this.target=target; this.input=input; this.previous=previous;
        this.rows=Collections.unmodifiableList(new ArrayList<>(rows));
    }
    public static final class Opened {
        public final boolean opened;
        public final String reason;
        public final ContextUniversalUnitLedger ledger;
        private Opened(boolean opened,String reason,ContextUniversalUnitLedger ledger) { this.opened=opened; this.reason=reason; this.ledger=ledger; }
    }
    public enum Status { ADVANCED, OPPOSITION, INCOMPLETE }
    public static final class Step {
        public final Status status;
        public final String reason;
        public final ContextUniversalUnitLedger input,next;
        public final boolean changed;
        public final List<Evidence> evidence;
        public final List<Proof> oppositions;
        private Step(Status status,String reason,ContextUniversalUnitLedger input,ContextUniversalUnitLedger next,boolean changed,List<Evidence> evidence,List<Proof> oppositions) {
            this.status=status; this.reason=reason; this.input=input; this.next=next; this.changed=changed;
            this.evidence=Collections.unmodifiableList(new ArrayList<>(evidence));
            this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    private static boolean add(List<Row> rows,Clause clause,Support proof,int maximumDepth,Runnable work) {
        work.run(); if(proof.depth>maximumDepth) throw new IllegalStateException("Unit ledger cumulative depth limit");
        boolean same=false; List<Clause> distinct=new ArrayList<>();
        for(int i=0;i<rows.size();i++) {
            Row old=rows.get(i); work.run();
            if(old.clause.equivalent(clause)) {
                same=true;
                if(old.support.sources.equals(proof.sources)) {
                    if(old.support.depth<=proof.depth) return false;
                    rows.set(i,new Row(clause,proof)); return true;
                }
            }
            boolean seen=false;
            for(Clause c:distinct) { work.run(); if(c.equivalent(old.clause)) { seen=true; break; } }
            if(!seen) distinct.add(old.clause);
        }
        if(rows.size()>=256 || !same && distinct.size()>=64) throw new IllegalStateException("Unit ledger clause/proof limit");
        rows.add(new Row(clause,proof)); return true;
    }
    public static Opened openPrimary(Snapshot x,Snapshot foreign,int maximumWork) {
        return open(x,foreign==null?x:foreign,null,Collections.emptyList(),0,maximumWork);
    }
    public static Opened open(Frontier input,Snapshot target,List<ContextUniversalCompositionProof.Evidence> compounds,int maximumDepth,int maximumWork) {
        return open(input==null?null:input.x,target,input,compounds,maximumDepth,maximumWork);
    }
    private static Opened open(Snapshot x,Snapshot target,Frontier input,List<ContextUniversalCompositionProof.Evidence> compounds,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded unit ledger required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(x==null || target==null || input!=null && !input.cuts.contains(target)) throw new IllegalStateException("Exact unit ledger cut required");
            List<Row> rows=new ArrayList<>();
            for(Entry entry:ContextUniversalUnitProof.declarations(x,target,work)) for(Clause clause:entry.clauses)
                add(rows,clause,new Support(Collections.singleton(entry.source),0),maximumDepth,work);
            for(Product p:input==null?Collections.<Product>emptyList():input.products) add(rows,p.clause,p.proof,maximumDepth,work);
            for(ContextUniversalCompositionProof.Evidence e:new ArrayList<>(compounds)) {
                work.run(); if(input==null || e==null || !e.proved || e.input!=input || e.target!=target)
                    throw new IllegalStateException("Wrong input/cut compound authority for ledger");
                for(ContextUniversalCompositionProof.Proof p:e.proofs) add(rows,p.conclusion,p.support,maximumDepth,work);
            }
            return new Opened(true,"Sealed historical private unit ledger; no family authority",new ContextUniversalUnitLedger(x,target,input,rows,null));
        } catch(Exception failure) { return new Opened(false,failure.getMessage(),null); }
    }
    /** A single immutable-input unit sweep; no newly produced row becomes a parent until next step. */
    public static Step step(ContextUniversalUnitLedger input,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded unit ledger step required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(input==null) throw new IllegalStateException("Sealed unit ledger required");
            List<Clause> clauses=new ArrayList<>();
            for(Row row:input.rows) {
                work.run(); if(row.support.depth>maximumDepth) throw new IllegalStateException("Unit ledger parent depth limit");
                boolean seen=false;
                for(Clause c:clauses) { work.run(); if(c.equivalent(row.clause)) { seen=true; break; } }
                if(!seen) clauses.add(row.clause);
            }
            List<Row> rows=new ArrayList<>(input.rows); List<Evidence> evidence=new ArrayList<>(); List<Proof> opposed=new ArrayList<>(); boolean changed=false;
            for(int i=0;i<clauses.size();i++) for(int j=i;j<clauses.size();j++) {
                Clause left=clauses.get(i),right=clauses.get(j);
                work.run(); if(left.literals.size()!=1 && right.literals.size()!=1) continue;
                for(int a=0;a<left.literals.size();a++) for(int b=0;b<right.literals.size();b++) {
                    work.run(); Atom x=left.literals.get(a),y=right.literals.get(b);
                    if(x.positive==y.positive || !x.predicate.equals(y.predicate) || x.arguments.size()!=y.arguments.size()) continue;
                    try { left.resolveWithUnits(right,a,b,work); } catch(Clause.UnificationFailure impossible) { continue; }
                    Evidence e=ContextUniversalUnitProof.proveLedger(input,left,a,right,b,maximumDepth,work);
                    if(!e.proved) throw new IllegalStateException(e.reason);
                    evidence.add(e);
                    for(Proof p:e.proofs) {
                        if(p.contradiction) opposed.add(p);
                        else changed |= add(rows,p.conclusion,p.support,maximumDepth,work);
                    }
                }
            }
            ContextUniversalUnitCoverage.Audit audit=ContextUniversalUnitCoverage.audit(input,evidence,maximumDepth,work);
            if(!audit.covered) throw new IllegalStateException(audit.reason);
            if(!opposed.isEmpty()) return new Step(Status.OPPOSITION,"Audited private unit opposition; no family verdict",input,null,false,evidence,opposed);
            ContextUniversalUnitLedger next=new ContextUniversalUnitLedger(input.x,input.target,input.input,rows,input.identity);
            return new Step(Status.ADVANCED,"Audited private unit sweep; compound closure/family admission unknown",input,next,changed,evidence,Collections.emptyList());
        } catch(Exception failure) { return new Step(Status.INCOMPLETE,failure.getMessage(),input,null,false,Collections.emptyList(),Collections.emptyList()); }
    }
}
