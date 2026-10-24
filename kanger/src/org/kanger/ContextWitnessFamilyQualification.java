/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessFamilyFixedPoint.Branch;
import org.kanger.ContextWitnessFamilyFixedPoint.Limits;
import org.kanger.ContextWitnessFamilyFixedPoint.Plan;

/** Positive refutation discovery over full supplied cuts; omitted productive coverage can never admit X. */
final class ContextWitnessFamilyQualification {
    enum Status { COLLISION, FIXED_POINT, INCOMPLETE }
    static final class Result {
        final Status status;
        final String reason;
        final Plan supplied;
        final UUID initialIdentity;
        final List<ContextWitnessClauseCollision.Result> collisions;
        final Set<Source> uncovered;
        final ContextWitnessFamilyFixedPoint.Result fixedPoint;
        final int barrier;
        private Result(Status status,String reason,Plan supplied,Packet initial,List<ContextWitnessClauseCollision.Result> collisions,
                Set<Source> uncovered,ContextWitnessFamilyFixedPoint.Result fixedPoint,int barrier) {
            this.status = status; this.reason = reason; this.supplied = supplied; initialIdentity = initial.identity;
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
            this.uncovered = Collections.unmodifiableSet(new LinkedHashSet<>(uncovered)); this.fixedPoint = fixedPoint; this.barrier = barrier;
        }
    }
    private static final class Limit extends IllegalStateException { Limit() { super("Family qualification work limit"); } }
    private static Runnable budget(int maximumWork) {
        int[] remaining = {maximumWork}; return () -> { if (--remaining[0] < 0) throw new Limit(); };
    }
    private static final class Scan implements ContextWitnessFamilyFixedPoint.BarrierCheck {
        final Plan supplied;
        final Limits limits;
        int barrier = -1;
        List<ContextWitnessClauseCollision.Result> collisions = Collections.emptyList();
        Scan(Plan supplied,Limits limits) { this.supplied = supplied; this.limits = limits; }
        public void inspect(Packet packet) {
            inspect(packet,supplied.privateStates(packet,limits.work));
        }
        public void inspect(Packet packet,Map<Cut,PrivatePool> privatePools) {
            ++barrier; List<ContextWitnessClauseCollision.Result> found = new ArrayList<>();
            for (Branch branch : supplied.branches) {
                PrivatePool local=privatePools.get(branch.cut);
                if(branch.privatePool!=null&&(local==null||local.common!=packet))throw new IllegalStateException("Missing private full-cut collision state");
                ContextWitnessClauseCollision.Result scan = branch.privatePool==null ? ContextWitnessClauseCollision.inspect(packet,branch.cut,branch.declarations,
                        limits.depth,limits.work,limits.events,branch.emptyTarget)
                        : ContextWitnessClauseCollision.inspectPrivate(local,limits.depth,limits.work,limits.events);
                if (!scan.scanned) throw new IllegalStateException("Incomplete full-cut collision scan: "+scan.reason);
                if (scan.status == ContextWitnessClauseCollision.Status.COLLISION) found.add(scan);
            }
            // Commit evidence only after every supplied cut's scan completed at this barrier.
            if (!found.isEmpty()) { collisions = found; throw new IllegalStateException("Proved full-cut collision"); }
        }
    }
    static Result run(Plan supplied,Packet input,Limits limits) {
        Set<Source> uncovered = new LinkedHashSet<>(); Scan scan = new Scan(supplied,limits);
        try {
            List<Cut> cuts = new ArrayList<>(); for (Branch branch : supplied.branches) cuts.add(branch.cut);
            ContextWitnessResultTransport.family(input,cuts,limits.work);
            Map<Cut,PrivatePool> privatePools=supplied.privateStates(input,limits.work);
            // Preflight every original cut before inference or publication of any collision prefix.
            List<Branch> producerBranches = new ArrayList<>(); boolean self = false;
            for (Branch branch : supplied.branches) {
                Runnable work = budget(limits.work);
                List<Input> definitions = ContextWitnessFamilyFixedPoint.captureBranch(branch,input,privatePools.get(branch.cut),limits,work);
                Packet proofPool=branch.privatePool==null?input:privatePools.get(branch.cut).local;
                ContextWitnessHornClosure.validate(proofPool,Collections.emptyList(),limits.depth,work);
                Map<Source,Boolean> eligible = new HashMap<>();
                for (Input definition : definitions) {
                    boolean supported = true;
                    try { ContextWitnessHornClosure.validate(proofPool,Collections.singletonList(definition),limits.depth,work); }
                    catch (Limit stopped) { throw stopped; }
                    catch (IllegalStateException outside) { supported = false; uncovered.add(definition.source); }
                    eligible.put(definition.source,supported);
                }
                List<Declaration> producers = new ArrayList<>(); boolean target = branch.emptyTarget != null || branch.privatePool!=null;
                for (Declaration declaration : branch.declarations) if (eligible.get(declaration.source)) {
                    producers.add(declaration); target |= branch.cut.contains(Cut.of(declaration.source));
                }
                if (target) {
                    producerBranches.add(branch.privatePool!=null ? ContextWitnessFamilyFixedPoint.privateBranch(branch.privatePool,producers)
                            : branch.emptyTarget == null ? ContextWitnessFamilyFixedPoint.branch(branch.cut,producers)
                            : ContextWitnessFamilyFixedPoint.emptyBranch(branch.emptyTarget,producers)); self |= branch.cut.localX;
                }
            }
            scan.inspect(input,privatePools); // barrier0: a contradiction may already be proved without any inference.
            if (!self) throw new IllegalStateException("No supported self-X producer snapshot; full-cut coverage remains incomplete");
            Plan discovery = ContextWitnessFamilyFixedPoint.plan(producerBranches);
            ContextWitnessFamilyFixedPoint.Result closed = discovery.run(input,limits,scan);
            if (!scan.collisions.isEmpty()) return collision(supplied,input,uncovered,scan);
            if (closed.status != ContextWitnessFamilyFixedPoint.Status.FIXED_POINT) throw new IllegalStateException(closed.reason);
            if (!uncovered.isEmpty()) throw new IllegalStateException("Positive discovery closed, but full supplied declaration coverage is incomplete");
            return new Result(Status.FIXED_POINT,"All supplied declarations covered and all direct collision scans completed; admission still pending",
                    supplied,input,Collections.emptyList(),uncovered,closed,scan.barrier);
        } catch (Exception failure) {
            if (!scan.collisions.isEmpty()) return collision(supplied,input,uncovered,scan);
            return new Result(Status.INCOMPLETE,failure.getMessage(),supplied,input,Collections.emptyList(),uncovered,null,-1);
        }
    }
    private static Result collision(Plan supplied,Packet input,Set<Source> uncovered,Scan scan) {
        return new Result(Status.COLLISION,"Proved sourced clause refutation at common barrier "+scan.barrier,
                supplied,input,scan.collisions,uncovered,null,scan.barrier);
    }
}
