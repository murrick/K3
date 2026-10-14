/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;

/**
 * Qualification-only reference for the observed ordinary-ground production
 * surface. It is not connected to publication gates. Unsupported residuals
 * and resource stops return INCOMPLETE, never SATURATED.
 */
public final class ContextProductionRoundsReference {
    public static final class Declaration {
        public final Source source;
        public final String statement;
        public Declaration(Source source, String statement) {
            this.source = Objects.requireNonNull(source); this.statement = Objects.requireNonNull(statement);
        }
    }
    public static final class Limits {
        public final int rounds, events, work, depth;
        public Limits(int rounds, int events, int work, int depth) {
            if (rounds < 1 || events < 1 || work < 1 || depth < 1) throw new IllegalArgumentException("Positive limits required");
            this.rounds = rounds; this.events = events; this.work = work; this.depth = depth;
        }
    }
    public enum Status { SATURATED, COLLISION, INCOMPLETE }
    public static final class Proof {
        public final Set<Source> sources;
        public final int depth;
        private Proof(Set<Source> sources, int depth) {
            this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources)); this.depth = depth;
        }
        public boolean usesX() { for (Source source : sources) if (source.localX) return true; return false; }
    }
    public static final class Product {
        public final Fact fact;
        public final Clause clause;
        public final List<Proof> supports;
        private Product(Bucket b) {
            clause = b.clause; fact = clause.literals.size() == 1 ? clause.literals.get(0) : null; supports = Collections.unmodifiableList(new ArrayList<>(b.proofs));
        }
    }
    public static final class Result {
        public final Status status;
        public final int rounds;
        public final String reason;
        public final List<Product> products;
        public final List<Snapshot> observations;
        public final ContextQualification collision;
        private Result(Status status, int rounds, String reason, List<Bucket> state,
                       List<Snapshot> observations, ContextQualification collision) {
            this.status = status; this.rounds = rounds; this.reason = reason; this.collision = collision;
            List<Product> copy = new ArrayList<>(); for (Bucket bucket : state) copy.add(new Product(bucket));
            products = Collections.unmodifiableList(copy);
            this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
        }
    }
    private static final class Bucket {
        final Clause clause;
        final List<Proof> proofs = new ArrayList<>();
        Bucket(Clause clause) { this.clause = clause; }
    }
    private static final class Budget {
        int remaining;
        Budget(int work) { remaining = work; }
        void take() { if (--remaining < 0) throw new IllegalStateException("Proof work limit; qualification incomplete"); }
    }
    private static Bucket find(List<Bucket> pool, Clause clause) {
        for (Bucket bucket : pool) if (bucket.clause.equivalent(clause)) return bucket;
        return null;
    }
    private static boolean add(List<Bucket> pool, Clause clause, Proof proof, Limits limits, Budget budget) {
        budget.take();
        Bucket bucket = find(pool, clause);
        if (bucket != null) for (int i = 0; i < bucket.proofs.size(); ++i) {
            Proof old = bucket.proofs.get(i);
            if (old.sources.equals(proof.sources)) {
                if (proof.depth < old.depth) { bucket.proofs.set(i, proof); return true; }
                return false;
            }
        }
        if (proof.depth > limits.depth) throw new IllegalStateException("Transported derivation depth limit; qualification incomplete");
        if (bucket == null) { bucket = new Bucket(clause); pool.add(bucket); }
        bucket.proofs.add(proof); return true;
    }
    private static List<Bucket> copy(List<Bucket> state) {
        List<Bucket> result = new ArrayList<>();
        for (Bucket bucket : state) { Bucket clone = new Bucket(bucket.clause); clone.proofs.addAll(bucket.proofs); result.add(clone); }
        return result;
    }
    private static boolean derive(Clause conclusion, int slot, Set<Source> sources, int depth,
                                  List<List<Proof>> premises, List<Bucket> pool, Limits limits, Budget budget) {
        budget.take();
        if (slot == premises.size()) return add(pool, conclusion, new Proof(sources, depth + 1), limits, budget);
        boolean changed = false;
        for (Proof proof : premises.get(slot)) {
            Set<Source> joined = new LinkedHashSet<>(sources); joined.addAll(proof.sources);
            changed |= derive(conclusion, slot + 1, joined, Math.max(depth, proof.depth), premises, pool, limits, budget);
        }
        return changed;
    }
    private static List<Bucket> closure(Snapshot snapshot, List<Bucket> state, Limits limits, Budget budget) {
        List<Bucket> pool = copy(state);
        for (Input input : snapshot.inputs) if (input.source != null) for (Clause clause : input.clauses)
            add(pool, clause, new Proof(Collections.singleton(input.source), 0), limits, budget);
        List<Residual> events = new ArrayList<>(snapshot.residuals);
        for (Application app : snapshot.applications)
            events.add(new Residual(new Clause(Collections.singletonList(app.conclusion)), app.premises, app.producerSupports));
        boolean changed;
        do {
            changed = false;
            for (Residual event : events) {
                List<List<Proof>> premises = new ArrayList<>();
                for (Fact fact : event.premises) {
                    Bucket bucket = find(pool, new Clause(Collections.singletonList(fact)));
                    if (bucket == null) { premises = null; break; }
                    premises.add(new ArrayList<>(bucket.proofs));
                }
                if (premises == null) continue;
                for (Support producer : event.producerSupports)
                    changed |= derive(event.conclusion, 0, new LinkedHashSet<>(producer.sources), producer.depth,
                            premises, pool, limits, budget);
            }
        } while (changed);
        for (Residual event : events) for (Fact premise : event.premises)
            if (find(pool, new Clause(Collections.singletonList(premise))) == null)
                throw new IllegalStateException("Unreconstructed native premise; qualification incomplete");
        return pool;
    }
    private static Mind memory(Limits limits) throws Exception {
        User user = new User() {
            @Override public String getProperty(String key, String fallback) throws Exception {
                return "flood.limit".equals(key) ? Integer.toString(limits.depth) : super.getProperty(key, fallback);
            }
        };
        Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind.setFloodControlLimit(limits.depth); return mind;
    }
    public static Result inspect(List<Declaration> x, List<List<Declaration>> targets, Limits limits) throws Exception {
        Objects.requireNonNull(x); Objects.requireNonNull(targets); Objects.requireNonNull(limits);
        List<Declaration> local = Collections.unmodifiableList(new ArrayList<>(x));
        List<List<Declaration>> frozen = new ArrayList<>();
        for (List<Declaration> target : targets) frozen.add(Collections.unmodifiableList(new ArrayList<>(target)));
        if (frozen.isEmpty()) frozen.add(Collections.<Declaration>emptyList());
        for (Declaration declaration : local) if (!declaration.source.localX) throw new IllegalArgumentException("X label required");
        for (List<Declaration> target : frozen) for (Declaration declaration : target)
            if (declaration.source.localX) throw new IllegalArgumentException("External label required");
        List<Bucket> state = new ArrayList<>(); List<Snapshot> observations = new ArrayList<>();
        Budget budget = new Budget(limits.work);
        for (int round = 1; round <= limits.rounds; ++round) {
            List<Bucket> next = copy(state); boolean changed = false;
            List<String> unresolvedResiduals = new ArrayList<>();
            try {
                for (List<Declaration> target : frozen) {
                    // Every participant starts from STATE, never another participant's NEXT.
                    List<Bucket> participantState = copy(state);
                    int remainingEvents = limits.events;
                    while (true) {
                        budget.take();
                        if (remainingEvents < 1) throw new IllegalStateException("Participant observation budget; qualification incomplete");
                        Mind work = memory(limits);
                        Snapshot snapshot;
                        try (ContextProductionCapture capture = ContextProductionCapture.begin(work, remainingEvents);
                             TechnicalMindTransaction transaction = TechnicalMindTransaction.begin(work)) {
                            Mind participant = transaction.mind();
                            participant.setQueryPass(org.kanger.enums.QueryPass.ACCEPT);
                            for (Declaration declaration : local) capture.replay(participant, declaration.source, declaration.statement);
                            for (Bucket bucket : participantState) {
                                List<Support> supports = new ArrayList<>();
                                for (Proof proof : bucket.proofs) supports.add(new Support(proof.sources, proof.depth));
                                capture.replayDerived(participant, bucket.clause, supports);
                            }
                            for (Declaration declaration : target) capture.replay(participant, declaration.source, declaration.statement);
                            participant.link(null, false);
                            Analyzer analyzer = new Analyzer(participant);
                            boolean collision = analyzer.analyze(null, false);
                            ContextQualification qualification = new ContextQualification(!collision, analyzer.getCollisionWitnesses());
                            snapshot = capture.snapshot(); observations.add(snapshot);
                            if (!qualification.isValid())
                                return new Result(Status.COLLISION, round, "Native collision", state, observations, qualification);
                        }
                        remainingEvents -= snapshot.eventCount;
                        for (String gap : snapshot.gaps) if (!gap.startsWith("compound-residual:"))
                            return new Result(Status.INCOMPLETE, round, gap, state, observations, null);
                        List<Bucket> settled = closure(snapshot, participantState, limits, budget);
                        boolean localChanged = false;
                        List<Bucket> localNext = copy(participantState);
                        for (Bucket bucket : settled) for (Proof proof : bucket.proofs)
                            localChanged |= add(localNext, bucket.clause, proof, limits, budget);
                        participantState = localNext;
                        // Fresh native replay makes proved local results usable as
                        // inputs; an in-place replay can be suppressed by an existing
                        // generated canonical twin. Foreign proofs never enter NEXT.
                        if (localChanged) continue;
                        unresolvedResiduals.addAll(snapshot.gaps);
                        for (Bucket bucket : participantState) for (Proof proof : bucket.proofs)
                            if (proof.usesX()) changed |= add(next, bucket.clause, proof, limits, budget);
                        break;
                    }
                }
            } catch (IllegalStateException | org.kanger.exception.RuntimeErrorException incomplete) {
                return new Result(Status.INCOMPLETE, round, incomplete.toString(), state, observations, null);
            }
            state = next;
            if (!changed) return new Result(unresolvedResiduals.isEmpty() ? Status.SATURATED : Status.INCOMPLETE,
                    round, unresolvedResiduals.isEmpty() ? "No new products or source support sets"
                            : unresolvedResiduals.toString(), state, observations, null);
        }
        return new Result(Status.INCOMPLETE, limits.rounds, "Round limit; qualification incomplete", state, observations, null);
    }
}
