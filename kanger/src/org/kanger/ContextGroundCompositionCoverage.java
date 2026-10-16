/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Exhaustive ground compound-pair obligations, independent of native candidate enumeration. */
public final class ContextGroundCompositionCoverage {
    public enum Mode { INPUT_STEP, LOCAL_CLOSURE }
    public static final class Audit {
        public final boolean covered;
        public final String reason;
        private Audit(boolean covered,String reason) { this.covered = covered; this.reason = reason; }
    }
    private static void check(List<Product> basis,List<Product> output,int maximumDepth,Runnable work) {
        for (Product product : basis) {
            work.run();
            if (!product.clause.variables.isEmpty()) throw new IllegalStateException("Nonground clause outside ground composition coverage");
            if (product.proof.depth > maximumDepth) throw new IllegalStateException("Composition coverage parent depth limit");
        }
        for (int i=0;i<basis.size();++i) for (int j=i+1;j<basis.size();++j) {
            work.run(); Product left = basis.get(i),right = basis.get(j);
            if (left.clause.literals.size() < 2 || right.clause.literals.size() < 2 || left.clause.equivalent(right.clause)) continue;
            for (int a=0;a<left.clause.literals.size();++a) for (int b=0;b<right.clause.literals.size();++b) {
                work.run(); Fact pivot = left.clause.literals.get(a).fact(),opposite = right.clause.literals.get(b).fact();
                if (!pivot.opposite().equivalent(opposite)) continue;
                List<Fact> remaining = new ArrayList<>();
                for (int k=0;k<left.clause.literals.size();++k) { work.run(); if (k != a) remaining.add(left.clause.literals.get(k).fact()); }
                for (int k=0;k<right.clause.literals.size();++k) { work.run(); if (k != b) remaining.add(right.clause.literals.get(k).fact()); }
                Clause required = new Clause(remaining);
                Set<Source> sources = new LinkedHashSet<>(left.proof.sources); sources.addAll(right.proof.sources);
                int depth = Math.max(left.proof.depth,right.proof.depth);
                if (depth == Integer.MAX_VALUE) throw new IllegalStateException("Composition coverage depth overflow");
                ++depth; boolean found = false;
                for (Product actual : output) {
                    work.run(); if (actual.proof.depth <= depth && actual.proof.sources.equals(sources) && actual.clause.equivalent(required)) { found = true; break; }
                }
                if (!found) throw new IllegalStateException(depth > maximumDepth ? "Composition coverage cumulative depth limit" : "Ground composition lacks a checked whole-source proof");
            }
        }
    }
    public static Audit audit(Frontier input,List<Receipt> receipts,Mode mode,int maximumDepth,int maximumWork) {
        if (input == null || mode == null || maximumDepth < 0) throw new IllegalArgumentException("Bounded composition coverage required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Receipt> outputs = new ArrayList<>(receipts);
            if (outputs.size() != input.cuts.size()) throw new IllegalStateException("Incomplete composition coverage family");
            Set<Snapshot> seen = new HashSet<>();
            for (Receipt output : outputs) {
                work.run();
                if (output == null || !output.verified || output.input != input || !input.cuts.contains(output.target) || !seen.add(output.target) || !output.collisions.isEmpty())
                    throw new IllegalStateException("Composition coverage requires complete collision-free exact-input receipts");
                for (Product actual : output.localProofs) {
                    work.run(); if (!actual.clause.variables.isEmpty() || actual.proof.depth > maximumDepth)
                        throw new IllegalStateException("Observed ledger outside bounded ground composition scope");
                }
                check(mode == Mode.INPUT_STEP ? output.owners : output.localProofs,output.localProofs,maximumDepth,work);
            }
            return new Audit(true,mode+" distinct ground compound-pair compositions covered; general saturation/admission unknown");
        } catch (Exception failure) { return new Audit(false,failure.getMessage()); }
    }
}
