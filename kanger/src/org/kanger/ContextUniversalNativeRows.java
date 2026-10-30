/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;

/** Whole original row accounting; classification is neither inference nor qualification. */
final class ContextUniversalNativeRows {
    enum Role { PRODUCER, CONSTRAINT, WITNESS, UNSUPPORTED }
    static final class Part {
        final int clauseIndex;
        final Clause clause;
        final Role role;
        final String reason;
        private Part(int clauseIndex,Clause clause,Role role,String reason){this.clauseIndex=clauseIndex;this.clause=clause;this.role=role;this.reason=reason;}
    }
    static final class Row {
        final Entry original;
        final List<Part> parts;
        final boolean withinShape;
        private Row(Entry original,List<Part> parts){this.original=original;this.parts=Collections.unmodifiableList(new ArrayList<>(parts));withinShape=parts.stream().noneMatch(p->p.role==Role.UNSUPPORTED);}
    }
    static final class Result {
        final boolean classified,withinShape;
        final ContextSignedWitnessFrameAudit.Receipt signedSeeds;
        final ContextSignedWitnessContinuationScope.Receipt signedContinuation;
        final String reason;
        final Snapshot x,foreign;
        final Cut parent,target;
        final List<Row> rows;
        final Set<Source> sources;
        private Result(boolean classified,String reason,Snapshot x,Snapshot foreign,List<Row> rows) {
            this(classified,reason,x,foreign,rows,null);
        }
        private Result(boolean classified,String reason,Snapshot x,Snapshot foreign,List<Row> rows,ContextSignedWitnessFrameAudit.Receipt signedSeeds) {
            this(classified,reason,x,foreign,rows,signedSeeds,null);
        }
        private Result(boolean classified,String reason,Snapshot x,Snapshot foreign,List<Row> rows,ContextSignedWitnessFrameAudit.Receipt signedSeeds,ContextSignedWitnessContinuationScope.Receipt signedContinuation) {
            this.signedContinuation=signedContinuation;this.signedSeeds=signedSeeds;this.classified=classified;this.reason=reason;this.x=x;this.foreign=foreign;
            parent=classified?Cut.of(x):null;target=classified?(foreign==null?parent:Cut.of(foreign)):null;
            this.rows=Collections.unmodifiableList(new ArrayList<>(rows));Set<Source> sources=new LinkedHashSet<>();for(Row row:rows)sources.add(row.original.source);
            this.sources=Collections.unmodifiableSet(sources);withinShape=classified&&rows.stream().allMatch(r->r.withinShape);
        }
    }
    private static Part clause(Clause clause,int index,Runnable work) {
        String reason=null;int heads=0;Atom head=null;Set<Integer> body=new HashSet<>();
        if(clause.literals.isEmpty()||clause.literals.size()>8||clause.variables.size()>2)reason="Outside bounded original Horn shape";
        for(Atom literal:clause.literals) {
            work.run();if(literal.arguments.size()>8)reason="Original Horn arity limit";
            if(literal.positive){++heads;head=literal;}
            else for(Slot slot:literal.arguments){work.run();if(slot.variable>=0)body.add(slot.variable);}
        }
        if(heads>1)reason="Multiple positive heads require another language plan";
        if(heads==1&&clause.literals.size()>1)for(Slot slot:head.arguments){work.run();if(slot.variable>=0&&!body.contains(slot.variable))reason="Original head variable absent from body";}
        return new Part(index,clause,reason!=null?Role.UNSUPPORTED:heads==0?Role.CONSTRAINT:Role.PRODUCER,
            reason!=null?reason:heads==0?"Headless original constraint":"Definite original producer");
    }
    private static Part witness(Entry entry,Runnable work) {
        return witness(entry,work,false);
    }
    private static Part witness(Entry entry,Runnable work,boolean signed) {
        String reason=null;ContextWitnessBundle.Bundle bundle=entry.bundle;
        if(bundle.witnesses<1||bundle.witnesses>8||bundle.atoms.isEmpty()||bundle.atoms.size()>8)reason="Outside bounded witness inventory";
        for(ContextWitnessBundle.Atom atom:bundle.atoms) {
            work.run();if(!signed&&!atom.positive)reason="Signed existential row is not a universal constraint";
            if(atom.arguments.size()>8)reason="Witness inventory arity limit";
            for(ContextWitnessBundle.Slot slot:atom.arguments){work.run();if(slot.witness>=bundle.witnesses)reason="Witness binder outside original row";}
        }
        return new Part(-1,null,reason==null?Role.WITNESS:Role.UNSUPPORTED,reason==null?(signed?"Whole signed witness row backed by audited native seed frame":"Whole positive existential row, separate materialization obligations"):reason);
    }
    static Result inspect(Snapshot x,Snapshot foreign,int maximumRows,int maximumWork) {
        if(maximumRows<1||maximumRows>64||maximumWork<1)throw new IllegalArgumentException("Bounded original row accounting required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(x==null)throw new IllegalStateException("Actual native X required");ContextProductionFamilyAssembly.identity(x,true,work);
            List<Entry> originals=new ArrayList<>(x.primary);
            if(foreign!=null){ContextProductionFamilyAssembly.identity(foreign,false,work);ContextProductionFamilyAssembly.disjoint(x,foreign,work);originals.addAll(foreign.primary);}
            if(originals.size()>maximumRows)throw new IllegalStateException("Original whole row inventory limit");
            Set<Source> sources=new HashSet<>();List<Row> rows=new ArrayList<>();
            for(Entry entry:originals) {
                work.run();if(!sources.add(entry.source))throw new IllegalStateException("Repeated original whole Source");
                List<Part> parts=new ArrayList<>();
                if(entry.bundle!=null)parts.add(witness(entry,work));
                else {
                    if(entry.clauses.isEmpty()||entry.clauses.size()>8)throw new IllegalStateException("Complete original clause row required");
                    for(int index=0;index<entry.clauses.size();++index){work.run();parts.add(clause(entry.clauses.get(index),index,work));}
                }
                rows.add(new Row(entry,parts));
            }
            return new Result(true,"All original rows classified without splitting Sources; inference, constraints and family qualification remain separate",x,foreign,rows);
        }catch(Exception failure){return new Result(false,failure.toString(),x,foreign,Collections.emptyList());}
    }
    /** Separate authenticated signed profile; old inspect remains positive-witness only. */
    static Result inspectSigned(ContextSignedWitnessFrameAudit.Receipt receipt,Snapshot target,int maximumRows,int maximumWork,int auditWork) {
        if(maximumRows<1||maximumRows>64||maximumWork<1||auditWork<1)throw new IllegalArgumentException("Bounded signed receiver rows required");
        Snapshot x=receipt==null?null:receipt.frame.plan.x;Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(receipt==null)throw new IllegalStateException("Audited signed frame required");ContextWitnessResultTransport.SignedSeedFrame f=receipt.frame;
            ContextSignedWitnessFrameAudit.check(receipt,f.plan,x,f.plan.foreign,f.plan.maximumRows,f.maximumRank,auditWork,auditWork);
            if(target!=null&&!f.plan.foreign.contains(target))throw new IllegalStateException("Different actual signed receiver cut");
            List<Entry> originals=new ArrayList<>(x.primary);if(target!=null)originals.addAll(target.primary);if(originals.size()>maximumRows)throw new IllegalStateException("Signed whole receiver row limit");Set<Source> sources=new HashSet<>();List<Row> rows=new ArrayList<>();
            for(Entry entry:originals){work.run();if(!sources.add(entry.source))throw new IllegalStateException("Repeated signed receiver Source");List<Part> parts=new ArrayList<>();if(entry.bundle!=null)parts.add(witness(entry,work,true));else{if(entry.clauses.isEmpty()||entry.clauses.size()>8)throw new IllegalStateException("Whole original signed receiver clauses required");for(int n=0;n<entry.clauses.size();n++){work.run();parts.add(clause(entry.clauses.get(n),n,work));}}rows.add(new Row(entry,parts));}
            return new Result(true,"Explicit signed witness frame profile; no continuation/admission authority",x,target,rows,receipt);
        }catch(Exception failure){return new Result(false,failure.toString(),x,target,Collections.emptyList(),null);}
    }

    /** Exact audited barrier successor; original initial signed rows remain pinned. */
    static Result inspectSignedContinuation(ContextSignedWitnessContinuationScope.Receipt scope,Snapshot target,int maximumRows,int maximumWork,int auditWork){
        Snapshot x=scope==null?null:scope.seeds.frame.plan.x;
        try{if(scope==null||maximumRows!=scope.rows)throw new IllegalStateException("Different signed successor row policy");ContextSignedWitnessContinuationScope.check(scope,auditWork,scope.capacity);Result original=inspectSigned(scope.seeds,target,maximumRows,maximumWork,auditWork);if(!original.classified)throw new IllegalStateException(original.reason);return new Result(true,"Explicit audited barrier successor rows",x,target,original.rows,scope.seeds,scope);}catch(Exception failure){return new Result(false,failure.toString(),x,target,Collections.emptyList());}
    }

}
