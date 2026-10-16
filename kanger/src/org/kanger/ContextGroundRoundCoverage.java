/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextGroundCutEvidence.Result;
import org.kanger.ContextGroundProductionRounds.Frontier;
import org.kanger.ContextWitnessUnaryChainVerifier.Route;
import org.kanger.ContextWitnessUnaryChainVerifier.Pattern;

/** Audits signed unit-inference obligations against actual proof receipts. Never an admission certificate. */
public final class ContextGroundRoundCoverage {
    public enum Mode { INPUT_STEP, LOCAL_CLOSURE }
    public static final class Audit {
        public final boolean covered;
        public final String reason;
        private Audit(boolean covered,String reason) { this.covered = covered; this.reason = reason; }
    }
    private static boolean match(Atom atom,Pattern actual,boolean opposite,Map<Integer,SemanticTermSnapshot> bindings,Runnable work) {
        work.run();
        if (!atom.predicate.equals(actual.predicate) || (opposite ? !atom.positive : atom.positive) != actual.positive
                || atom.arguments.size() != actual.arguments.size() || actual.root != null) return false;
        for (int i=0;i<atom.arguments.size();++i) {
            work.run(); Slot slot = atom.arguments.get(i); SemanticTermSnapshot value = actual.arguments.get(i).value;
            if (value == null) return false;
            if (slot.variable < 0) { if (!slot.value.semanticallyEquals(value)) return false; }
            else {
                SemanticTermSnapshot old = bindings.putIfAbsent(slot.variable,value);
                if (old != null && !old.semanticallyEquals(value)) return false;
            }
        }
        return true;
    }
    static void scope(Clause clause,Runnable work) {
        // Every orientation must bind its head from donors. No finite-domain approximation of @x.
        for (int head=0;head<clause.literals.size();++head) {
            Set<Integer> donors = new HashSet<>();
            for (int i=0;i<clause.literals.size();++i) if (i != head)
                for (Slot slot : clause.literals.get(i).arguments) { work.run(); if (slot.variable >= 0) donors.add(slot.variable); }
            for (Slot slot : clause.literals.get(head).arguments) {
                work.run(); if (slot.variable >= 0 && !donors.contains(slot.variable))
                    throw new IllegalStateException("Signed coverage requires separately covered unbound head variable");
            }
        }
    }
    private static void obligation(Clause clause,int head,int index,List<Route> donors,List<Route> output,
            Map<Integer,SemanticTermSnapshot> bindings,Set<Source> sources,int depth,int maximumDepth,Runnable work) {
        work.run();
        if (index < clause.literals.size()) {
            if (index == head) { obligation(clause,head,index+1,donors,output,bindings,sources,depth,maximumDepth,work); return; }
            for (Route donor : donors) {
                Map<Integer,SemanticTermSnapshot> next = new HashMap<>(bindings);
                if (!match(clause.literals.get(index),donor.physical,true,next,work)) continue;
                Set<Source> joined = new LinkedHashSet<>(sources); joined.addAll(donor.proof.sources);
                obligation(clause,head,index+1,donors,output,next,joined,Math.max(depth,donor.proof.depth),maximumDepth,work);
            }
            return;
        }
        int requiredDepth = depth+1;
        for (Route actual : output) {
            work.run();
            if (actual.proof.sources.equals(sources) && actual.proof.depth <= requiredDepth
                    && match(clause.literals.get(head),actual.physical,false,new HashMap<>(bindings),work)) return;
        }
        if (requiredDepth > maximumDepth) throw new IllegalStateException("Signed coverage cumulative depth limit");
        throw new IllegalStateException("Enabled signed conclusion lacks an observed whole-source proof");
    }
    static void check(List<Entry> declarations,List<Route> donors,List<Route> output,int maximumDepth,Runnable work) {
        // Preflight all declarations before considering any enabled instance.
        for (Entry entry : declarations) for (Clause clause : entry.clauses) scope(clause,work);
        for (Entry entry : declarations) for (Clause clause : entry.clauses) {
            work.run(); if (clause.literals.size() == 1) continue; // actual primary units are already proof roots
            for (int head=0;head<clause.literals.size();++head)
                obligation(clause,head,0,donors,output,new HashMap<>(),new LinkedHashSet<>(Collections.singleton(entry.source)),0,maximumDepth,work);
        }
    }
    /** All cuts must refer to this exact immutable input; no prefix survives a failed obligation. */
    public static Audit audit(Frontier input,List<Result> results,Mode mode,int maximumDepth,int maximumWork) {
        if (input == null || mode == null || maximumDepth < 0) throw new IllegalArgumentException("Bounded signed coverage required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Result> outputs = new ArrayList<>(results);
            if (outputs.size() != input.cuts.size()) throw new IllegalStateException("Incomplete signed coverage family");
            Set<ContextProductionNativeBinding.Snapshot> seen = new HashSet<>();
            for (Result result : outputs) {
                work.run();
                if (result == null || !result.verified || result.input != input || !input.cuts.contains(result.target) || !seen.add(result.target)
                        || !result.collisions.isEmpty()) throw new IllegalStateException("Signed coverage needs complete collision-free exact-input receipts");
            }
            for (Result result : outputs) {
                List<Entry> declarations = ContextGroundCutEvidence.declarations(input.x,result.foreign,work);
                List<Route> donors = new ArrayList<>();
                for (Route route : result.localRoutes) {
                    work.run(); if (route.proof.depth > maximumDepth) throw new IllegalStateException("Signed coverage carried/observed depth limit");
                    boolean initial = route.proof.depth == 0;
                    for (ContextGroundCutEvidence.Production carried : input.productions) {
                        work.run();
                        if (carried.proof.equals(route.proof) && matchFact(carried.conclusion,route.physical)) { initial = true; break; }
                    }
                    if (mode == Mode.LOCAL_CLOSURE || initial) donors.add(route);
                }
                check(declarations,donors,result.localRoutes,maximumDepth,work);
            }
            return new Audit(true,mode+" signed unit obligations covered; consistency/admission unknown");
        } catch (Exception failure) { return new Audit(false,failure.getMessage()); }
    }
    private static boolean matchFact(Fact fact,Pattern pattern) {
        if (pattern.root != null || !fact.predicate.equals(pattern.predicate) || fact.positive != pattern.positive || fact.arguments.size() != pattern.arguments.size()) return false;
        for (int i=0;i<fact.arguments.size();++i) if (!fact.arguments.get(i).semanticallyEquals(pattern.arguments.get(i).value)) return false;
        return true;
    }
}
