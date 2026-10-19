/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextGroundProductionRounds.Collision;

/** Closed ground cuts, immutable common barriers and separately checked models. No publication lease. */
public final class ContextGroundClauseFamily {
    public enum Scope { ORDINARY_GROUND_UNIT_AND_COMPOUND_RESOLUTION }
    public enum Status { GROUND_FIXED_POINT, COLLISION, INCOMPLETE }
    /** A chosen consistency witness only: absent atoms are false in this model, not negative knowledge. */
    public static final class Model {
        public final Snapshot target;
        public final List<Fact> positiveAtoms;
        private Model(Snapshot target,List<Fact> positives) {
            this.target = target; positiveAtoms = Collections.unmodifiableList(new ArrayList<>(positives));
        }
    }
    public static final class Certificate {
        public final Scope scope = Scope.ORDINARY_GROUND_UNIT_AND_COMPOUND_RESOLUTION;
        public final Frontier frontier;
        public final List<Model> models;
        private Certificate(Frontier frontier,List<Model> models) {
            this.frontier = frontier; this.models = Collections.unmodifiableList(new ArrayList<>(models));
        }
    }
    public static final class Evaluation {
        public final Status status;
        public final String reason;
        public final int rounds;
        public final Certificate certificate;
        public final List<Collision> collisions;
        private Evaluation(Status status,String reason,int rounds,Certificate certificate,List<Collision> collisions) {
            this.status = status; this.reason = reason; this.rounds = rounds; this.certificate = certificate;
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
        }
    }
    private static Evaluation incomplete(String reason,int rounds) {
        return new Evaluation(Status.INCOMPLETE,reason,rounds,null,Collections.emptyList());
    }
    private static void ground(Clause clause,Runnable work) {
        work.run(); if (!clause.variables.isEmpty()) throw new IllegalStateException("Ground family excludes universal clause slots");
        for (Atom atom : clause.literals) { work.run(); if (atom.fact() == null) throw new IllegalStateException("Ordinary ground atom required"); }
    }
    private static void scope(Frontier input,Runnable work) {
        for (Snapshot cut : input.cuts) for (Entry entry : ContextGroundCutEvidence.declarations(input.x,cut == input.x ? null : cut,work))
            for (Clause clause : entry.clauses) ground(clause,work);
        for (Product product : input.products) ground(product.clause,work);
    }
    private static boolean search(List<int[]> clauses,boolean[] values,int index,int[] remaining,Runnable work) {
        work.run();
        if (index < values.length) {
            values[index] = false; if (search(clauses,values,index+1,remaining,work)) return true;
            values[index] = true; return search(clauses,values,index+1,remaining,work);
        }
        if (remaining[0]-- <= 0) throw new IllegalStateException("Ground model assignment limit");
        for (int[] clause : clauses) {
            boolean satisfied = false;
            for (int literal : clause) { work.run(); if (values[Math.abs(literal)-1] == (literal > 0)) { satisfied = true; break; } }
            if (!satisfied) return false;
        }
        return true;
    }
    private static Model model(Frontier input,Receipt receipt,int maximumModels,Runnable work) {
        List<Clause> theory = new ArrayList<>();
        for (Entry entry : ContextGroundCutEvidence.declarations(input.x,receipt.target == input.x ? null : receipt.target,work)) theory.addAll(entry.clauses);
        for (Product product : input.products) { work.run(); theory.add(product.clause); }
        for (Product product : receipt.localProofs) { work.run(); theory.add(product.clause); }
        List<Fact> atoms = new ArrayList<>(); List<int[]> clauses = new ArrayList<>();
        for (Clause clause : theory) {
            ground(clause,work); int[] encoded = new int[clause.literals.size()]; int at = 0;
            for (Atom literal : clause.literals) {
                Fact fact = literal.fact(),positive = fact.positive ? fact : fact.opposite(); int found = -1;
                for (int i=0;i<atoms.size();++i) { work.run(); if (atoms.get(i).equivalent(positive)) { found = i; break; } }
                if (found < 0) {
                    if (atoms.size() >= 256) throw new IllegalStateException("Ground model atom limit");
                    found = atoms.size(); atoms.add(positive);
                }
                encoded[at++] = fact.positive ? found+1 : -(found+1);
            }
            clauses.add(encoded);
        }
        boolean[] values = new boolean[atoms.size()];
        if (!search(clauses,values,0,new int[]{maximumModels},work))
            throw new IllegalStateException("No model found; no sourced collision proof supplied");
        // Recheck the complete selected witness before exposing it.
        List<Fact> positives = new ArrayList<>();
        for (int i=0;i<values.length;++i) { work.run(); if (values[i]) positives.add(atoms.get(i)); }
        for (int[] clause : clauses) {
            boolean satisfied = false;
            for (int literal : clause) { work.run(); satisfied |= values[Math.abs(literal)-1] == (literal > 0); }
            if (!satisfied) throw new IllegalStateException("Ground model witness check failed");
        }
        return new Model(receipt.target,positives);
    }
    /** Exact-input unchanged barrier, both local audits and a checked separate model for every cut. */
    public static Evaluation certify(Frontier input,List<Receipt> receipts,int maximumDepth,int maximumWork,int maximumModels) {
        if (input == null || maximumDepth < 0 || maximumModels < 1) throw new IllegalArgumentException("Bounded ground certificate required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Receipt> outputs = new ArrayList<>(receipts); scope(input,work);
            Barrier barrier = ContextOrdinaryResidualRounds.merge(input,outputs,maximumWork);
            if (barrier.status == ContextOrdinaryResidualRounds.Status.COLLISION)
                return new Evaluation(Status.COLLISION,barrier.reason,0,null,barrier.collisions);
            if (barrier.status != ContextOrdinaryResidualRounds.Status.MERGED) return incomplete(barrier.reason,0);
            if (barrier.changed) return incomplete("Ground certificate requires unchanged shared recipes",0);
            ContextOrdinaryResidualCoverage.Audit reductions = ContextOrdinaryResidualCoverage.audit(input,outputs,ContextOrdinaryResidualCoverage.Mode.LOCAL_CLOSURE,maximumDepth,maximumWork);
            if (!reductions.covered) return incomplete(reductions.reason,0);
            ContextGroundCompositionCoverage.Audit compositions = ContextGroundCompositionCoverage.audit(input,outputs,ContextGroundCompositionCoverage.Mode.LOCAL_CLOSURE,maximumDepth,maximumWork);
            if (!compositions.covered) return incomplete(compositions.reason,0);
            List<Model> models = new ArrayList<>();
            for (Snapshot cut : input.cuts) for (Receipt receipt : outputs) {
                work.run(); if (receipt.target == cut) { models.add(model(input,receipt,maximumModels,work)); break; }
            }
            return new Evaluation(Status.GROUND_FIXED_POINT,"Closed ordinary ground family with separate checked models; general language/admission unknown",0,new Certificate(input,models),Collections.emptyList());
        } catch (Exception failure) { return incomplete(failure.getMessage(),0); }
    }
    public static Evaluation evaluate(Snapshot x,List<Snapshot> foreign,int maximumRounds,int maximumLocalRounds,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes,int maximumModels) {
        return evaluate(x,foreign,maximumRounds,maximumLocalRounds,maximumDepth,maximumWork,maximumEvents,maximumProbes,maximumModels,LedgerLimits.DEFAULT);
    }
    public static Evaluation evaluate(Snapshot x,List<Snapshot> foreign,int maximumRounds,int maximumLocalRounds,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes,int maximumModels,LedgerLimits ledgerLimits) {
        if (ledgerLimits == null || maximumRounds < 1 || maximumLocalRounds < 1 || maximumDepth < 0 || maximumWork < 1 || maximumEvents < 1 || maximumProbes < 1 || maximumModels < 1)
            throw new IllegalArgumentException("Bounded ground family required");
        int completed = 0;
        try {
            Frontier input = ContextOrdinaryResidualRounds.start(x,foreign,maximumWork,ledgerLimits);
            scope(input,ContextGroundProductionRounds.budget(maximumWork));
            for (int step=0;step<maximumRounds;++step) {
                List<Receipt> outputs = new ArrayList<>();
                for (Snapshot cut : input.cuts) outputs.add(ContextOrdinaryResidualRounds.inspectWithLocalCompositions(input,cut,maximumDepth,maximumWork,maximumEvents,maximumProbes,maximumLocalRounds));
                completed = step+1;
                Barrier barrier = ContextOrdinaryResidualRounds.merge(input,outputs,maximumWork);
                if (barrier.status == ContextOrdinaryResidualRounds.Status.COLLISION)
                    return new Evaluation(Status.COLLISION,barrier.reason,completed,null,barrier.collisions);
                if (barrier.status != ContextOrdinaryResidualRounds.Status.MERGED) return incomplete(barrier.reason,completed);
                ContextOrdinaryResidualCoverage.Audit reductions = ContextOrdinaryResidualCoverage.audit(input,outputs,ContextOrdinaryResidualCoverage.Mode.INPUT_STEP,maximumDepth,maximumWork);
                if (!reductions.covered) return incomplete(reductions.reason,completed);
                ContextGroundCompositionCoverage.Audit compositions = ContextGroundCompositionCoverage.audit(input,outputs,ContextGroundCompositionCoverage.Mode.INPUT_STEP,maximumDepth,maximumWork);
                if (!compositions.covered) return incomplete(compositions.reason,completed);
                if (!barrier.changed) {
                    Evaluation certified = certify(input,outputs,maximumDepth,maximumWork,maximumModels);
                    return new Evaluation(certified.status,certified.reason,completed,certified.certificate,certified.collisions);
                }
                input = barrier.frontier;
            }
            return incomplete("Ground family common round limit",completed);
        } catch (Exception failure) { return incomplete(failure.getMessage(),completed); }
    }
}
