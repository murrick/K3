/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundCutEvidence.Result;
import org.kanger.ContextGroundProductionRounds.*;
import org.kanger.ContextWitnessUnaryChainVerifier.Route;
import org.kanger.ContextWitnessUnaryChainVerifier.Pattern;

/** Bounded synchronous signed-unit family and separate Horn models; residual-family saturation unknown. */
public final class ContextGroundHornFamily {
    public static final class UnitCertificate {
        public final Frontier frontier;
        private UnitCertificate(Frontier frontier) { this.frontier = frontier; }
    }
    public enum Status { UNIT_FIXED_POINT, COLLISION, INCOMPLETE }
    public static final class Evaluation {
        public final Status status;
        public final String reason;
        public final int rounds;
        public final UnitCertificate certificate;
        public final List<Collision> collisions;
        private Evaluation(Status status,String reason,int rounds,UnitCertificate certificate,List<Collision> collisions) {
            this.status = status; this.reason = reason; this.rounds = rounds; this.certificate = certificate;
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
        }
    }
    private static Evaluation incomplete(String reason,int rounds) {
        return new Evaluation(Status.INCOMPLETE,reason,rounds,null,Collections.emptyList());
    }
    private static void horn(Frontier input,Runnable work) {
        for (Snapshot cut : input.cuts) for (Entry entry : ContextGroundCutEvidence.declarations(input.x,cut == input.x ? null : cut,work))
            for (Clause clause : entry.clauses) {
                int positive = 0;
                for (Atom atom : clause.literals) { work.run(); if (atom.positive) ++positive; }
                if (positive > 1) throw new IllegalStateException("Signed consistency certificate requires Horn clauses");
                ContextGroundRoundCoverage.scope(clause,work);
            }
    }
    private static boolean opposite(Pattern a,Pattern b,Runnable work) {
        work.run(); if (a.root != null || b.root != null) throw new IllegalStateException("Nonordinary Horn model atom");
        if (a.positive == b.positive || !a.predicate.equals(b.predicate) || a.arguments.size() != b.arguments.size()) return false;
        for (int i=0;i<a.arguments.size();++i) {
            work.run(); if (!a.arguments.get(i).value.semanticallyEquals(b.arguments.get(i).value)) return false;
        }
        return true;
    }
    /** Certifies only an unchanged, fully covered common unit frontier with a separate model for each cut. */
    public static Evaluation certify(Frontier input,List<Result> receipts,int maximumDepth,int maximumWork) {
        if (input == null || maximumDepth < 0) throw new IllegalArgumentException("Bounded Horn certificate required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Result> outputs = new ArrayList<>(receipts);
            horn(input,work);
            Barrier barrier = ContextGroundProductionRounds.merge(input,outputs,maximumWork);
            if (barrier.status == ContextGroundProductionRounds.Status.COLLISION)
                return new Evaluation(Status.COLLISION,barrier.reason,0,null,barrier.collisions);
            if (barrier.status != ContextGroundProductionRounds.Status.MERGED) return incomplete(barrier.reason,0);
            if (barrier.changed) return incomplete("Horn model needs unchanged shared proof frontier",0);
            ContextGroundRoundCoverage.Audit audit = ContextGroundRoundCoverage.audit(input,outputs,ContextGroundRoundCoverage.Mode.LOCAL_CLOSURE,maximumDepth,maximumWork);
            if (!audit.covered) return incomplete(audit.reason,0);
            // P = all actually proved positive local atoms; all other ground atoms are false.
            // Closed Horn obligations satisfy every rule/constraint; opposition would falsify a negative unit.
            for (Result cut : outputs) for (int i=0;i<cut.localRoutes.size();++i) for (int j=i+1;j<cut.localRoutes.size();++j) {
                Route a = cut.localRoutes.get(i),b = cut.localRoutes.get(j);
                if (opposite(a.physical,b.physical,work))
                    return incomplete("Opposite local proofs prevent a Horn model certificate",0);
            }
            return new Evaluation(Status.UNIT_FIXED_POINT,"Separate closed Horn models; unit frontier stable; residual-family saturation unknown",0,new UnitCertificate(input),Collections.emptyList());
        } catch (Exception failure) { return incomplete(failure.getMessage(),0); }
    }
    public static Evaluation evaluate(Snapshot x,List<Snapshot> foreign,int maximumRounds,int maximumDepth,int maximumWork,int maximumEvents) {
        if (maximumRounds < 1 || maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1)
            throw new IllegalArgumentException("Bounded Horn family required");
        int completed = 0;
        try {
            Frontier input = ContextGroundProductionRounds.start(x,foreign,maximumWork);
            horn(input,ContextGroundProductionRounds.budget(maximumWork));
            for (int round=1;round<=maximumRounds;++round) {
                List<Result> outputs = new ArrayList<>();
                for (Snapshot cut : input.cuts) outputs.add(ContextGroundCutEvidence.inspect(input,cut,maximumDepth,maximumWork,maximumEvents));
                completed = round;
                Barrier barrier = ContextGroundProductionRounds.merge(input,outputs,maximumWork);
                if (barrier.status == ContextGroundProductionRounds.Status.COLLISION)
                    return new Evaluation(Status.COLLISION,barrier.reason,round,null,barrier.collisions);
                if (barrier.status != ContextGroundProductionRounds.Status.MERGED) return incomplete(barrier.reason,round);
                ContextGroundRoundCoverage.Audit audit = ContextGroundRoundCoverage.audit(input,outputs,ContextGroundRoundCoverage.Mode.INPUT_STEP,maximumDepth,maximumWork);
                if (!audit.covered) return incomplete(audit.reason,round);
                if (!barrier.changed) {
                    Evaluation certified = certify(input,outputs,maximumDepth,maximumWork);
                    return new Evaluation(certified.status,certified.reason,round,certified.certificate,certified.collisions);
                }
                input = barrier.frontier;
            }
            return incomplete("Horn family round limit",completed);
        } catch (Exception failure) { return incomplete(failure.getMessage(),completed); }
    }
}
