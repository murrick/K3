/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessResultTransport.Slot;

/** Checks closure obligations over a sound pool; never generates a missing product or proof. */
final class ContextWitnessHornClosure {
    static void sameDeclarations(List<Input> expected,List<Input> actual,Runnable work) {
        if (expected.size() != actual.size()) throw new IllegalStateException("Different owning declaration snapshot");
        Map<Source,Input> indexed = new HashMap<>();
        for (Input input : actual) { work.run(); if (indexed.put(input.source,input) != null) throw new IllegalStateException("Repeated owning source"); }
        for (Input input : expected) {
            work.run(); Input original = indexed.get(input.source);
            if (original == null || original.shapes.size() != input.shapes.size()) throw new IllegalStateException("Different owning declaration source/shape");
            boolean[] used = new boolean[original.shapes.size()];
            for (Clause clause : input.shapes) {
                boolean found = false;
                for (int i=0;i<used.length;++i) {
                    work.run(); if (!used[i] && clause.equivalent(original.shapes.get(i))) { used[i] = true; found = true; break; }
                }
                if (!found) throw new IllegalStateException("Different owning declaration clause");
            }
        }
    }
    private static boolean head(ContextProductionCapture.Atom formal,Item item,Map<Integer,Slot> assignment,Runnable work) {
        work.run(); if (!item.atom.positive || !formal.predicate.equals(item.atom.predicate) || formal.arguments.size() != item.atom.arguments.size()) return false;
        for (int i=0;i<formal.arguments.size();++i) {
            work.run(); ContextProductionCapture.Slot term = formal.arguments.get(i); Slot actual = item.atom.arguments.get(i);
            Slot bound = term.variable < 0 ? null : assignment.get(term.variable);
            if (term.variable >= 0) {
                if (bound == null) throw new IllegalStateException("Unbound Horn head variable");
                if (bound.origin != null || actual.origin != null) { if (!Objects.equals(bound.origin,actual.origin)) return false; }
                else if (!bound.value.semanticallyEquals(actual.value)) return false;
            } else if (actual.origin != null || !term.value.semanticallyEquals(actual.value)) return false;
        }
        return true;
    }
    private static void proofs(Item conclusion,List<Item> donors,int index,Set<Source> sources,int depth,int maximumDepth,
            int[] obligations,Runnable work) {
        work.run();
        if (index == donors.size()) {
            if (depth == Integer.MAX_VALUE) throw new IllegalStateException("Horn closure depth overflow");
            int expected = depth+1;
            for (Support proof : conclusion.proofs) {
                work.run(); if (proof.sources.equals(sources) && proof.depth <= expected) { ++obligations[0]; return; }
            }
            throw new IllegalStateException(expected > maximumDepth ? "Uncovered Horn proof exceeds depth limit" : "Missing Horn source alternative or shorter proof");
        }
        for (Support proof : donors.get(index).proofs) {
            work.run(); Set<Source> next = new LinkedHashSet<>(sources); next.addAll(proof.sources);
            proofs(conclusion,donors,index+1,next,Math.max(depth,proof.depth),maximumDepth,obligations,work);
        }
    }
    private static void instances(Source producer,ContextProductionCapture.Atom head,List<ContextProductionCapture.Atom> body,
            Packet pool,Packet output,int index,Map<Integer,Slot> assignment,List<Item> donors,int maximumDepth,int[] obligations,Runnable work) {
        work.run();
        if (index == body.size()) {
            Item conclusion = null;
            for (Item item : output.items) if (head(head,item,assignment,work)) { conclusion = item; break; }
            if (conclusion == null) throw new IllegalStateException("Missing Horn conclusion instance");
            proofs(conclusion,donors,0,new LinkedHashSet<>(Collections.singleton(producer)),0,maximumDepth,obligations,work); return;
        }
        for (Item item : pool.items) {
            Map<Integer,Slot> next = new LinkedHashMap<>(assignment);
            if (!ContextWitnessClauseCollision.match(body.get(index),item,next,work)) continue;
            List<Item> selected = new ArrayList<>(donors); selected.add(item);
            instances(producer,head,body,pool,output,index+1,next,selected,maximumDepth,obligations,work);
        }
    }
    private static final class Schema {
        final ContextProductionCapture.Atom head;
        final List<ContextProductionCapture.Atom> body;
        Schema(ContextProductionCapture.Atom head,List<ContextProductionCapture.Atom> body) { this.head = head; this.body = body; }
    }
    private static Schema schema(Clause clause,Runnable work) {
        ContextProductionCapture.Atom head = null; List<ContextProductionCapture.Atom> body = new ArrayList<>(); Set<Integer> variables = new HashSet<>();
        for (ContextProductionCapture.Atom literal : clause.literals) {
            work.run();
            if (literal.positive) {
                if (head != null) throw new IllegalStateException("Definite Horn closure required"); head = literal;
            } else {
                body.add(literal); for (ContextProductionCapture.Slot slot : literal.arguments) { work.run(); if (slot.variable >= 0) variables.add(slot.variable); }
            }
        }
        if (head == null || body.isEmpty()) throw new IllegalStateException("Fact-free definite Horn closure required");
        for (ContextProductionCapture.Slot slot : head.arguments) {
            work.run(); if (slot.variable >= 0 && !variables.contains(slot.variable)) throw new IllegalStateException("Range-restricted Horn head required");
        }
        return new Schema(head,body);
    }
    /** Shape preflight, including every later clause, without testing closure or executing native inference. */
    static void validate(Packet pool,List<Input> definitions,int maximumDepth,Runnable work) {
        for (Item item : pool.items) {
            work.run(); if (!item.atom.positive) throw new IllegalStateException("Positive Horn closure pool required");
            for (Support proof : item.proofs) { work.run(); if (proof.depth > maximumDepth) throw new IllegalStateException("Carried Horn proof exceeds depth limit"); }
        }
        for (Input input : definitions) for (Clause clause : input.shapes) schema(clause,work);
    }
    /** Input-enabled obligations only; later output-enabled bodies belong to a later barrier. */
    static int verifyRound(Packet input,Packet output,List<Input> definitions,int maximumDepth,Runnable work) {
        validate(input,definitions,maximumDepth,work); validate(output,definitions,maximumDepth,work);
        if (!ContextWitnessResultTransport.retains(input,output,work))
            throw new IllegalStateException("Round output lost an input proof");
        int[] obligations = {0};
        for (Input definition : definitions) for (Clause clause : definition.shapes) {
            Schema rule = schema(clause,work);
            instances(definition.source,rule.head,rule.body,input,output,0,new LinkedHashMap<>(),new ArrayList<>(),maximumDepth,obligations,work);
        }
        return obligations[0];
    }
    /** Preconditions: sound sealed pool and whole-rule-validated supplied definitions. */
    static int verify(Packet pool,List<Input> definitions,int maximumDepth,Runnable work) {
        validate(pool,definitions,maximumDepth,work); int[] obligations = {0};
        for (Input input : definitions) for (Clause clause : input.shapes) {
            Schema rule = schema(clause,work);
            instances(input.source,rule.head,rule.body,pool,pool,0,new LinkedHashMap<>(),new ArrayList<>(),maximumDepth,obligations,work);
        }
        return obligations[0];
    }
}
