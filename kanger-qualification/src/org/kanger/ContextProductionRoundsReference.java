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
        public final List<Proof> supports;
        private Product(Bucket b) {
            fact = b.fact; supports = Collections.unmodifiableList(new ArrayList<>(b.proofs));
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
        final Fact fact;
        final List<Proof> proofs = new ArrayList<>();
        Bucket(Fact fact) { this.fact = fact; }
    }
    private static final class Budget {
        int remaining;
        Budget(int work) { remaining = work; }
        void take() { if (--remaining < 0) throw new IllegalStateException("Proof work limit; qualification incomplete"); }
    }
    private static Bucket find(List<Bucket> pool, Fact fact) {
        for (Bucket bucket : pool) if (bucket.fact.equivalent(fact)) return bucket;
        return null;
    }
    private static boolean add(List<Bucket> pool, Fact fact, Proof proof, Limits limits, Budget budget) {
        budget.take();
        Bucket bucket = find(pool, fact);
        if (bucket != null) for (int i = 0; i < bucket.proofs.size(); ++i) {
            Proof old = bucket.proofs.get(i);
            if (old.sources.equals(proof.sources)) {
                if (proof.depth < old.depth) { bucket.proofs.set(i, proof); return true; }
                return false;
            }
        }
        if (proof.depth > limits.depth) throw new IllegalStateException("Transported derivation depth limit; qualification incomplete");
        if (bucket == null) { bucket = new Bucket(fact); pool.add(bucket); }
        bucket.proofs.add(proof); return true;
    }
    private static List<Bucket> copy(List<Bucket> state) {
        List<Bucket> result = new ArrayList<>();
        for (Bucket bucket : state) { Bucket clone = new Bucket(bucket.fact); clone.proofs.addAll(bucket.proofs); result.add(clone); }
        return result;
    }
    private static boolean derive(Application application, Source producer, int slot, Set<Source> sources, int depth,
                                  List<List<Proof>> premises, List<Bucket> pool, Limits limits, Budget budget) {
        budget.take();
        if (slot == premises.size()) return add(pool, application.conclusion, new Proof(sources, depth + 1), limits, budget);
        boolean changed = false;
        for (Proof proof : premises.get(slot)) {
            Set<Source> joined = new LinkedHashSet<>(sources); joined.addAll(proof.sources);
            changed |= derive(application, producer, slot + 1, joined, Math.max(depth, proof.depth), premises, pool, limits, budget);
        }
        return changed;
    }
    private static List<Bucket> closure(Snapshot snapshot, List<Bucket> state, Limits limits, Budget budget) {
        List<Bucket> pool = copy(state);
        for (Input input : snapshot.inputs) if (input.fact != null)
            add(pool, input.fact, new Proof(Collections.singleton(input.source), 0), limits, budget);
        boolean changed;
        do {
            changed = false;
            for (Application application : snapshot.applications) {
                List<List<Proof>> premises = new ArrayList<>();
                for (Fact fact : application.premises) {
                    Bucket bucket = find(pool, fact);
                    if (bucket == null) { premises = null; break; }
                    premises.add(new ArrayList<>(bucket.proofs));
                }
                if (premises == null) continue;
                for (Source producer : application.producers)
                    changed |= derive(application, producer, 0, new LinkedHashSet<>(Collections.singleton(producer)), 0,
                            premises, pool, limits, budget);
            }
        } while (changed);
        for (Application application : snapshot.applications) for (Fact premise : application.premises)
            if (find(pool, premise) == null)
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
                    // Every participant receives STATE, never another participant's NEXT.
                    Mind work = memory(limits);
                    Snapshot snapshot;
                    try (ContextProductionCapture capture = ContextProductionCapture.begin(work, limits.events);
                         TechnicalMindTransaction transaction = TechnicalMindTransaction.begin(work)) {
                        Mind participant = transaction.mind();
                        participant.setQueryPass(org.kanger.enums.QueryPass.ACCEPT);
                        for (Declaration declaration : local) capture.replay(participant, declaration.source, declaration.statement);
                        for (Bucket bucket : state) participant.compileLine(bucket.fact.assertion(), false, bucket.fact.project(participant));
                        for (Declaration declaration : target) capture.replay(participant, declaration.source, declaration.statement);
                        participant.link(null, false);
                        Analyzer analyzer = new Analyzer(participant);
                        boolean collision = analyzer.analyze(null, false);
                        ContextQualification qualification = new ContextQualification(!collision, analyzer.getCollisionWitnesses());
                        snapshot = capture.snapshot(); observations.add(snapshot);
                        if (!qualification.isValid()) return new Result(Status.COLLISION, round, "Native collision", state, observations, qualification);
                    }
                    for (String gap : snapshot.gaps) {
                        if (gap.startsWith("compound-residual:")) unresolvedResiduals.add(gap);
                        else return new Result(Status.INCOMPLETE, round, gap, state, observations, null);
                    }
                    for (Bucket bucket : closure(snapshot, state, limits, budget)) for (Proof proof : bucket.proofs)
                        if (proof.usesX()) changed |= add(next, bucket.fact, proof, limits, budget);
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
