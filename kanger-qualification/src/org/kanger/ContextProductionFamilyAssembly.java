/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessFamilyFixedPoint.Branch;

/** Complete row accounting and diagnostic family assembly from native-bound snapshots; never admission. */
public final class ContextProductionFamilyAssembly {
    public enum Use { X_SEEDS, X_PRODUCER, X_SEEDS_AND_PRODUCER, FOREIGN_CUT }
    public static final class RowUse {
        public final Source source;
        public final Use use;
        public final int seedLiterals;
        private RowUse(Source source,Use use,int seeds) { this.source = source; this.use = use; seedLiterals = seeds; }
    }
    public enum Status { COLLISION, FIXED_POINT, INCOMPLETE }
    public static final class Evaluation {
        public final Status status;
        public final String reason;
        final ContextWitnessFamilyQualification.Result qualification;
        private Evaluation(ContextWitnessFamilyQualification.Result qualification) {
            this.qualification = qualification; status = Status.valueOf(qualification.status.name()); reason = qualification.reason;
        }
    }
    public static final class Artifact {
        public final Snapshot x;
        public final List<Snapshot> foreign;
        public final List<RowUse> rows;
        public final int seedAtoms;
        final Packet input;
        final ContextWitnessFamilyFixedPoint.Plan plan;
        private Artifact(Snapshot x,List<Snapshot> foreign,List<RowUse> rows,Packet input,ContextWitnessFamilyFixedPoint.Plan plan) {
            this.x = x; this.foreign = Collections.unmodifiableList(new ArrayList<>(foreign)); this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.input = input; this.plan = plan; seedAtoms = input.items.size();
        }
        /** Historical diagnostic on these detached snapshots; a live provider must guard its own operation. */
        public Evaluation evaluate(int maximumRounds,int maximumDepth,int maximumWork,int maximumEvents) {
            return new Evaluation(ContextWitnessFamilyQualification.run(plan,input,
                    new ContextWitnessFamilyFixedPoint.Limits(maximumRounds,maximumDepth,maximumWork,maximumEvents)));
        }
    }
    public static final class Result {
        public final boolean assembled;
        public final String reason;
        public final Artifact artifact;
        private Result(boolean assembled,String reason,Artifact artifact) { this.assembled = assembled; this.reason = reason; this.artifact = artifact; }
    }
    static void identity(Snapshot snapshot,boolean localX,Runnable work) {
        ContextProductionInventory inventory = snapshot.inventory;
        if (inventory.localX != localX || snapshot.primary.size() != inventory.primary.size()) throw new IllegalStateException("Bound snapshot role/primary inventory mismatch");
        Set<Long> expected = new HashSet<>(); for (ContextProductionInventory.Row row : inventory.primary) { work.run(); expected.add(row.nativeRule); }
        for (Entry entry : snapshot.primary) {
            work.run(); Source source = entry.source;
            if (!source.context.equals(inventory.context) || source.revision != inventory.revision || source.localX != localX
                    || !source.view.equals("inventory:"+inventory.fingerprint) || !expected.remove(source.rule))
                throw new IllegalStateException("Bound primary source/view mismatch");
        }
        if (!expected.isEmpty()) throw new IllegalStateException("Unaccounted primary inventory rows");
    }
    private static Cut cut(Snapshot snapshot) {
        return Cut.of(snapshot.inventory);
    }
    public static Result assemble(Snapshot x,List<Snapshot> foreign,int maximumWork) {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded family assembly required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Family assembly work limit"); };
        try {
            List<Snapshot> sources = new ArrayList<>(foreign);
            if (sources.size() > 15) throw new IllegalStateException("Family source limit");
            identity(x,true,work); Set<UUID> contexts = new HashSet<>(); contexts.add(x.inventory.context);
            for (Snapshot source : sources) {
                identity(source,false,work); if (!contexts.add(source.inventory.context)) throw new IllegalStateException("Repeated family Context");
            }
            ContextWitnessResultTransport.Result seeds = ContextWitnessResultTransport.primarySeeds(x,maximumWork);
            if (!seeds.exported) throw new IllegalStateException(seeds.reason);
            List<Declaration> xProducers = new ArrayList<>(); List<RowUse> rows = new ArrayList<>();
            for (Entry entry : x.primary) {
                work.run(); int literals = 0; boolean producer = false;
                if (entry.bundle != null) literals = entry.bundle.atoms.size();
                else for (Clause clause : entry.clauses) {
                    work.run(); if (clause.variables.isEmpty() && clause.literals.size() == 1) ++literals; else producer = true;
                }
                if (producer) xProducers.add(new Declaration(entry.source,entry.statement));
                rows.add(new RowUse(entry.source,producer ? (literals == 0 ? Use.X_PRODUCER : Use.X_SEEDS_AND_PRODUCER) : Use.X_SEEDS,literals));
            }
            List<Branch> branches = new ArrayList<>();
            branches.add(xProducers.isEmpty() ? ContextWitnessFamilyFixedPoint.emptyBranch(ContextWitnessEmptyTarget.of(x,work),xProducers)
                    : ContextWitnessFamilyFixedPoint.branch(cut(x),xProducers));
            for (Snapshot source : sources) {
                List<Declaration> declarations = new ArrayList<>(xProducers);
                for (Entry entry : source.primary) {
                    work.run(); declarations.add(new Declaration(entry.source,entry.statement)); rows.add(new RowUse(entry.source,Use.FOREIGN_CUT,0));
                }
                branches.add(source.primary.isEmpty() ? ContextWitnessFamilyFixedPoint.emptyBranch(ContextWitnessEmptyTarget.of(source,work),declarations)
                        : ContextWitnessFamilyFixedPoint.branch(cut(source),declarations));
            }
            ContextWitnessFamilyFixedPoint.Plan plan = ContextWitnessFamilyFixedPoint.plan(branches);
            return new Result(true,"Every bound primary row accounted as X seeds/producers or its own foreign cut",new Artifact(x,sources,rows,seeds.packet,plan));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
}
