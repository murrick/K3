/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalInputResiduals.Frame;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalInputResiduals.Literal;
import org.kanger.ContextWitnessResultTransport.*;

/** Bounded positive unit resolution over universal schemas and rigid-origin input facts. No admission authority. */
final class ContextUniversalFrozenClosure {
    static final class Proof {
        final Residual clause;
        final Support support;
        final ContextUniversalUnitClosure.Proof schema;
        final Item input;
        final Support inputProof;
        final Proof unit,horn;
        final int pivot;
        private Proof(ContextUniversalUnitClosure.Proof schema,Runnable work) {
            this.schema=schema;input=null;inputProof=null;unit=null;horn=null;pivot=-1;
            clause=ContextUniversalInputResiduals.schema(schema.clause,work);support=schema.support;
        }
        private Proof(Item input,Support support,Runnable work) {
            schema=null;this.input=input;inputProof=support;unit=null;horn=null;pivot=-1;
            clause=ContextUniversalInputResiduals.fact(input,work);this.support=support;
        }
        private Proof(Residual clause,Proof unit,Proof horn,int pivot,Runnable work) {
            this.clause=clause;this.unit=unit;this.horn=horn;this.pivot=pivot;schema=null;input=null;inputProof=null;
            Set<Source> sources=new LinkedHashSet<>();for(Source source:unit.support.sources){work.run();sources.add(source);}
            for(Source source:horn.support.sources){work.run();sources.add(source);}
            int depth=Math.max(unit.support.depth,horn.support.depth);if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Typed closure depth overflow");
            support=new Support(sources,depth+1);
        }
    }
    static final class Result {
        final boolean closed,privateInput;
        final String reason;
        final UUID basis,localBasis;
        final Cut parent,target;
        final int rounds,obligations;
        final List<Proof> proofs;
        final Frame frame;
        private Result(boolean closed,String reason,Frame frame,int rounds,int obligations,List<Proof> proofs) {
            this.closed=closed;this.reason=reason;this.frame=frame;this.rounds=rounds;this.obligations=obligations;
            privateInput=frame!=null&&frame.pool!=null;basis=frame==null?null:frame.common.identity;localBasis=frame==null?null:frame.local.identity;
            parent=frame==null?null:frame.theory.parent;target=frame==null?null:frame.theory.target;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    private static final class Row {
        final Residual clause;final Map<Set<Source>,Proof> alternatives=new LinkedHashMap<>();
        Row(Residual clause){this.clause=clause;}
    }
    private static final class Ledger {
        final List<Row> rows=new ArrayList<>();final int maximumClauses,maximumAlternatives;boolean uncoveredDepth;
        Ledger(int clauses,int alternatives){maximumClauses=clauses;maximumAlternatives=alternatives;}
        List<Proof> all(Runnable work){List<Proof> out=new ArrayList<>();for(Row row:rows)for(Proof proof:row.alternatives.values()){work.run();out.add(proof);}return out;}
        Ledger copy(Runnable work){Ledger next=new Ledger(maximumClauses,maximumAlternatives);for(Row row:rows){work.run();Row out=new Row(row.clause);out.alternatives.putAll(row.alternatives);next.rows.add(out);}return next;}
        boolean add(Proof proof,int maximumDepth,Runnable work) {
            shape(proof.clause,work);Row found=null;
            for(Row row:rows){work.run();if(row.clause.equivalent(proof.clause,work)){found=row;break;}}
            Proof old=found==null?null:found.alternatives.get(proof.support.sources);
            if(old!=null&&old.support.depth<=proof.support.depth)return false;
            if(proof.support.depth>maximumDepth){uncoveredDepth=true;return false;}
            if(found==null){if(rows.size()==maximumClauses)throw new IllegalStateException("Typed closure clause limit");found=new Row(proof.clause);rows.add(found);}
            if(old==null&&found.alternatives.size()==maximumAlternatives)throw new IllegalStateException("Typed closure Source alternative limit");
            found.alternatives.put(proof.support.sources,proof);return true;
        }
    }
    private static void shape(Residual clause,Runnable work) {
        if(clause.literals.isEmpty()||clause.literals.size()>8||clause.variables.size()>2||clause.origins.size()>8)throw new IllegalStateException("Outside bounded typed Horn shape");
        int heads=0;for(Literal literal:clause.literals){work.run();if(literal.positive)++heads;if(literal.arguments.size()>8)throw new IllegalStateException("Typed Horn arity limit");}
        if(heads!=1)throw new IllegalStateException("Typed positive definite Horn required");
    }
    private static boolean unit(Proof proof){return proof.clause.literals.size()==1&&proof.clause.literals.get(0).positive;}
    private static void verify(Proof proof,Frame frame,Set<Proof> verified,int maximumDepth,Runnable work) {
        work.run();if(verified.contains(proof))return;shape(proof.clause,work);
        if(proof.support.depth>maximumDepth)throw new IllegalStateException("Typed proof exceeds owning depth bound");
        if(proof.schema!=null) {
            boolean found=false;for(ContextUniversalUnitClosure.Proof original:frame.theory.proofs){work.run();if(original==proof.schema){found=true;break;}}
            if(!found||!proof.support.equals(proof.schema.support)||!proof.clause.equivalent(ContextUniversalInputResiduals.schema(proof.schema.clause,work),work))
                throw new IllegalStateException("Different checked typed schema root");
        }else if(proof.input!=null) {
            boolean found=false;for(Item item:frame.local.items){work.run();if(item==proof.input){found=true;break;}}
            if(!found||!proof.input.proofs.contains(proof.inputProof)||!proof.support.equals(proof.inputProof)
                    ||!proof.clause.equivalent(ContextUniversalInputResiduals.fact(proof.input,work),work))throw new IllegalStateException("Different authenticated typed input root");
        }else {
            if(proof.unit==null||proof.horn==null||!unit(proof.unit))throw new IllegalStateException("Missing typed resolution proof parents");
            verify(proof.unit,frame,verified,maximumDepth,work);verify(proof.horn,frame,verified,maximumDepth,work);
            Residual expected=ContextUniversalInputResiduals.resolve(proof.unit.clause,proof.horn.clause,proof.pivot,work);
            Proof label=new Proof(expected,proof.unit,proof.horn,proof.pivot,work);
            if(!expected.equivalent(proof.clause,work)||!label.support.equals(proof.support))throw new IllegalStateException("Different typed resolution proof");
        }
        verified.add(proof);
    }
    static void check(Result result,int maximumDepth,Runnable work) {
        if(result==null||!result.closed||result.frame==null)throw new IllegalStateException("Closed typed unit frame required");
        ContextUniversalUnitClosure.check(result.frame.theory,maximumDepth,work);
        Set<Proof> verified=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());
        for(Proof proof:result.proofs)verify(proof,result.frame,verified,maximumDepth,work);
    }
    static Result close(ContextUniversalUnitClosure.Result theory,Packet input,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(theory,input,input,null,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork);
    }
    static Result closePrivate(ContextUniversalUnitClosure.Result theory,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(pool==null)throw new IllegalArgumentException("Sealed private typed input required");
        return close(theory,pool.common,pool.local,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork);
    }
    private static Result close(ContextUniversalUnitClosure.Result theory,Packet common,Packet local,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(maximumRounds<1||maximumRounds>64||maximumDepth<0||maximumDepth>64||maximumClauses<1||maximumClauses>512||maximumAlternatives<1||maximumAlternatives>256||maximumWork<1)
            throw new IllegalArgumentException("Bounded typed unit closure required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);Frame frame=null;
        try {
            frame=ContextUniversalInputResiduals.frame(theory,common,local,pool,maximumDepth,maximumWork,work);
            Ledger current=new Ledger(maximumClauses,maximumAlternatives);
            for(ContextUniversalUnitClosure.Proof schema:theory.proofs){work.run();current.add(new Proof(schema,work),maximumDepth,work);}
            for(Item item:local.items)for(Support proof:item.proofs){work.run();current.add(new Proof(item,proof,work),maximumDepth,work);}
            for(int round=1;round<=maximumRounds;++round) {
                List<Proof> input=current.all(work);Ledger next=current.copy(work);int obligations=0;boolean changed=false;
                for(Proof unit:input) {
                    work.run();if(!unit(unit))continue;
                    for(Proof horn:input) {
                        work.run();if(horn.clause.literals.size()<2)continue;
                        for(int pivot=0;pivot<horn.clause.literals.size();++pivot) {
                            work.run();Literal literal=horn.clause.literals.get(pivot),head=unit.clause.literals.get(0);
                            if(literal.positive||!head.predicate.equals(literal.predicate)||head.arguments.size()!=literal.arguments.size())continue;
                            Residual residual;try{residual=ContextUniversalInputResiduals.resolve(unit.clause,horn.clause,pivot,work);}
                            catch(ContextUniversalInputResiduals.UnificationFailure incompatible){continue;}
                            ++obligations;changed|=next.add(new Proof(residual,unit,horn,pivot,work),maximumDepth,work);
                        }
                    }
                }
                if(!changed) {
                    if(next.uncoveredDepth)throw new IllegalStateException("Uncovered typed unit proof exceeds depth limit at stable bounded barrier");
                    List<Proof> proofs=next.all(work);Set<Proof> verified=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());
                    for(Proof proof:proofs)verify(proof,frame,verified,maximumDepth,work);
                    return new Result(true,"Typed positive unit/Horn projection closed with checked proof DAGs; native replay and family admission remain open",frame,round,obligations,proofs);
                }
                current=next;
            }
            throw new IllegalStateException("Typed unit closure round limit");
        }catch(Exception failure){return new Result(false,failure.getMessage(),frame,0,0,Collections.emptyList());}
    }
}
