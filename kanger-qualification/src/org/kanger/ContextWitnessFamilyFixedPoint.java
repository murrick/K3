/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessResultTransport.*;

/** Synchronous fixed point relative to sealed positive Horn declaration snapshots; no authoring admission. */
final class ContextWitnessFamilyFixedPoint {
    interface BarrierCheck { void inspect(Packet packet) throws Exception; }
    enum Status { FIXED_POINT, INCOMPLETE }
    static final class Limits {
        final int rounds, depth, work, events;
        Limits(int rounds,int depth,int work,int events) {
            if (rounds < 1 || depth < 0 || work < 1 || events < 1) throw new IllegalArgumentException("Bounded family execution required");
            this.rounds = rounds; this.depth = depth; this.work = work; this.events = events;
        }
    }
    static final class Branch {
        final Cut cut;
        final List<Declaration> declarations;
        private Branch(Cut cut,List<Declaration> declarations) {
            this.cut = Objects.requireNonNull(cut); this.declarations = Collections.unmodifiableList(new ArrayList<>(declarations));
        }
    }
    static Branch branch(Cut cut,List<Declaration> declarations) { return new Branch(cut,declarations); }
    static final class Round {
        final UUID input, output;
        final boolean changed;
        final int productive, unchanged;
        private Round(Packet input,Packet output,boolean changed,int productive,int unchanged) {
            this.input = input.identity; this.output = output.identity; this.changed = changed;
            this.productive = productive; this.unchanged = unchanged;
        }
    }
    static final class Result {
        final Status status;
        final String reason;
        final Packet packet;
        final List<Round> rounds;
        final Plan plan;
        final UUID initialIdentity;
        private Result(Status status,String reason,Packet packet,List<Round> rounds,Plan plan,Packet initial) {
            this.status = status; this.reason = reason; this.packet = packet;
            this.rounds = Collections.unmodifiableList(new ArrayList<>(rounds));
            this.plan = plan; initialIdentity = initial.identity;
        }
    }
    static final class Plan {
        final List<Branch> branches;
        private final List<Cut> expected;
        private Plan(List<Branch> branches) {
            if (branches.isEmpty() || branches.size() > 16) throw new IllegalArgumentException("Bounded nonempty fixed-point family required");
            this.branches = Collections.unmodifiableList(new ArrayList<>(branches));
            List<Cut> cuts = new ArrayList<>(); Set<UUID> contexts = new HashSet<>(); Branch self = null;
            for (Branch branch : this.branches) {
                if (!contexts.add(branch.cut.context)) throw new IllegalArgumentException("Repeated fixed-point context");
                cuts.add(branch.cut);
                if (branch.cut.localX) {
                    if (self != null) throw new IllegalArgumentException("Exactly one self-X branch required"); self = branch;
                }
                if (branch.declarations.isEmpty() || branch.declarations.size() > 64) throw new IllegalArgumentException("Bounded nonempty declaration snapshot required");
            }
            if (self == null) throw new IllegalArgumentException("Exactly one self-X branch required");
            Map<Source,String> shared = xDeclarations(self);
            if (shared.size() != self.declarations.size()) throw new IllegalArgumentException("Self-X snapshot contains foreign or duplicate sources");
            for (Branch branch : this.branches)
                if (!shared.equals(xDeclarations(branch))) throw new IllegalArgumentException("Every branch must carry the same sealed X declarations");
            expected = Collections.unmodifiableList(cuts);
        }
        private static Map<Source,String> xDeclarations(Branch branch) {
            Map<Source,String> result = new LinkedHashMap<>();
            for (Declaration declaration : branch.declarations) if (declaration.source.localX)
                if (result.put(declaration.source,declaration.statement) != null) throw new IllegalArgumentException("Repeated X declaration source");
            return result;
        }
        Result run(Packet input,Limits limits) {
            return run(input,limits,null);
        }
        Result run(Packet input,Limits limits,BarrierCheck check) {
            List<Round> trace = new ArrayList<>(); Packet current = input;
            try {
                // Validate the whole plan before any native pass, including unsupported later branches.
                ContextWitnessResultTransport.family(input,expected,limits.work);
                for (Branch branch : branches) {
                    Runnable work = budget(limits.work);
                    List<Input> definitions = ContextWitnessDeclarationCut.capture(input,branch.cut,branch.declarations,work,limits.work,limits.events);
                    ContextWitnessHornClosure.validate(input,definitions,limits.depth,work);
                }
                for (int number=0;number<limits.rounds;++number) {
                    Family barrier = ContextWitnessResultTransport.family(current,expected,limits.work);
                    Map<Cut,Packet> outputs = new LinkedHashMap<>(); int productive = 0, unchanged = 0;
                    for (Branch branch : branches) {
                        ContextWitnessResultTransport.Result result = ContextWitnessResultTransport.exportHornUnchanged(
                                current,branch.cut,branch.declarations,limits.depth,limits.work,limits.events);
                        if (result.exported) ++unchanged;
                        else {
                            try (ContextWitnessSeedReplay nativePass = ContextWitnessSeedReplay.open(current,limits.work,limits.events)) {
                                for (Declaration declaration : branch.declarations) nativePass.rule(declaration.source,declaration.statement);
                                result = ContextWitnessResultTransport.exportCoveredNext(nativePass,branch.cut,branch.declarations,
                                        limits.depth,limits.work,limits.events);
                            }
                            if (!result.exported) throw new IllegalStateException("Round "+(number+1)+" branch incomplete: "+result.reason);
                            ++productive;
                        }
                        if (result.packet.coveredCut == null || !result.packet.coveredCut.equals(branch.cut))
                            throw new IllegalStateException("Uncovered branch cannot establish family fixed point");
                        outputs.put(branch.cut,result.packet);
                    }
                    FamilyResult merged = barrier.finish(outputs,limits.work);
                    if (merged.status != FamilyStatus.MERGED) throw new IllegalStateException("Family merge incomplete: "+merged.reason);
                    trace.add(new Round(current,merged.packet,merged.changed,productive,unchanged)); current = merged.packet;
                    if (check != null) check.inspect(current);
                    if (!merged.changed) return new Result(Status.FIXED_POINT,"Supplied positive Horn family proof pools reached a common fixed point; admission pending",current,trace,this,input);
                }
                throw new IllegalStateException("Family round limit before an unchanged fully covered barrier");
            } catch (Exception failure) {
                return new Result(Status.INCOMPLETE,failure.getMessage(),null,Collections.emptyList(),this,input);
            }
        }
    }
    static Plan plan(List<Branch> branches) { return new Plan(branches); }
    private static Runnable budget(int work) {
        int[] remaining = {work}; return () -> { if (--remaining[0] < 0) throw new IllegalStateException("Family preflight work limit"); };
    }
}
