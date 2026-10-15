/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundCutEvidence.Production;
import org.kanger.ContextGroundCutEvidence.Result;

/** Sealed common-input transport of observed signed proofs; no coverage/fixed-point verdict. */
public final class ContextGroundProductionRounds {
    public static final class Frontier {
        public final UUID identity = UUID.randomUUID(), basis;
        public final Snapshot x;
        public final List<Snapshot> cuts;
        public final List<Production> productions;
        private Frontier(Snapshot x,List<Snapshot> cuts,List<Production> productions,UUID basis) {
            this.x = x; this.cuts = Collections.unmodifiableList(new ArrayList<>(cuts));
            this.productions = Collections.unmodifiableList(new ArrayList<>(productions)); this.basis = basis;
        }
    }
    /** Two proved opposite ordinary literals, including an X-dependent side. Not a production. */
    public static final class Collision {
        public final Fact left,right;
        public final Support leftProof,rightProof;
        private Collision(Fact left,Fact right,Support leftProof,Support rightProof) {
            this.left = left; this.right = right; this.leftProof = leftProof; this.rightProof = rightProof;
        }
    }
    public enum Status { MERGED, COLLISION, INCOMPLETE }
    public static final class Barrier {
        public final Status status;
        public final String reason;
        public final Frontier frontier;
        public final boolean changed;
        public final List<Collision> collisions;
        private Barrier(Status status,String reason,Frontier frontier,boolean changed,List<Collision> collisions) {
            this.status = status; this.reason = reason; this.frontier = frontier; this.changed = changed;
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
        }
    }
    static Runnable budget(int maximumWork) {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded ground transport required");
        int[] remaining = {maximumWork}; return () -> { if (--remaining[0] < 0) throw new IllegalStateException("Ground transport work limit"); };
    }
    public static Frontier start(Snapshot x,List<Snapshot> foreign,int maximumWork) {
        Runnable work = budget(maximumWork); ContextProductionFamilyAssembly.identity(x,true,work);
        if (foreign.size() > 15) throw new IllegalStateException("Ground family source limit");
        List<Snapshot> cuts = new ArrayList<>(); cuts.add(x); Set<UUID> ids = new HashSet<>(); ids.add(x.inventory.context);
        for (Snapshot cut : new ArrayList<>(foreign)) {
            work.run(); ContextProductionFamilyAssembly.identity(cut,false,work);
            if (!ids.add(cut.inventory.context)) throw new IllegalStateException("Repeated ground family context"); cuts.add(cut);
        }
        for (Snapshot cut : cuts) ContextGroundCutEvidence.declarations(x,cut == x ? null : cut,work);
        return new Frontier(x,cuts,Collections.emptyList(),null);
    }
    static Collision opposition(Fact left,Support leftProof,Fact right,Support rightProof,Runnable work) {
        work.run(); return left.opposite().equivalent(right) ? new Collision(left,right,leftProof,rightProof) : null;
    }
    private static void add(List<Production> pool,Production next,Runnable work) {
        boolean sameAtom = false; int atoms = 0;
        for (int i=0;i<pool.size();++i) {
            work.run(); Production old = pool.get(i);
            if (old.conclusion.equivalent(next.conclusion)) {
                sameAtom = true;
                if (old.proof.sources.equals(next.proof.sources)) { if (next.proof.depth < old.proof.depth) pool.set(i,next); return; }
            }
            boolean first = true;
            for (int j=0;j<i;++j) { work.run(); if (pool.get(j).conclusion.equivalent(old.conclusion)) { first = false; break; } }
            if (first) ++atoms;
        }
        if (pool.size() >= 256 || !sameAtom && atoms >= 64) throw new IllegalStateException("Ground transport proof/atom limit");
        pool.add(next);
    }
    private static boolean same(List<Production> left,List<Production> right,Runnable work) {
        if (left.size() != right.size()) return false;
        for (Production a : left) {
            boolean found = false;
            for (Production b : right) { work.run(); if (a.conclusion.equivalent(b.conclusion) && a.proof.equals(b.proof)) { found = true; break; } }
            if (!found) return false;
        }
        return true;
    }
    public static Barrier merge(Frontier input,List<Result> results,int maximumWork) {
        Runnable work = budget(maximumWork);
        try {
            List<Result> outputs = new ArrayList<>(results);
            if (outputs.size() != input.cuts.size()) throw new IllegalStateException("Incomplete ground family barrier");
            Set<Snapshot> seen = new HashSet<>();
            for (Result output : outputs) {
                work.run();
                if (output == null || !output.verified || output.input != input || !input.cuts.contains(output.target) || !seen.add(output.target))
                    throw new IllegalStateException("Ground branch has wrong scope, input barrier or incomplete evidence");
            }
            // Publish no collision prefix before every expected cut's result is authenticated.
            List<Collision> collisions = new ArrayList<>();
            for (Result output : outputs) { work.run(); collisions.addAll(output.collisions); }
            if (!collisions.isEmpty()) return new Barrier(Status.COLLISION,"Proved sourced ground cut collision",null,false,collisions);
            List<Production> pool = new ArrayList<>(input.productions);
            for (Result output : outputs) for (Production production : output.productions) { work.run(); add(pool,production,work); }
            for (int i=0;i<pool.size();++i) for (int j=i+1;j<pool.size();++j) {
                Collision collision = opposition(pool.get(i).conclusion,pool.get(i).proof,pool.get(j).conclusion,pool.get(j).proof,work);
                if (collision != null) collisions.add(collision);
            }
            if (!collisions.isEmpty()) return new Barrier(Status.COLLISION,"Opposite X-dependent ground productions",null,false,collisions);
            return new Barrier(Status.MERGED,"Observed ground family merged; closure/admission unknown",
                    new Frontier(input.x,input.cuts,pool,input.identity),!same(input.productions,pool,work),Collections.emptyList());
        } catch (Exception failure) { return new Barrier(Status.INCOMPLETE,failure.getMessage(),null,false,Collections.emptyList()); }
    }
}
