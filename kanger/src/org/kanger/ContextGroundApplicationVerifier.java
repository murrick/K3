/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;

/** Authenticates a captured ordinary application against its exact primary owner. */
final class ContextGroundApplicationVerifier {
    private static boolean instance(Clause shape,List<Fact> literals,int index,boolean[] used,
            Map<Integer,SemanticTermSnapshot> bindings,Runnable work) {
        work.run(); if (index == shape.literals.size()) return true;
        Atom expected = shape.literals.get(index);
        for (int i=0;i<literals.size();++i) if (!used[i]) {
            work.run(); Fact actual = literals.get(i);
            if (!expected.predicate.equals(actual.predicate) || expected.positive != actual.positive
                    || expected.arguments.size() != actual.arguments.size()) continue;
            Map<Integer,SemanticTermSnapshot> next = new HashMap<>(bindings); boolean matches = true;
            for (int a=0;a<expected.arguments.size();++a) {
                work.run(); Slot slot = expected.arguments.get(a); SemanticTermSnapshot value = actual.arguments.get(a);
                if (slot.variable < 0) { if (!slot.value.semanticallyEquals(value)) { matches = false; break; } }
                else {
                    SemanticTermSnapshot old = next.putIfAbsent(slot.variable,value);
                    if (old != null && !old.semanticallyEquals(value)) { matches = false; break; }
                }
            }
            if (matches) {
                used[i] = true;
                boolean found = instance(shape,literals,index+1,used,next,work);
                used[i] = false; if (found) return true;
            }
        }
        return false;
    }
    /** Shape-only match for a separately authenticated derived producer; grants no primary authority. */
    static boolean producerInstance(Clause shape,Application observation,Runnable work) {
        List<Fact> literals=new ArrayList<>();literals.add(observation.conclusion);
        for(Fact premise:observation.premises){work.run();literals.add(premise.opposite());}
        return shape.literals.size()==literals.size()&&instance(shape,literals,0,new boolean[literals.size()],new HashMap<Integer,SemanticTermSnapshot>(),work);
    }
    static void application(ContextWitnessPrimaryStepVerifier.Scope scope,Application observation,Runnable work) {
        if (observation.steps != 1 || observation.premises.isEmpty() || observation.premises.size() > 2)
            throw new IllegalStateException("outside-ground-donor-step");
        List<Fact> literals = new ArrayList<>(); literals.add(observation.conclusion);
        for (Fact premise : observation.premises) { work.run(); literals.add(premise.opposite()); }
        boolean matched = false;
        for (Clause shape : scope.shapes.getOrDefault(observation.nativeRule,Collections.emptyList())) {
            work.run();
            if (shape.literals.size() == literals.size() && instance(shape,literals,0,new boolean[literals.size()],new HashMap<>(),work)) {
                matched = true; break;
            }
        }
        if (!matched) throw new IllegalStateException("ground-producer-instance-mismatch");
        binding(scope,observation,work);
    }
    /** Native materialization of a ground primary conjunct, not an inference from zero donors. */
    static void primaryInstance(ContextWitnessPrimaryStepVerifier.Scope scope,Application observation,Runnable work) {
        if(observation.steps!=0||!observation.premises.isEmpty()||!observation.conclusion.positive)
            throw new IllegalStateException("outside-ground-primary-instance");
        boolean matched=false;
        for(Clause shape:scope.shapes.getOrDefault(observation.nativeRule,Collections.emptyList())){
            work.run();if(shape.variables.isEmpty()&&shape.literals.size()==1&&shape.literals.get(0).positive
                    &&instance(shape,Collections.singletonList(observation.conclusion),0,new boolean[1],new HashMap<>(),work))matched=true;
        }
        if(!matched)throw new IllegalStateException("ground-primary-instance-shape-mismatch");
        binding(scope,observation,work);
    }
    private static void binding(ContextWitnessPrimaryStepVerifier.Scope scope,Application observation,Runnable work) {
        Set<Source> expected = scope.producers.get(observation.nativeRule),actual = new LinkedHashSet<>();
        for (Support producer : observation.producerSupports) {
            work.run(); if (producer.depth != 0 || producer.sources.size() != 1)
                throw new IllegalStateException("ground-producer-proof-not-primary");
            actual.addAll(producer.sources);
        }
        if (expected == null || !actual.equals(expected)) throw new IllegalStateException("ground-producer-binding-mismatch");
    }
}
