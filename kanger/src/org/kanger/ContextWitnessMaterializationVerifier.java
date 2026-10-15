/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Snapshot;
import org.kanger.ContextWitnessBundle.Session;
import org.kanger.ContextWitnessProjection.*;

/** Per-occurrence correspondence only; does not equate roots across applications. */
final class ContextWitnessMaterializationVerifier {
    static final class Relation {
        final UUID replay;
        final Materialization occurrence;
        final List<ContextWitnessProjection> applications;
        Relation(UUID replay,Materialization occurrence,List<ContextWitnessProjection> applications) {
            this.replay = replay; this.occurrence = occurrence;
            this.applications = Collections.unmodifiableList(new ArrayList<>(applications));
        }
    }
    static final class Result {
        final boolean verified;
        final String reason;
        final List<Relation> relations;
        Result(boolean verified,String reason,List<Relation> relations) {
            this.verified = verified; this.reason = reason;
            this.relations = Collections.unmodifiableList(new ArrayList<>(relations));
        }
    }
    private static Result incomplete(String reason) { return new Result(false,reason,Collections.emptyList()); }
    private static boolean sameNode(Node left,Node right) {
        return left.term == right.term && left.ownerRule == right.ownerRule && left.index == right.index && left.domini == right.domini;
    }
    private static boolean sameMatch(Literal left,Literal right,Runnable work) {
        work.run();
        if (!left.predicate.equals(right.predicate) || left.positive != right.positive || left.arguments.size() != right.arguments.size()) return false;
        for (int i=0;i<left.arguments.size();++i) {
            work.run(); Slot a = left.arguments.get(i), b = right.arguments.get(i);
            if (a.lineage.size() != b.lineage.size()) return false;
            if (a.lineage.isEmpty()) { if (!a.value.semanticallyEquals(b.value)) return false; }
            else for (int j=0;j<a.lineage.size();++j) { work.run(); if (!sameNode(a.lineage.get(j),b.lineage.get(j))) return false; }
        }
        return true;
    }
    static boolean alphaPreserving(Literal matched,Literal stored,Runnable work) {
        work.run();
        if (!matched.predicate.equals(stored.predicate) || matched.positive != stored.positive || matched.arguments.size() != stored.arguments.size()) return false;
        Map<Long,Long> forward = new HashMap<>(), reverse = new HashMap<>();
        for (int i=0;i<matched.arguments.size();++i) {
            work.run(); Slot a = matched.arguments.get(i), b = stored.arguments.get(i);
            if (a.lineage.isEmpty() || b.lineage.isEmpty()) {
                if (!a.lineage.isEmpty() || !b.lineage.isEmpty() || !a.value.semanticallyEquals(b.value)) return false;
            } else {
                if (b.lineage.size() != 1) return false;
                Node source = a.lineage.get(0), target = b.lineage.get(0);
                if (source.domini != target.domini) return false;
                Long oldTarget = forward.putIfAbsent(source.term,target.term), oldSource = reverse.putIfAbsent(target.term,source.term);
                if (oldTarget != null && oldTarget != target.term || oldSource != null && oldSource != source.term) return false;
            }
        }
        return true;
    }
    static Result verify(UUID replay,Snapshot snapshot,Runnable work) {
        List<Relation> relations = new ArrayList<>();
        if (snapshot.gaps.contains("observation-budget-exhausted")) return incomplete("observation-budget-exhausted");
        for (Materialization occurrence : snapshot.witnessMaterializations) {
            work.run();
            if (!alphaPreserving(occurrence.matched,occurrence.stored,work)) return incomplete("witness-materialization-shape-mismatch");
            List<ContextWitnessProjection> applications = new ArrayList<>();
            for (ContextWitnessProjection application : snapshot.witnessProjections) {
                work.run();
                if (application.nativeRule == occurrence.producerRule && sameMatch(application.conclusion,occurrence.matched,work)
                        && application.producerSupports.equals(occurrence.producerSupports)) applications.add(application);
            }
            if (applications.isEmpty()) return incomplete("witness-materialization-application-missing");
            relations.add(new Relation(replay,occurrence,applications));
        }
        return relations.isEmpty() ? incomplete("no-witness-materializations") : new Result(true,"Observed witness materializations matched",relations);
    }
    static Result inspect(Session session,int maximumWork) {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded materialization verification required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness materialization work limit"); };
        try { return verify(session.replay,session.capture.snapshot(),work); }
        catch (IllegalStateException failure) { return incomplete(failure.getMessage()); }
    }
}
