/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextWitnessResultTransport.*;

/** Complete roots and unit-resolution obligations in one positive typed projection; not family coverage. */
final class ContextUniversalFrozenCoverage {
    static final class Obligation {
        final Proof unit,horn,cover;
        final int pivot;
        final Residual expected;
        final Support support;
        private Obligation(Proof unit,Proof horn,int pivot,Residual expected,Support support,Proof cover) {
            this.unit=unit;this.horn=horn;this.pivot=pivot;this.expected=expected;this.support=support;this.cover=cover;
        }
    }
    static final class Result {
        final boolean covered;
        final String reason;
        final ContextUniversalFrozenClosure.Result input;
        final Snapshot x,foreign;
        final int rows,clauses,witnessAtoms,inputAlternatives,attempts;
        final List<Obligation> obligations;
        private Result(boolean covered,String reason,ContextUniversalFrozenClosure.Result input,Snapshot x,Snapshot foreign,
                int rows,int clauses,int witnessAtoms,int inputAlternatives,int attempts,List<Obligation> obligations) {
            this.covered=covered;this.reason=reason;this.input=input;this.x=x;this.foreign=foreign;
            this.rows=rows;this.clauses=clauses;this.witnessAtoms=witnessAtoms;this.inputAlternatives=inputAlternatives;this.attempts=attempts;
            this.obligations=Collections.unmodifiableList(new ArrayList<>(obligations));
        }
    }
    private static Proof cover(ContextUniversalFrozenClosure.Result input,Residual expected,Support support,Runnable work) {
        for(Proof proof:input.proofs){work.run();if(proof.support.sources.equals(support.sources)&&proof.support.depth<=support.depth&&proof.clause.equivalent(expected,work))return proof;}
        throw new IllegalStateException("Uncovered typed clause or exact whole Source/depth alternative");
    }
    static boolean bundleAtom(ContextWitnessBundle.Atom expected,Item actual,Source source,Runnable work) {
        work.run();if(!expected.positive||!actual.atom.positive||!expected.predicate.equals(actual.atom.predicate)||expected.arguments.size()!=actual.atom.arguments.size())return false;
        for(int i=0;i<expected.arguments.size();++i) {
            work.run();ContextWitnessBundle.Slot a=expected.arguments.get(i);ContextWitnessResultTransport.Slot b=actual.atom.arguments.get(i);
            if(a.witness>=0) {if(b.origin==null||!source.equals(b.origin.source)||a.witness!=b.origin.binder)return false;}
            else if(b.origin!=null||!a.value.semanticallyEquals(b.value))return false;
        }
        return true;
    }
    private static void roots(ContextUniversalFrozenClosure.Result input,Snapshot x,Snapshot foreign,int[] counts,Runnable work) {
        ContextProductionFamilyAssembly.identity(x,true,work);
        if(!Cut.of(x).equals(input.parent)||!Objects.equals(foreign==null?Cut.of(x):Cut.of(foreign),input.target))
            throw new IllegalStateException("Typed projection has different owning native cuts");
        if(foreign!=null){ContextProductionFamilyAssembly.identity(foreign,false,work);ContextProductionFamilyAssembly.disjoint(x,foreign,work);}
        if(input.frame.pool!=null&&(input.frame.pool.x!=x||input.frame.pool.snapshot!=foreign))
            throw new IllegalStateException("Different exact private native snapshots");
        List<Entry> originals=new ArrayList<>(x.primary);if(foreign!=null)originals.addAll(foreign.primary);
        Set<Entry> allowed=Collections.newSetFromMap(new IdentityHashMap<Entry,Boolean>());allowed.addAll(originals);
        Set<Source> sources=new LinkedHashSet<>(),separate=new LinkedHashSet<>();
        for(ContextUniversalUnitClosure.Proof proof:input.frame.theory.proofs) {
            work.run();if(proof.original!=null&&!allowed.contains(proof.original))throw new IllegalStateException("Schema root is not the actual native Entry");
        }
        for(Entry entry:originals) {
            work.run();if(!sources.add(entry.source))throw new IllegalStateException("Repeated original native Source");++counts[0];
            if(entry.bundle!=null) {
                separate.add(entry.source);
                for(ContextWitnessBundle.Atom atom:entry.bundle.atoms) {
                    work.run();boolean found=false;
                    for(Item item:input.frame.local.items)if(bundleAtom(atom,item,entry.source,work))for(Support support:item.proofs) {
                        work.run();if(support.depth==0&&support.sources.equals(Collections.singleton(entry.source))){found=true;break;}
                    }
                    if(!found)throw new IllegalStateException("Missing full original witness row in owning local input");++counts[2];
                }
            }else {
                for(Clause clause:entry.clauses) {
                    work.run();boolean found=false;
                    for(ContextUniversalUnitClosure.Proof proof:input.frame.theory.proofs) {
                        work.run();if(proof.original==entry&&proof.support.depth==0&&proof.support.sources.equals(Collections.singleton(entry.source))&&proof.clause.equivalent(clause)){found=true;break;}
                    }
                    if(!found)throw new IllegalStateException("Missing original whole-row schema clause");++counts[1];
                }
            }
        }
        if(!separate.equals(input.frame.theory.separateWitnessSources))throw new IllegalStateException("Different complete separate witness Source inventory");
        for(ContextUniversalUnitClosure.Proof schema:input.frame.theory.proofs) {
            work.run();cover(input,ContextUniversalInputResiduals.schema(schema.clause,work),schema.support,work);
        }
        for(Item item:input.frame.local.items)for(Support support:item.proofs) {
            work.run();cover(input,ContextUniversalInputResiduals.fact(item,work),support,work);++counts[3];
        }
    }
    static Result inspect(ContextUniversalFrozenClosure.Result input,Snapshot x,Snapshot foreign,int maximumDepth,int maximumObligations,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumObligations<1||maximumObligations>65536||maximumWork<1)
            throw new IllegalArgumentException("Bounded typed projection audit required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            ContextUniversalFrozenClosure.check(input,maximumDepth,work);int[] counts=new int[4];roots(input,x,foreign,counts,work);
            List<Obligation> obligations=new ArrayList<>();int attempts=0;
            for(Proof unit:input.proofs) {
                work.run();if(unit.clause.literals.size()!=1)continue;
                ContextUniversalInputResiduals.Literal head=unit.clause.literals.get(0);
                for(Proof horn:input.proofs) {
                    work.run();if(horn.clause.literals.size()<2)continue;
                    for(int pivot=0;pivot<horn.clause.literals.size();++pivot) {
                        work.run();ContextUniversalInputResiduals.Literal literal=horn.clause.literals.get(pivot);
                        if(literal.positive||!head.predicate.equals(literal.predicate)||head.arguments.size()!=literal.arguments.size())continue;++attempts;
                        Residual expected;try{expected=ContextUniversalInputResiduals.resolve(unit.clause,horn.clause,pivot,work);}
                        catch(ContextUniversalInputResiduals.UnificationFailure incompatible){continue;}
                        Set<Source> sources=new LinkedHashSet<>();for(Source source:unit.support.sources){work.run();sources.add(source);}
                        for(Source source:horn.support.sources){work.run();sources.add(source);}
                        int depth=Math.max(unit.support.depth,horn.support.depth);if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Typed audit depth overflow");
                        Support support=new Support(sources,depth+1);Proof covered=cover(input,expected,support,work);
                        if(obligations.size()==maximumObligations)throw new IllegalStateException("Typed audit obligation limit");
                        obligations.add(new Obligation(unit,horn,pivot,expected,support,covered));
                    }
                }
            }
            return new Result(true,"Whole native roots/input and all typed unit-resolution pairs covered; instantiation/transport/family qualification remain separate",input,x,foreign,
                    counts[0],counts[1],counts[2],counts[3],attempts,obligations);
        }catch(Exception failure){return new Result(false,failure.toString(),input,x,foreign,0,0,0,0,0,Collections.emptyList());}
    }
}
