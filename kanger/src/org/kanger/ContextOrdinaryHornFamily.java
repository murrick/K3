/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextGroundProductionRounds.Collision;

/** Common signed residual barriers and separate Horn models. Scoped reductions, never a publication lease. */
public final class ContextOrdinaryHornFamily {
    public enum Scope { ORDINARY_HORN_UNIT_DONOR_REDUCTIONS }
    public enum Status { REDUCTION_FIXED_POINT, COLLISION, INCOMPLETE }
    /** A consistency witness only: all other ground atoms are false in this chosen model, not asserted negative knowledge. */
    public static final class Model {
        public final Snapshot target;
        public final List<Fact> positiveAtoms;
        private Model(Snapshot target,List<Fact> positiveAtoms) {
            this.target = target; this.positiveAtoms = Collections.unmodifiableList(new ArrayList<>(positiveAtoms));
        }
    }
    public static final class ReductionCertificate {
        public final Scope scope = Scope.ORDINARY_HORN_UNIT_DONOR_REDUCTIONS;
        public final Frontier frontier;
        public final List<Model> models;
        private ReductionCertificate(Frontier frontier,List<Model> models) {
            this.frontier = frontier; this.models = Collections.unmodifiableList(new ArrayList<>(models));
        }
    }
    public static final class Evaluation {
        public final Status status;
        public final String reason;
        public final int rounds;
        public final ReductionCertificate certificate;
        public final List<Collision> collisions;
        private Evaluation(Status status,String reason,int rounds,ReductionCertificate certificate,List<Collision> collisions) {
            this.status = status; this.reason = reason; this.rounds = rounds; this.certificate = certificate;
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
        }
    }
    private static Evaluation incomplete(String reason,int rounds) {
        return new Evaluation(Status.INCOMPLETE,reason,rounds,null,Collections.emptyList());
    }
    private static void horn(Clause clause,Runnable work) {
        int positive = 0;
        for (Atom atom : clause.literals) { work.run(); if (atom.positive) ++positive; }
        if (positive > 1) throw new IllegalStateException("Ordinary model certificate requires Horn clauses");
        if (clause.literals.size() == 1 && !clause.variables.isEmpty()) throw new IllegalStateException("Universal unit outside ordinary Horn model scope");
    }
    private static void scope(Frontier input,Runnable work) {
        for (Snapshot cut : input.cuts) for (Entry entry : ContextGroundCutEvidence.declarations(input.x,cut == input.x ? null : cut,work))
            for (Clause clause : entry.clauses) horn(clause,work);
        for (Product product : input.products) horn(product.clause,work);
    }
    private static Model model(Receipt receipt,Runnable work) {
        List<Fact> positives = new ArrayList<>(),negatives = new ArrayList<>();
        for (Product product : receipt.localProofs) {
            horn(product.clause,work);
            if (product.clause.literals.size() != 1) continue;
            Fact fact = product.clause.literals.get(0).fact();
            if (fact == null) throw new IllegalStateException("Non-ground unit cannot enter ordinary model");
            if (!fact.positive) { negatives.add(fact); continue; }
            boolean duplicate = false;
            for (Fact old : positives) { work.run(); if (old.equivalent(fact)) { duplicate = true; break; } }
            if (!duplicate) positives.add(fact);
        }
        for (Fact negative : negatives) for (Fact positive : positives) {
            work.run(); if (negative.opposite().equivalent(positive)) throw new IllegalStateException("Opposite local proofs prevent a Horn model");
        }
        return new Model(receipt.target,positives);
    }
    /** Exact-input authenticated unchanged barrier plus complete local reductions; general clause-composition unknown. */
    public static Evaluation certify(Frontier input,List<Receipt> receipts,int maximumDepth,int maximumWork) {
        if (input == null || maximumDepth < 0) throw new IllegalArgumentException("Bounded ordinary Horn certificate required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Receipt> outputs = new ArrayList<>(receipts); scope(input,work);
            Barrier barrier = ContextOrdinaryResidualRounds.merge(input,outputs,maximumWork);
            if (barrier.status == ContextOrdinaryResidualRounds.Status.COLLISION)
                return new Evaluation(Status.COLLISION,barrier.reason,0,null,barrier.collisions);
            if (barrier.status != ContextOrdinaryResidualRounds.Status.MERGED) return incomplete(barrier.reason,0);
            if (barrier.changed) return incomplete("Ordinary Horn certificate requires unchanged shared recipes",0);
            ContextOrdinaryResidualCoverage.Audit audit = ContextOrdinaryResidualCoverage.audit(input,outputs,ContextOrdinaryResidualCoverage.Mode.LOCAL_CLOSURE,maximumDepth,maximumWork);
            if (!audit.covered) return incomplete(audit.reason,0);
            List<Model> models = new ArrayList<>();
            for (Snapshot cut : input.cuts) for (Receipt receipt : outputs) {
                work.run(); if (receipt.target == cut) { models.add(model(receipt,work)); break; }
            }
            return new Evaluation(Status.REDUCTION_FIXED_POINT,"Common residual reduction closure and separate Horn models; general clause-composition/admission unknown",0,new ReductionCertificate(input,models),Collections.emptyList());
        } catch (Exception failure) { return incomplete(failure.getMessage(),0); }
    }
    public static Evaluation evaluate(Snapshot x,List<Snapshot> foreign,int maximumRounds,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes) {
        if (maximumRounds < 1 || maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1 || maximumProbes < 1)
            throw new IllegalArgumentException("Bounded ordinary Horn family required");
        int completed = 0;
        try {
            Frontier input = ContextOrdinaryResidualRounds.start(x,foreign,maximumWork);
            scope(input,ContextGroundProductionRounds.budget(maximumWork));
            for (int step=0;step<maximumRounds;++step) {
                List<Receipt> outputs = new ArrayList<>();
                for (Snapshot cut : input.cuts) outputs.add(ContextOrdinaryResidualRounds.inspectWithProbes(input,cut,maximumDepth,maximumWork,maximumEvents,maximumProbes));
                completed = step+1;
                Barrier barrier = ContextOrdinaryResidualRounds.merge(input,outputs,maximumWork);
                if (barrier.status == ContextOrdinaryResidualRounds.Status.COLLISION)
                    return new Evaluation(Status.COLLISION,barrier.reason,completed,null,barrier.collisions);
                if (barrier.status != ContextOrdinaryResidualRounds.Status.MERGED) return incomplete(barrier.reason,completed);
                ContextOrdinaryResidualCoverage.Audit audit = ContextOrdinaryResidualCoverage.audit(input,outputs,ContextOrdinaryResidualCoverage.Mode.INPUT_STEP,maximumDepth,maximumWork);
                if (!audit.covered) return incomplete(audit.reason,completed);
                if (!barrier.changed) {
                    Evaluation checked = certify(input,outputs,maximumDepth,maximumWork);
                    return new Evaluation(checked.status,checked.reason,completed,checked.certificate,checked.collisions);
                }
                input = barrier.frontier;
            }
            return incomplete("Ordinary Horn family round limit",completed);
        } catch (Exception failure) { return incomplete(failure.getMessage(),completed); }
    }
}
