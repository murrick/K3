/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalNativeRows.*;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalInputResiduals.Literal;
import org.kanger.ContextWitnessResultTransport.*;

/** Whole-row rigid-Origin producer/constraint projection; not native/family qualification. */
final class ContextUniversalFrozenConstraints {
    static final class Proof {
        /** Null only for a successfully checked empty resolvent. */
        final Residual clause;
        final Support support;
        final Row original;
        final Part part;
        final Item input;
        final Support inputProof;
        final Proof unit,horn;
        final int pivot;
        private Proof(Row row,Part part,Runnable work){original=row;this.part=part;clause=ContextUniversalInputResiduals.schema(part.clause,work);support=new Support(Collections.singleton(row.original.source),0);input=null;inputProof=null;unit=null;horn=null;pivot=-1;}
        private Proof(Item input,Support support,Runnable work){this.input=input;inputProof=support;clause=ContextUniversalInputResiduals.fact(input,work);this.support=support;original=null;part=null;unit=null;horn=null;pivot=-1;}
        private Proof(Residual clause,Proof unit,Proof horn,int pivot,Runnable work){this.clause=clause;this.unit=unit;this.horn=horn;this.pivot=pivot;original=null;part=null;input=null;inputProof=null;Set<Source> sources=new LinkedHashSet<>();for(Source source:unit.support.sources){work.run();sources.add(source);}for(Source source:horn.support.sources){work.run();sources.add(source);}int depth=Math.max(unit.support.depth,horn.support.depth);if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Constraint proof depth overflow");support=new Support(sources,depth+1);}
    }
    static final class Result {
        final boolean closed,xRefuted,foreignRefuted,signedUnits;
        final String reason;
        final ContextUniversalNativeRows.Result input;
        final Packet common,local;
        final PrivatePool pool;
        final int rounds,obligations;
        final List<Proof> proofs,emptyProofs;
        private Result(boolean closed,String reason,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,boolean signedUnits,int rounds,int obligations,List<Proof> proofs) {
            this.closed=closed;this.signedUnits=signedUnits;this.reason=reason;this.input=input;this.common=common;this.pool=pool;local=pool==null?common:pool.local;this.rounds=rounds;this.obligations=obligations;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));List<Proof> empty=new ArrayList<>();boolean x=false,foreign=false;
            for(Proof proof:proofs)if(proof.clause==null){empty.add(proof);if(proof.support.sources.stream().anyMatch(s->s.localX))x=true;else foreign=true;}
            emptyProofs=Collections.unmodifiableList(empty);xRefuted=x;foreignRefuted=foreign;
        }
    }
    private static boolean same(Residual a,Residual b,Runnable work){return a==null?b==null:b!=null&&a.equivalent(b,work);}
    private static void shape(Residual clause,Runnable work) {
        if(clause==null)return;
        if(clause.literals.isEmpty()||clause.literals.size()>8||clause.variables.size()>2||clause.origins.size()>8)throw new IllegalStateException("Outside bounded rigid-Origin Horn shape");
        int heads=0;for(Literal literal:clause.literals){work.run();if(literal.positive)++heads;if(literal.arguments.size()>8)throw new IllegalStateException("Rigid-Origin Horn arity limit");}
        if(heads>1)throw new IllegalStateException("Horn producer or headless constraint required");
    }
    private static boolean unit(Proof p,boolean signed){return p.clause!=null&&p.clause.literals.size()==1&&(signed||p.clause.literals.get(0).positive);}
    private static final class Bucket {
        final Residual clause;final Map<Set<Source>,Proof> proofs=new LinkedHashMap<>();Bucket(Residual clause){this.clause=clause;}
    }
    private static final class Ledger {
        final List<Bucket> buckets=new ArrayList<>();final int clauses,alternatives;
        Ledger(int clauses,int alternatives){this.clauses=clauses;this.alternatives=alternatives;}
        boolean add(Proof proof,int depth,Runnable work) {
            shape(proof.clause,work);Bucket found=null;for(Bucket b:buckets){work.run();if(same(b.clause,proof.clause,work)){found=b;break;}}
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
    private static void verify(Proof p,ContextUniversalNativeRows.Result input,Packet local,boolean signed,Set<Proof> checked,int depth,Runnable work) {
        work.run();if(checked.contains(p))return;shape(p.clause,work);if(p.support.depth>depth)throw new IllegalStateException("Constraint proof exceeds depth bound");
        if(p.original!=null) {
            boolean found=false;for(Row row:input.rows){work.run();if(row==p.original)found=true;}
            if(!found||!p.original.parts.contains(p.part)||!p.clause.equivalent(ContextUniversalInputResiduals.schema(p.part.clause,work),work)||p.part.role!=Role.PRODUCER&&p.part.role!=Role.CONSTRAINT||p.support.depth!=0||!p.support.sources.equals(Collections.singleton(p.original.original.source)))throw new IllegalStateException("Different original constraint/producer root");
        }else if(p.input!=null) {
            boolean found=false;for(Item item:local.items){work.run();if(item==p.input)found=true;}
            if(!found||!p.input.proofs.contains(p.inputProof)||!p.support.equals(p.inputProof)||!p.clause.equivalent(ContextUniversalInputResiduals.fact(p.input,work),work))throw new IllegalStateException("Different actual carried input proof");
        }else {
            if(p.unit==null||p.horn==null||!unit(p.unit,signed))throw new IllegalStateException("Missing constraint resolution parents");verify(p.unit,input,local,signed,checked,depth,work);verify(p.horn,input,local,signed,checked,depth,work);
            Residual expected=resolve(p.unit,p.horn,p.pivot,signed,work);Proof label=new Proof(expected,p.unit,p.horn,p.pivot,work);
            if(!same(expected,p.clause,work)||!label.support.equals(p.support))throw new IllegalStateException("Different constraint resolvent/Source proof");
        }
        checked.add(p);
    }
    static void check(Result result,int maximumDepth,Runnable work) {
        if(result==null||!result.closed||result.input==null||!result.input.classified||!result.input.withinShape)throw new IllegalStateException("Closed whole rigid-Origin constraint frame required");
        frame(result.input,result.common,result.pool,maximumDepth,Integer.MAX_VALUE,work);
        Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof proof:result.proofs)verify(proof,result.input,result.local,result.signedUnits,checked,maximumDepth,work);
    }
    private static Residual resolve(Proof unit,Proof horn,int pivot,boolean signed,Runnable work) {
        return signed?ContextUniversalInputResiduals.resolveSignedUnit(unit.clause,horn.clause,pivot,work)
            :ContextUniversalInputResiduals.resolveConstraint(unit.clause,horn.clause,pivot,work);
    }
    private interface Step { void accept(Proof proof); }
    private static void steps(List<Proof> input,boolean signed,Step output,Runnable work) {
        for(Proof unit:input) {
            work.run();if(!unit(unit,signed))continue;
            for(Proof horn:input) {
            work.run();if(horn.clause==null)continue;Literal head=unit.clause.literals.get(0);
            for(int pivot=0;pivot<horn.clause.literals.size();++pivot) {
                work.run();Literal body=horn.clause.literals.get(pivot);if(body.positive==head.positive||!head.predicate.equals(body.predicate)||head.arguments.size()!=body.arguments.size())continue;
                Residual residual;try{residual=resolve(unit,horn,pivot,signed,work);}catch(ContextUniversalInputResiduals.UnificationFailure incompatible){continue;}
                output.accept(new Proof(residual,unit,horn,pivot,work));
            }
            }
        }
    }
    private static Packet frame(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int depth,int maximumWork,Runnable work) {
        if(common==null)throw new IllegalStateException("Actual common input required");
        ContextWitnessResultTransport.family(common,input.parent.equals(input.target)?Collections.singletonList(input.parent):Arrays.asList(input.parent,input.target),maximumWork);
        if(pool!=null&&(pool.common!=common||pool.x!=input.x||pool.snapshot!=input.foreign||!pool.cut.equals(input.target)))throw new IllegalStateException("Different exact private native/common frame");
        Packet local=pool==null?common:pool.local;
        for(Row row:input.rows)for(Part part:row.parts) {
            work.run();if(part.role==Role.UNSUPPORTED)throw new IllegalStateException("Unsupported whole original row");
            if(part.role!=Role.WITNESS)continue;
            for(ContextWitnessBundle.Atom atom:row.original.bundle.atoms) {
                work.run();boolean found=false;
                for(Item item:local.items)if(ContextUniversalFrozenCoverage.bundleAtom(atom,item,row.original.source,work))for(Support support:item.proofs){work.run();if(support.depth==0&&support.sources.equals(Collections.singleton(row.original.source)))found=true;}
                if(!found)throw new IllegalStateException("Missing actual full original witness row in local frame");
            }
        }
        for(Item item:local.items)for(Support support:item.proofs){work.run();if(support.depth>depth)throw new IllegalStateException("Carried input proof exceeds exploration bound");}
        return local;
    }
    static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,false);
    }
    static Result closeSigned(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true);
    }
    private static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,boolean signed) {
        if(maximumRounds<1||maximumRounds>64||maximumDepth<0||maximumDepth>64||maximumClauses<1||maximumClauses>512||maximumAlternatives<1||maximumAlternatives>256||maximumWork<1)throw new IllegalArgumentException("Bounded ordinary constraint closure required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(input==null||!input.classified||!input.withinShape)throw new IllegalStateException("Whole supported native row inventory required");
            Packet local=frame(input,common,pool,maximumDepth,maximumWork,work);
            Ledger current=new Ledger(maximumClauses,maximumAlternatives);
            for(Row row:input.rows)for(Part part:row.parts){work.run();if(part.role!=Role.WITNESS)current.add(new Proof(row,part,work),maximumDepth,work);}
            for(Item item:local.items)for(Support support:item.proofs){work.run();current.add(new Proof(item,support,work),maximumDepth,work);}
            for(int round=1;round<=maximumRounds;++round) {
                List<Proof> before=current.all(work);Ledger next=current.copy(work);boolean[] changed={false};
                steps(before,signed,proof->{changed[0]|=next.add(proof,maximumDepth,work);},work);
                if(!changed[0]) {
                    List<Proof> proofs=next.all(work);int[] obligations={0};
                    steps(proofs,signed,expected->{work.run();++obligations[0];boolean found=false;for(Proof proof:proofs){work.run();if(same(proof.clause,expected.clause,work)&&proof.support.sources.equals(expected.support.sources)&&proof.support.depth<=expected.support.depth){found=true;break;}}if(!found)throw new IllegalStateException("Uncovered exact Source/depth constraint obligation");},work);
                    Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof proof:proofs)verify(proof,input,local,signed,checked,maximumDepth,work);
                    return new Result(true,"Whole rigid-Origin producer/constraint pairs covered; native discovery, all-cut qualification and admission remain open",input,common,pool,signed,round,obligations[0],proofs);
                }
                current=next;
            }
            throw new IllegalStateException("Constraint closure round limit");
        }catch(Exception failure){return new Result(false,failure.toString(),input,common,pool,signed,0,0,Collections.emptyList());}
    }
}
