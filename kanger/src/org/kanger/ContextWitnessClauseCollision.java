/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.Cut;
import org.kanger.ContextWitnessResultTransport.Item;
import org.kanger.ContextWitnessResultTransport.Packet;
import org.kanger.ContextWitnessResultTransport.PrivatePool;
import org.kanger.ContextWitnessResultTransport.Slot;

/** Positive refutations only: universal elimination and a falsified sourced clause. */
final class ContextWitnessClauseCollision {
    enum Status { COLLISION, INCOMPLETE }
    static final class Evidence {
        final Source producer;
        final Clause clause;
        final Map<Integer,Slot> assignment;
        /** One opposite proved Item per clause literal, in the same order. */
        final List<Item> opponents;
        final List<Support> proofs;
        private Evidence(Source producer,Clause clause,Map<Integer,Slot> assignment,List<Item> opponents,List<Support> proofs) {
            this.producer = producer; this.clause = clause;
            this.assignment = Collections.unmodifiableMap(new LinkedHashMap<>(assignment));
            this.opponents = Collections.unmodifiableList(new ArrayList<>(opponents));
            this.proofs = Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    static final class Result {
        final Status status;
        final String reason;
        final UUID inputIdentity;
        final Cut cut;
        final List<Evidence> evidence;
        /** Exhaustive direct-instance scan only, never consistency or inference coverage. */
        final boolean scanned;
        private Result(Status status,String reason,Packet input,Cut cut,List<Evidence> evidence,boolean scanned) {
            this.status = status; this.reason = reason; inputIdentity = input.identity; this.cut = cut;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
            this.scanned = scanned;
        }
    }
    private static boolean same(Slot left,Slot right) {
        return left.origin != null || right.origin != null ? Objects.equals(left.origin,right.origin)
                : left.value.semanticallyEquals(right.value);
    }
    /** Shared exact opposite-literal instance match; each caller owns its tentative assignment. */
    static boolean match(ContextProductionCapture.Atom literal,Item opponent,Map<Integer,Slot> assignment,Runnable work) {
        work.run();
        if (!literal.predicate.equals(opponent.atom.predicate) || literal.positive == opponent.atom.positive
                || literal.arguments.size() != opponent.atom.arguments.size()) return false;
        for (int i=0;i<literal.arguments.size();++i) {
            work.run(); ContextProductionCapture.Slot formal = literal.arguments.get(i); Slot actual = opponent.atom.arguments.get(i);
            if (formal.variable >= 0) {
                Slot prior = assignment.putIfAbsent(formal.variable,actual);
                if (prior != null && !same(prior,actual)) return false;
            } else if (actual.origin != null || !formal.value.semanticallyEquals(actual.value)) return false;
        }
        return true;
    }
    private static void supports(List<Item> opponents,int index,Set<Source> sources,int depth,int maximumDepth,
            List<Support> proofs,Runnable work) {
        work.run();
        if (index == opponents.size()) {
            if (depth == Integer.MAX_VALUE || depth+1 > maximumDepth) throw new IllegalStateException("Clause collision depth limit");
            for (int i=0;i<proofs.size();++i) {
                work.run(); Support old = proofs.get(i);
                if (old.sources.equals(sources)) {
                    if (depth+1 < old.depth) proofs.set(i,new Support(sources,depth+1)); return;
                }
            }
            if (proofs.size() == 256) throw new IllegalStateException("Clause collision support limit");
            proofs.add(new Support(sources,depth+1)); return;
        }
        for (Support donor : opponents.get(index).proofs) {
            work.run(); Set<Source> next = new LinkedHashSet<>(sources); next.addAll(donor.sources);
            supports(opponents,index+1,next,Math.max(depth,donor.depth),maximumDepth,proofs,work);
        }
    }
    private static void instances(Source producer,Clause clause,Packet input,int index,Map<Integer,Slot> assignment,
            List<Item> opponents,int maximumDepth,List<Evidence> evidence,Runnable work) {
        work.run();
        if (index == clause.literals.size()) {
            List<Support> proofs = new ArrayList<>();
            supports(opponents,0,new LinkedHashSet<>(Collections.singleton(producer)),0,maximumDepth,proofs,work);
            if (evidence.size() == 256) throw new IllegalStateException("Clause collision evidence limit");
            evidence.add(new Evidence(producer,clause,assignment,opponents,proofs)); return;
        }
        for (Item item : input.items) {
            Map<Integer,Slot> next = new LinkedHashMap<>(assignment);
            if (!match(clause.literals.get(index),item,next,work)) continue;
            List<Item> selected = new ArrayList<>(opponents); selected.add(item);
            instances(producer,clause,input,index+1,next,selected,maximumDepth,evidence,work);
        }
    }
    /** A fresh compiler authenticates universal clause shape; no native link or general inference. */
    static Result inspect(Packet input,Cut cut,List<Declaration> declarations,int maximumDepth,int maximumWork,int maximumEvents) {
        return inspect(input,cut,declarations,maximumDepth,maximumWork,maximumEvents,null);
    }
    static Result inspect(Packet input,Cut cut,List<Declaration> declarations,int maximumDepth,int maximumWork,int maximumEvents,ContextWitnessEmptyTarget emptyTarget) {
        return inspectPool(input,cut,declarations,maximumDepth,maximumWork,maximumEvents,emptyTarget,null);
    }
    /** Direct local refutation only; never private coverage or family admission. */
    static Result inspectPrivate(PrivatePool pool,int maximumDepth,int maximumWork,int maximumEvents) {
        if(pool==null)throw new IllegalArgumentException("Sealed private pool required");
        return inspectPool(pool.common,pool.cut,pool.declarations,maximumDepth,maximumWork,maximumEvents,null,pool);
    }
    private static Result inspectPool(Packet input,Cut cut,List<Declaration> declarations,int maximumDepth,int maximumWork,int maximumEvents,
            ContextWitnessEmptyTarget emptyTarget,PrivatePool privatePool) {
        if (maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1) throw new IllegalArgumentException("Bounded collision verification required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Clause collision work limit"); };
        try {
            List<Input> inputs = privatePool==null ? ContextWitnessDeclarationCut.capture(input,cut,declarations,work,maximumWork,maximumEvents,emptyTarget)
                    : ContextWitnessDeclarationCut.capturePrivate(privatePool,declarations,work,maximumWork,maximumEvents);
            List<Evidence> evidence = new ArrayList<>();
            for (Input declaration : inputs) for (Clause clause : declaration.shapes)
                instances(declaration.source,clause,privatePool==null?input:privatePool.local,0,new LinkedHashMap<>(),new ArrayList<>(),maximumDepth,evidence,work);
            if(privatePool!=null){
                List<Evidence> anchored=new ArrayList<>();for(Evidence e:evidence){work.run();List<Support> proofs=new ArrayList<>();for(Support p:e.proofs){work.run();if(p.sources.stream().anyMatch(source->source.localX))proofs.add(p);}if(!proofs.isEmpty())anchored.add(new Evidence(e.producer,e.clause,e.assignment,e.opponents,proofs));}
                evidence=anchored;
            }
            return new Result(evidence.isEmpty() ? Status.INCOMPLETE : Status.COLLISION,
                    privatePool==null ? (evidence.isEmpty()?"No falsified clause instance proved; coverage unknown":"Sourced universal clause refuted by carried proofs")
                        : (evidence.isEmpty()?"No X-dependent local refutation proved; coverage unknown":"Sourced clause refuted by common and sealed private proofs"),
                    input,cut,evidence,true);
        } catch (Exception failure) {
            return new Result(Status.INCOMPLETE,failure.getMessage(),input,cut,Collections.emptyList(),false);
        }
    }
}
