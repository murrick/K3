/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalNativeRows.*;

/** Whole-ledger ordinary Horn unit resolution including constraints; no frozen witness/family authority. */
final class ContextUniversalConstraintClosure {
    static final class Proof {
        /** Null only for a successfully checked empty resolvent. */
        final Clause clause;
        final Support support;
        final Row original;
        final Part part;
        final Proof unit,horn;
        final int pivot;
        private Proof(Row row,Part part){original=row;this.part=part;clause=part.clause;support=new Support(Collections.singleton(row.original.source),0);unit=null;horn=null;pivot=-1;}
        private Proof(Clause clause,Proof unit,Proof horn,int pivot){this.clause=clause;this.unit=unit;this.horn=horn;this.pivot=pivot;original=null;part=null;Set<Source> sources=new LinkedHashSet<>(unit.support.sources);sources.addAll(horn.support.sources);int depth=Math.max(unit.support.depth,horn.support.depth);if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Constraint proof depth overflow");support=new Support(sources,depth+1);}
    }
    static final class Result {
        final boolean closed,xRefuted,foreignRefuted;
        final String reason;
        final ContextUniversalNativeRows.Result input;
        final int rounds,obligations;
        final List<Proof> proofs,emptyProofs;
        private Result(boolean closed,String reason,ContextUniversalNativeRows.Result input,int rounds,int obligations,List<Proof> proofs) {
            this.closed=closed;this.reason=reason;this.input=input;this.rounds=rounds;this.obligations=obligations;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));List<Proof> empty=new ArrayList<>();boolean x=false,foreign=false;
            for(Proof proof:proofs)if(proof.clause==null){empty.add(proof);if(proof.support.sources.stream().anyMatch(s->s.localX))x=true;else foreign=true;}
            emptyProofs=Collections.unmodifiableList(empty);xRefuted=x;foreignRefuted=foreign;
        }
    }
    private static boolean same(Clause a,Clause b){return a==null?b==null:b!=null&&a.equivalent(b);}
    private static boolean unit(Proof p){return p.clause!=null&&p.clause.literals.size()==1&&p.clause.literals.get(0).positive;}
    private static final class Bucket {
        final Clause clause;final Map<Set<Source>,Proof> proofs=new LinkedHashMap<>();Bucket(Clause clause){this.clause=clause;}
    }
    private static final class Ledger {
        final List<Bucket> buckets=new ArrayList<>();final int clauses,alternatives;
        Ledger(int clauses,int alternatives){this.clauses=clauses;this.alternatives=alternatives;}
        boolean add(Proof proof,int depth,Runnable work) {
            Bucket found=null;for(Bucket b:buckets){work.run();if(same(b.clause,proof.clause)){found=b;break;}}
            Proof old=found==null?null:found.proofs.get(proof.support.sources);
            if(old!=null&&old.support.depth<=proof.support.depth)return false;
            if(proof.support.depth>depth)return false; // Final pair audit must still find a shorter exact-Source cover.
            if(found==null){if(buckets.size()==clauses)throw new IllegalStateException("Constraint clause limit");found=new Bucket(proof.clause);buckets.add(found);}
            if(old==null&&found.proofs.size()==alternatives)throw new IllegalStateException("Constraint Source alternative limit");
            found.proofs.put(proof.support.sources,proof);return true;
        }
        List<Proof> all(Runnable work){List<Proof> out=new ArrayList<>();for(Bucket b:buckets)for(Proof p:b.proofs.values()){work.run();out.add(p);}return out;}
        Ledger copy(Runnable work){Ledger next=new Ledger(clauses,alternatives);for(Bucket b:buckets){work.run();Bucket copy=new Bucket(b.clause);copy.proofs.putAll(b.proofs);next.buckets.add(copy);}return next;}
    }
    private static void verify(Proof p,ContextUniversalNativeRows.Result input,Set<Proof> checked,int depth,Runnable work) {
        work.run();if(checked.contains(p))return;if(p.support.depth>depth)throw new IllegalStateException("Constraint proof exceeds depth bound");
        if(p.original!=null) {
            boolean found=false;for(Row row:input.rows){work.run();if(row==p.original)found=true;}
            if(!found||!p.original.parts.contains(p.part)||p.part.clause!=p.clause||p.part.role!=Role.PRODUCER&&p.part.role!=Role.CONSTRAINT||p.support.depth!=0||!p.support.sources.equals(Collections.singleton(p.original.original.source)))throw new IllegalStateException("Different original constraint/producer root");
        }else {
            if(p.unit==null||p.horn==null||!unit(p.unit))throw new IllegalStateException("Missing constraint resolution parents");verify(p.unit,input,checked,depth,work);verify(p.horn,input,checked,depth,work);
            Clause expected=p.unit.clause.resolveWithUnits(p.horn.clause,0,p.pivot,work);Proof label=new Proof(expected,p.unit,p.horn,p.pivot);
            if(!same(expected,p.clause)||!label.support.equals(p.support))throw new IllegalStateException("Different constraint resolvent/Source proof");
        }
        checked.add(p);
    }
    static void check(Result result,int maximumDepth,Runnable work) {
        if(result==null||!result.closed||result.input==null||!result.input.classified||!result.input.withinShape)throw new IllegalStateException("Closed whole ordinary constraint frame required");
        Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof proof:result.proofs)verify(proof,result.input,checked,maximumDepth,work);
    }
    private interface Step { void accept(Proof proof); }
    private static void steps(List<Proof> input,Step output,Runnable work) {
        for(Proof unit:input) {
            work.run();if(!unit(unit))continue;
            for(Proof horn:input) {
            work.run();if(horn.clause==null)continue;Atom head=unit.clause.literals.get(0);
            for(int pivot=0;pivot<horn.clause.literals.size();++pivot) {
                work.run();Atom body=horn.clause.literals.get(pivot);if(body.positive||!head.predicate.equals(body.predicate)||head.arguments.size()!=body.arguments.size())continue;
                Clause residual;try{residual=unit.clause.resolveWithUnits(horn.clause,0,pivot,work);}catch(Clause.UnificationFailure incompatible){continue;}
                output.accept(new Proof(residual,unit,horn,pivot));
            }
            }
        }
    }
    static Result close(ContextUniversalNativeRows.Result input,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(maximumRounds<1||maximumRounds>64||maximumDepth<0||maximumDepth>64||maximumClauses<1||maximumClauses>512||maximumAlternatives<1||maximumAlternatives>256||maximumWork<1)throw new IllegalArgumentException("Bounded ordinary constraint closure required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(input==null||!input.classified||!input.withinShape)throw new IllegalStateException("Whole supported native row inventory required");
            // Preflight the entire original inventory before adding any root, including a mixed Source.
            for(Row row:input.rows)for(Part part:row.parts){work.run();if(part.role!=Role.PRODUCER&&part.role!=Role.CONSTRAINT)throw new IllegalStateException("Witness materialization and unsupported rows remain separate obligations");}
            Ledger current=new Ledger(maximumClauses,maximumAlternatives);for(Row row:input.rows)for(Part part:row.parts){work.run();current.add(new Proof(row,part),maximumDepth,work);}
            for(int round=1;round<=maximumRounds;++round) {
                List<Proof> before=current.all(work);Ledger next=current.copy(work);boolean[] changed={false};
                steps(before,proof->{changed[0]|=next.add(proof,maximumDepth,work);},work);
                if(!changed[0]) {
                    List<Proof> proofs=next.all(work);int[] obligations={0};
                    steps(proofs,expected->{work.run();++obligations[0];boolean found=false;for(Proof proof:proofs){work.run();if(same(proof.clause,expected.clause)&&proof.support.sources.equals(expected.support.sources)&&proof.support.depth<=expected.support.depth){found=true;break;}}if(!found)throw new IllegalStateException("Uncovered exact Source/depth constraint obligation");},work);
                    Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof proof:proofs)verify(proof,input,checked,maximumDepth,work);
                    return new Result(true,"Whole ordinary Horn producer/constraint unit-resolution pairs covered; witness/native/family qualification remains separate",input,round,obligations[0],proofs);
                }
                current=next;
            }
            throw new IllegalStateException("Constraint closure round limit");
        }catch(Exception failure){return new Result(false,failure.toString(),input,0,0,Collections.emptyList());}
    }
}
