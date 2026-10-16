/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Symbolic unit-donor reduction obligations against actual ordinary clause proofs; no admission verdict. */
public final class ContextOrdinaryResidualCoverage {
    public enum Mode { INPUT_STEP, LOCAL_CLOSURE }
    public static final class Audit {
        public final boolean covered;
        public final String reason;
        private Audit(boolean covered,String reason) { this.covered = covered; this.reason = reason; }
    }
    static boolean match(Atom atom,Fact donor,Map<Integer,SemanticTermSnapshot> bindings,Runnable work) {
        work.run();
        if (atom.positive == donor.positive || !atom.predicate.equals(donor.predicate) || atom.arguments.size() != donor.arguments.size()) return false;
        for (int i=0;i<atom.arguments.size();++i) {
            work.run(); Slot slot = atom.arguments.get(i); SemanticTermSnapshot value = donor.arguments.get(i);
            if (slot.variable < 0) { if (!slot.value.semanticallyEquals(value)) return false; }
            else {
                SemanticTermSnapshot old = bindings.putIfAbsent(slot.variable,value);
                if (old != null && !old.semanticallyEquals(value)) return false;
            }
        }
        return true;
    }
    private static void reductions(Product owner,int index,Set<Integer> removed,Map<Integer,SemanticTermSnapshot> bindings,
            Set<Source> sources,int depth,List<Product> donors,List<Product> output,int maximumDepth,Runnable work) {
        work.run();
        if (index == owner.clause.literals.size()) {
            if (removed.isEmpty()) return;
            if (removed.size() == owner.clause.literals.size()) throw new IllegalStateException("Residual coverage requires a reported collision");
            Clause required = owner.clause.remainder(removed,bindings);
            if (required.literals.size() == 1 && !required.variables.isEmpty())
                throw new IllegalStateException("Residual coverage needs unsupported universal unit");
            if (depth == Integer.MAX_VALUE) throw new IllegalStateException("Residual coverage depth overflow");
            int requiredDepth = depth+1;
            for (Product actual : output) {
                work.run(); if (actual.proof.depth <= requiredDepth && actual.proof.sources.equals(sources) && actual.clause.equivalent(required)) return;
            }
            if (requiredDepth > maximumDepth) throw new IllegalStateException("Residual coverage cumulative depth limit");
            throw new IllegalStateException("Enabled symbolic residual lacks an observed whole-source proof");
        }
        // Every nonempty removed subset is an obligation, including proper partial reductions.
        reductions(owner,index+1,removed,bindings,sources,depth,donors,output,maximumDepth,work);
        for (Product donor : donors) {
            work.run(); if (donor.clause.literals.size() != 1) continue;
            Fact fact = donor.clause.literals.get(0).fact(); if (fact == null) continue;
            Map<Integer,SemanticTermSnapshot> next = new HashMap<>(bindings);
            if (!match(owner.clause.literals.get(index),fact,next,work)) continue;
            Set<Integer> selected = new LinkedHashSet<>(removed); selected.add(index);
            Set<Source> joined = new LinkedHashSet<>(sources); joined.addAll(donor.proof.sources);
            reductions(owner,index+1,selected,next,joined,Math.max(depth,donor.proof.depth),donors,output,maximumDepth,work);
        }
    }
    static void check(List<Product> owners,List<Product> donors,List<Product> output,int maximumDepth,Runnable work) {
        for (List<Product> ledger : Arrays.asList(owners,donors,output)) for (Product product : ledger) {
            work.run(); if (product.proof.depth > maximumDepth) throw new IllegalStateException("Residual coverage carried/observed depth limit");
        }
        for (Product owner : owners)
            reductions(owner,0,Collections.emptySet(),Collections.emptyMap(),owner.proof.sources,owner.proof.depth,donors,output,maximumDepth,work);
    }
    public static Audit audit(Frontier input,List<Receipt> receipts,Mode mode,int maximumDepth,int maximumWork) {
        if (input == null || mode == null || maximumDepth < 0) throw new IllegalArgumentException("Bounded residual audit required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Receipt> outputs = new ArrayList<>(receipts);
            if (outputs.size() != input.cuts.size()) throw new IllegalStateException("Incomplete residual audit family");
            Set<Snapshot> seen = new HashSet<>();
            for (Receipt output : outputs) {
                work.run();
                if (output == null || !output.verified || output.input != input || !input.cuts.contains(output.target) || !seen.add(output.target) || !output.collisions.isEmpty())
                    throw new IllegalStateException("Residual audit requires complete collision-free exact-input receipts");
            }
            for (Receipt output : outputs) {
                List<Product> basis = mode == Mode.INPUT_STEP ? output.owners : output.localProofs;
                check(basis,basis,output.localProofs,maximumDepth,work);
            }
            return new Audit(true,mode+" symbolic unit-donor residual obligations covered; general saturation/admission unknown");
        } catch (Exception failure) { return new Audit(false,failure.getMessage()); }
    }
}
