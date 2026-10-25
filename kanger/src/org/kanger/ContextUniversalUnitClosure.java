/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;

/** Complete bounded unit-resolution ledger for one detached X/foreign frame, not family admission. */
final class ContextUniversalUnitClosure {
    static final class Proof {
        final Clause clause;
        final Support support;
        final Entry original;
        final int originalClause,pivot;
        final Proof unit,horn;
        private Proof(Entry original,int clause) {
            this.original=original;originalClause=clause;this.clause=original.clauses.get(clause);
            support=new Support(Collections.singleton(original.source),0);unit=null;horn=null;pivot=-1;
        }
        private Proof(Clause clause,Proof unit,Proof horn,int pivot) {
            this.clause=clause;this.unit=unit;this.horn=horn;this.pivot=pivot;original=null;originalClause=-1;
            Set<Source> sources=new LinkedHashSet<>(unit.support.sources);sources.addAll(horn.support.sources);
            support=new Support(sources,Math.max(unit.support.depth,horn.support.depth)+1);
        }
    }
    static final class Result {
        final boolean unitClosed;
        final String reason;
        final Cut parent,target;
        final int rounds,obligations;
        final List<Proof> proofs;
        final Set<Source> separateWitnessSources;
        private Result(boolean closed,String reason,Cut parent,Cut target,int rounds,int obligations,List<Proof> proofs,Set<Source> separate) {
            unitClosed=closed;this.reason=reason;this.parent=parent;this.target=target;this.rounds=rounds;this.obligations=obligations;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));separateWitnessSources=Collections.unmodifiableSet(new LinkedHashSet<>(separate));
        }
    }
    private static final class Row {
        final Clause clause;
        final Map<Set<Source>,Proof> alternatives=new LinkedHashMap<>();
        Row(Clause clause){this.clause=clause;}
    }
    private static final class Ledger {
        final List<Row> rows=new ArrayList<>();final int maximumClauses,maximumAlternatives;
        Ledger(int clauses,int alternatives){maximumClauses=clauses;maximumAlternatives=alternatives;}
        List<Proof> all(Runnable work){List<Proof> proofs=new ArrayList<>();for(Row row:rows)for(Proof proof:row.alternatives.values()){work.run();proofs.add(proof);}return proofs;}
        Ledger copy(Runnable work){Ledger copy=new Ledger(maximumClauses,maximumAlternatives);for(Row row:rows){work.run();Row next=new Row(row.clause);next.alternatives.putAll(row.alternatives);copy.rows.add(next);}return copy;}
        boolean add(Proof proof,int maximumDepth,Runnable work) {
            Row found=null;for(Row row:rows){work.run();if(row.clause.equivalent(proof.clause)){found=row;break;}}
            Proof old=found==null?null:found.alternatives.get(proof.support.sources);
            if(old!=null&&old.support.depth<=proof.support.depth)return false;
            if(proof.support.depth>maximumDepth)throw new IllegalStateException("Uncovered symbolic unit proof exceeds depth limit");
            if(found==null){if(rows.size()==maximumClauses)throw new IllegalStateException("Symbolic unit clause limit");found=new Row(proof.clause);rows.add(found);}
            if(old==null&&found.alternatives.size()==maximumAlternatives)throw new IllegalStateException("Symbolic unit source alternative limit");
            found.alternatives.put(proof.support.sources,proof);return true;
        }
    }
    private static void shape(Clause clause,boolean primary,Runnable work) {
        if(clause.literals.isEmpty()||clause.literals.size()>8||clause.variables.size()>2)throw new IllegalStateException("Outside bounded symbolic Horn shape");
        Atom head=null;Set<Integer> body=new HashSet<>();
        for(Atom literal:clause.literals) {
            work.run();if(literal.arguments.size()>8)throw new IllegalStateException("Symbolic Horn arity limit");
            if(literal.positive){if(head!=null)throw new IllegalStateException("Definite positive Horn clause required");head=literal;}
            else for(Slot slot:literal.arguments){work.run();if(slot.variable>=0)body.add(slot.variable);}
        }
        if(head==null)throw new IllegalStateException("Positive Horn head required");
        if(primary&&clause.literals.size()>1)for(Slot slot:head.arguments){work.run();if(slot.variable>=0&&!body.contains(slot.variable))throw new IllegalStateException("Primary range-restricted Horn clause required");}
    }
    private static boolean unit(Proof proof){return proof.clause.literals.size()==1&&proof.clause.literals.get(0).positive;}
    private static void verify(Proof proof,Set<Source> allowed,int maximumDepth,Set<Proof> verified,Runnable work) {
        work.run();if(verified.contains(proof))return;
        if(!allowed.containsAll(proof.support.sources)||proof.support.depth>maximumDepth)throw new IllegalStateException("Symbolic proof outside owning sources/depth");
        shape(proof.clause,proof.original!=null,work);
        if(proof.original!=null) {
            if(proof.originalClause<0||proof.originalClause>=proof.original.clauses.size()||proof.clause!=proof.original.clauses.get(proof.originalClause)
                    ||proof.support.depth!=0||!proof.support.sources.equals(Collections.singleton(proof.original.source)))throw new IllegalStateException("Different primary whole schema owner");
        } else {
            if(proof.unit==null||proof.horn==null||!unit(proof.unit))throw new IllegalStateException("Missing symbolic unit derivation");
            verify(proof.unit,allowed,maximumDepth,verified,work);verify(proof.horn,allowed,maximumDepth,verified,work);
            Clause expected=proof.unit.clause.resolveWithUnits(proof.horn.clause,0,proof.pivot,work);
            Set<Source> sources=new LinkedHashSet<>(proof.unit.support.sources);sources.addAll(proof.horn.support.sources);
            if(expected==null||!expected.equivalent(proof.clause)||!sources.equals(proof.support.sources)
                    ||proof.support.depth!=Math.max(proof.unit.support.depth,proof.horn.support.depth)+1)throw new IllegalStateException("Different symbolic unit derivation");
        }
        verified.add(proof);
    }
    /** Recheck the sealed proof DAG and its exact owning cuts at a native replay boundary. */
    static void check(Result result,int maximumDepth,Runnable work) {
        if(result==null||!result.unitClosed||result.parent==null||result.target==null)throw new IllegalStateException("Closed symbolic frame required");
        Set<Source> roots=new LinkedHashSet<>();
        for(Proof proof:result.proofs) {
            work.run();if(proof.original==null)continue;Source source=proof.original.source;Cut actual=Cut.of(source);
            if(source.localX?!actual.equals(result.parent):result.target.localX||!result.target.contains(actual))throw new IllegalStateException("Symbolic root outside exact replay cuts");
            roots.add(source);
        }
        Set<Proof> verified=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());
        for(Proof proof:result.proofs)verify(proof,roots,maximumDepth,verified,work);
    }
    static Result close(Snapshot x,Snapshot foreign,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(x==null||maximumRounds<1||maximumRounds>64||maximumDepth<0||maximumDepth>64||maximumClauses<1||maximumClauses>512
                ||maximumAlternatives<1||maximumAlternatives>256||maximumWork<1)throw new IllegalArgumentException("Bounded symbolic unit frame required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);Cut parent=null,target=null;Set<Source> separate=new LinkedHashSet<>();
        try {
            ContextProductionFamilyAssembly.identity(x,true,work);parent=Cut.of(x);target=foreign==null?parent:Cut.of(foreign);
            List<Entry> originals=new ArrayList<>(x.primary);
            if(foreign!=null) {
                ContextProductionFamilyAssembly.identity(foreign,false,work);
                if(target.contexts().contains(parent.context))throw new IllegalStateException("Symbolic foreign frame overlaps X");
                originals.addAll(foreign.primary);
            }
            if(originals.size()>64)throw new IllegalStateException("Symbolic whole source limit");
            Set<Source> allowed=new LinkedHashSet<>();Ledger current=new Ledger(maximumClauses,maximumAlternatives);
            for(Entry entry:originals) {
                work.run();if(!allowed.add(entry.source))throw new IllegalStateException("Repeated symbolic whole source");
                if(entry.bundle!=null){separate.add(entry.source);continue;}
                if(entry.clauses.isEmpty()||entry.clauses.size()>8)throw new IllegalStateException("Complete symbolic whole row required");
                for(Clause clause:entry.clauses)shape(clause,true,work); // Preflight every clause before using this Source.
                for(int i=0;i<entry.clauses.size();++i)current.add(new Proof(entry,i),maximumDepth,work);
            }
            for(int round=1;round<=maximumRounds;++round) {
                List<Proof> input=current.all(work);Ledger next=current.copy(work);boolean changed=false;int obligations=0;
                for(Proof unit:input) {
                    work.run();if(!unit(unit))continue;
                    for(Proof horn:input) {
                        work.run();if(horn.clause.literals.size()<2)continue;
                        for(int pivot=0;pivot<horn.clause.literals.size();++pivot) {
                            work.run();Atom literal=horn.clause.literals.get(pivot),head=unit.clause.literals.get(0);
                            if(literal.positive||!literal.predicate.equals(head.predicate)||literal.arguments.size()!=head.arguments.size())continue;
                            Clause residual;try{residual=unit.clause.resolveWithUnits(horn.clause,0,pivot,work);}catch(Clause.UnificationFailure incompatible){continue;}
                            if(residual==null)throw new IllegalStateException("Unexpected empty positive unit resolvent");
                            shape(residual,false,work);++obligations;changed|=next.add(new Proof(residual,unit,horn,pivot),maximumDepth,work);
                        }
                    }
                }
                if(!changed) {
                    List<Proof> proofs=next.all(work);Set<Proof> verified=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());
                    for(Proof proof:proofs)verify(proof,allowed,maximumDepth,verified,work);
                    return new Result(true,"All unit/Horn pairs and exact source alternatives closed in this schema projection; witness Sources remain separate, not family admission",parent,target,round,obligations,proofs,separate);
                }
                current=next;
            }
            throw new IllegalStateException("Symbolic unit round limit");
        }catch(Exception failure){return new Result(false,failure.getMessage(),parent,target,0,0,Collections.emptyList(),separate);}
    }
}
