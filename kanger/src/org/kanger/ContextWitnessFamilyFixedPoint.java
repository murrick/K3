/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.*;

/** Synchronous fixed point relative to sealed positive Horn declaration snapshots; no authoring admission. */
final class ContextWitnessFamilyFixedPoint {
    interface BarrierCheck {
        void inspect(Packet packet) throws Exception;
        default void inspect(Packet packet,Map<Cut,PrivatePool> privatePools)throws Exception { inspect(packet); }
    }
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
        final ContextWitnessEmptyTarget emptyTarget;
        final PrivatePool privatePool;
        private Branch(Cut cut,List<Declaration> declarations,ContextWitnessEmptyTarget emptyTarget,PrivatePool privatePool) {
            this.privatePool=privatePool;
            this.emptyTarget = emptyTarget;
            if (emptyTarget != null) emptyTarget.validate(cut,() -> {});
            this.cut = Objects.requireNonNull(cut); this.declarations = Collections.unmodifiableList(new ArrayList<>(declarations));
        }
    }
    static Branch branch(Cut cut,List<Declaration> declarations) { return new Branch(cut,declarations,null,null); }
    static Branch emptyBranch(ContextWitnessEmptyTarget emptyTarget,List<Declaration> declarations) {
        return new Branch(emptyTarget.cut,declarations,emptyTarget,null);
    }
    static Branch privateBranch(PrivatePool pool,List<Declaration> declarations) {
        return new Branch(pool.cut,declarations,null,pool);
    }
    static List<Input> captureBranch(Branch branch,Packet input,PrivatePool pool,Limits limits,Runnable work)throws Exception {
        if(branch.privatePool==null)return ContextWitnessDeclarationCut.capture(input,branch.cut,branch.declarations,work,limits.work,limits.events,branch.emptyTarget);
        if(pool==null||pool.common!=input||!pool.cut.equals(branch.cut))throw new IllegalStateException("Missing exact private barrier state");
        return ContextWitnessDeclarationCut.capturePrivate(pool,branch.declarations,work,limits.work,limits.events);
    }
    static Set<Source> sources(Branch branch) {
        Set<Source> sources=new LinkedHashSet<>();for(Declaration declaration:branch.declarations)sources.add(declaration.source);
        return sources;
    }
    static final class Round {
        final UUID input, output;
        final boolean changed, commonChanged, privateChanged;
        final int productive, unchanged;
        private Round(Packet input,Packet output,boolean commonChanged,boolean privateChanged,int productive,int unchanged) {
            this.input = input.identity; this.output = output.identity; this.commonChanged=commonChanged; this.privateChanged=privateChanged; this.changed = commonChanged||privateChanged;
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
        final Map<Cut,PrivatePool> privatePools;
        private Result(Status status,String reason,Packet packet,List<Round> rounds,Plan plan,Packet initial,Map<Cut,PrivatePool> privatePools) {
            this.privatePools=Collections.unmodifiableMap(new LinkedHashMap<>(privatePools));
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
                branch.cut.claimContexts(contexts,()->{});
                cuts.add(branch.cut);
                if (branch.cut.localX) {
                    if (self != null) throw new IllegalArgumentException("Exactly one self-X branch required"); self = branch;
                }
                if (branch.declarations.isEmpty() && branch.emptyTarget == null && branch.privatePool == null || branch.declarations.size() > 64) throw new IllegalArgumentException("Bounded nonempty declaration snapshot required");
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
        Map<Cut,PrivatePool> privateStates(Packet common,int maximumWork) {
            Map<Cut,PrivatePool> states=new LinkedHashMap<>();
            for(Branch branch:branches)if(branch.privatePool!=null)
                states.put(branch.cut,ContextWitnessResultTransport.rebasePrivate(branch.privatePool,common,maximumWork));
            return Collections.unmodifiableMap(states);
        }
        Result run(Packet input,Limits limits) {
            return run(input,limits,null);
        }
        Result run(Packet input,Limits limits,BarrierCheck check) {
            List<Round> trace = new ArrayList<>(); Packet current = input;
            try {
                // Validate the whole plan before any native pass, including unsupported later branches.
                ContextWitnessResultTransport.family(input,expected,limits.work);
                Map<Cut,PrivatePool> states=privateStates(input,limits.work);
                for (Branch branch : branches) {
                    Runnable work = budget(limits.work);
                    List<Input> definitions = captureBranch(branch,input,states.get(branch.cut),limits,work);
                    ContextWitnessHornClosure.validate(branch.privatePool==null?input:states.get(branch.cut).local,definitions,limits.depth,work);
                }
                for (int number=0;number<limits.rounds;++number) {
                    Family barrier = ContextWitnessResultTransport.family(current,expected,limits.work);
                    Map<Cut,Packet> outputs = new LinkedHashMap<>(); int productive = 0, unchanged = 0; boolean privateChanged=false;
                    Map<Cut,PrivatePool> nextStates=new LinkedHashMap<>();
                    for (Branch branch : branches) {
                        if(branch.privatePool!=null) {
                            PrivateAdvance step;
                            try { step=ContextWitnessResultTransport.closedPrivate(states.get(branch.cut),sources(branch),limits.depth,limits.work,limits.events); }
                            catch(Exception notClosed) { step=ContextWitnessResultTransport.advancePrivate(states.get(branch.cut),sources(branch),limits.depth,limits.work,limits.events); }
                            outputs.put(branch.cut,ContextWitnessResultTransport.privateRoundOutput(step,limits.work));
                            nextStates.put(branch.cut,step.after);privateChanged|=step.localChanged;
                            if(step.localChanged||step.commonChanged)++productive;else ++unchanged;
                            continue;
                        }
                        ContextWitnessResultTransport.Result result = ContextWitnessResultTransport.exportHornUnchanged(
                                current,branch.cut,branch.declarations,limits.depth,limits.work,limits.events,branch.emptyTarget);
                        if (result.exported) ++unchanged;
                        else {
                            try (ContextWitnessSeedReplay nativePass = ContextWitnessSeedReplay.open(current,branch.cut,limits.work,limits.events)) {
                                for (Declaration declaration : branch.declarations) nativePass.rule(declaration.source,declaration.statement);
                                result = ContextWitnessResultTransport.exportRoundNext(nativePass,branch.cut,branch.declarations,
                                        limits.depth,limits.work,limits.events,branch.emptyTarget);
                            }
                            if (!result.exported) throw new IllegalStateException("Round "+(number+1)+" branch incomplete: "+result.reason);
                            ++productive;
                        }
                        if (!branch.cut.equals(result.packet.coveredCut) && !branch.cut.equals(result.packet.roundCut))
                            throw new IllegalStateException("Uncovered branch cannot establish family barrier");
                        outputs.put(branch.cut,result.packet);
                    }
                    FamilyResult merged = barrier.finish(outputs,limits.work);
                    if (merged.status != FamilyStatus.MERGED) throw new IllegalStateException("Family merge incomplete: "+merged.reason);
                    trace.add(new Round(current,merged.packet,merged.changed,privateChanged,productive,unchanged)); current = merged.packet;
                    Map<Cut,PrivatePool> rebased=new LinkedHashMap<>();
                    for(Map.Entry<Cut,PrivatePool> entry:nextStates.entrySet())
                        rebased.put(entry.getKey(),ContextWitnessResultTransport.rebasePrivate(entry.getValue(),current,limits.work));
                    states=Collections.unmodifiableMap(rebased);
                    if (check != null) check.inspect(current,states);
                    if (!merged.changed&&!privateChanged) {
                        // Round coverage never substitutes for a full unchanged-pool closure verdict.
                        for (Branch branch : branches) {
                            if(branch.privatePool!=null) {
                                ContextWitnessResultTransport.verifyPrivateClosed(states.get(branch.cut),sources(branch),limits.depth,limits.work,limits.events);
                                continue;
                            }
                            ContextWitnessResultTransport.Result closed = ContextWitnessResultTransport.exportHornUnchanged(
                                    current,branch.cut,branch.declarations,limits.depth,limits.work,limits.events,branch.emptyTarget);
                            if (!closed.exported) throw new IllegalStateException("Unchanged barrier is not closed: "+closed.reason);
                        }
                        return new Result(Status.FIXED_POINT,"Supplied positive Horn family proof pools reached a common fixed point; admission pending",current,trace,this,input,states);
                    }
                }
                throw new IllegalStateException("Family round limit before an unchanged fully covered barrier");
            } catch (Exception failure) {
                return new Result(Status.INCOMPLETE,failure.getMessage(),null,Collections.emptyList(),this,input,Collections.emptyMap());
            }
        }
    }
    static Plan plan(List<Branch> branches) { return new Plan(branches); }
    private static Runnable budget(int work) {
        int[] remaining = {work}; return () -> { if (--remaining[0] < 0) throw new IllegalStateException("Family preflight work limit"); };
    }
}
