/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextWitnessBundle.PrimaryEvidence;
import org.kanger.ContextWitnessBundle.Session;
import org.kanger.ContextWitnessMaterializationVerifier.Relation;
import org.kanger.ContextWitnessProjection.Literal;
import org.kanger.ContextWitnessProjection.Node;

/** Observed single-premise, single-witness proof closure; no global root aliases. */
final class ContextWitnessUnaryChainVerifier {
    static final class Root {
        final long term, owner;
        Root(long term,long owner) { this.term = term; this.owner = owner; }
        public boolean equals(Object value) { return value instanceof Root && term == ((Root)value).term && owner == ((Root)value).owner; }
        public int hashCode() { return Objects.hash(term,owner); }
    }
    static final class Argument {
        final Root root;
        final SemanticTermSnapshot value;
        Argument(Root root,SemanticTermSnapshot value) { this.root = root; this.value = value; }
    }
    static final class Pattern {
        final String predicate;
        final boolean positive;
        final List<Argument> arguments;
        final Root root;
        Pattern(String predicate,boolean positive,List<Argument> arguments) {
            this.predicate = predicate; this.positive = positive;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
            Root root = null;
            for (Argument argument : arguments) if (argument.root != null) {
                if (root != null && !root.equals(argument.root)) throw new IllegalStateException("multiple-witness-chain-literal");
                root = argument.root;
            }
            if (root == null) throw new IllegalStateException("witness-free-chain-literal");
            this.root = root;
        }
        boolean equivalent(Pattern other,Runnable work) {
            work.run();
            if (!predicate.equals(other.predicate) || positive != other.positive || arguments.size() != other.arguments.size()) return false;
            for (int i=0;i<arguments.size();++i) {
                work.run(); Argument a = arguments.get(i), b = other.arguments.get(i);
                if (a.root != null || b.root != null) { if (!Objects.equals(a.root,b.root)) return false; }
                else if (!a.value.semanticallyEquals(b.value)) return false;
            }
            return true;
        }
    }
    static final class Route {
        final UUID replay;
        final Pattern physical;
        final Root origin;
        final Support proof;
        final Route prior;
        final ContextWitnessProjection application;
        final Support producer;
        final Relation crossing;
        Route(UUID replay,Pattern physical,Root origin,Support proof,Route prior,
                ContextWitnessProjection application,Support producer,Relation crossing) {
            this.replay = replay; this.physical = physical; this.origin = origin; this.proof = proof;
            this.prior = prior; this.application = application; this.producer = producer; this.crossing = crossing;
        }
    }
    static final class Result {
        final boolean reconstructed;
        final String reason;
        final List<Route> routes;
        Result(boolean reconstructed,String reason,List<Route> routes) {
            this.reconstructed = reconstructed; this.reason = reason;
            this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        }
    }
    private static Result incomplete(String reason) { return new Result(false,reason,Collections.emptyList()); }
    private static Pattern pattern(Literal literal,Runnable work) {
        List<Argument> arguments = new ArrayList<>();
        for (ContextWitnessProjection.Slot slot : literal.arguments) {
            work.run(); Root root = null;
            for (Node node : slot.lineage) { work.run(); if (node.domini) throw new IllegalStateException("dominating-chain-witness"); }
            if (!slot.lineage.isEmpty()) {
                Node node = slot.lineage.get(slot.lineage.size()-1); root = new Root(node.term,node.ownerRule);
            }
            arguments.add(new Argument(root,slot.value));
        }
        return new Pattern(literal.predicate,literal.positive,arguments);
    }
    private static boolean add(List<Route> pool,Route route,int maximumDepth,Runnable work) {
        for (int i=0;i<pool.size();++i) {
            work.run(); Route old = pool.get(i);
            if (old.origin.equals(route.origin) && old.proof.sources.equals(route.proof.sources) && old.physical.equivalent(route.physical,work)) {
                if (old.proof.depth <= route.proof.depth) return false;
                if (route.proof.depth > maximumDepth) throw new IllegalStateException("Witness chain depth limit");
                pool.set(i,route); return true;
            }
        }
        if (route.proof.depth > maximumDepth) throw new IllegalStateException("Witness chain depth limit");
        pool.add(route); return true;
    }
    static Result inspect(Session session,int maximumDepth,int maximumWork) {
        if (maximumDepth < 0 || maximumWork < 1) throw new IllegalArgumentException("Bounded witness chain verification required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness chain work limit"); };
        try {
            Snapshot snapshot = session.capture.snapshot();
            ContextWitnessPrimaryStepVerifier.Scope scope = ContextWitnessPrimaryStepVerifier.scope(session,snapshot,work);
            ContextWitnessMaterializationVerifier.Result correspondence = ContextWitnessMaterializationVerifier.verify(session.replay,snapshot,work);
            if (!correspondence.verified) return incomplete(correspondence.reason);
            Map<ContextWitnessProjection,Pattern> premises = new IdentityHashMap<>(), conclusions = new IdentityHashMap<>();
            Map<ContextWitnessProjection,List<Route>> applications = new IdentityHashMap<>();
            for (ContextWitnessProjection observation : snapshot.witnessProjections) {
                work.run(); if (observation.premises.size() != 1) return incomplete("outside-single-premise-witness-chain");
                ContextWitnessPrimaryStepVerifier.application(scope,observation,work);
                Pattern premise = pattern(observation.premises.get(0),work), conclusion = pattern(observation.conclusion,work);
                if (!premise.root.equals(conclusion.root)) return incomplete("non-preserving-witness-chain-step");
                premises.put(observation,premise); conclusions.put(observation,conclusion); applications.put(observation,new ArrayList<>());
            }
            if (applications.isEmpty()) return incomplete("no-witness-chain-applications");
            List<Route> pool = new ArrayList<>();
            for (PrimaryEvidence evidence : session.primary) for (ContextWitnessBundle.Atom atom : evidence.bundle.atoms) {
                work.run(); List<Argument> arguments = new ArrayList<>(); boolean witness = false;
                for (ContextWitnessBundle.Slot slot : atom.arguments) {
                    work.run(); Root root = slot.witness < 0 ? null : new Root(evidence.witnessTerms.get(slot.witness),evidence.nativeRule);
                    arguments.add(new Argument(root,slot.value)); witness |= root != null;
                }
                if (!witness) continue;
                Pattern key = new Pattern(atom.predicate,atom.positive,arguments);
                add(pool,new Route(session.replay,key,key.root,evidence.support,null,null,null,null),maximumDepth,work);
            }
            boolean changed;
            do {
                work.run(); changed = false;
                for (ContextWitnessProjection observation : snapshot.witnessProjections) {
                    for (Route donor : new ArrayList<>(pool)) if (donor.physical.equivalent(premises.get(observation),work))
                        for (Support producer : observation.producerSupports) {
                            work.run(); int depth = Math.max(donor.proof.depth,producer.depth);
                            if (depth == Integer.MAX_VALUE) return incomplete("witness-chain-depth-overflow");
                            Set<Source> sources = new LinkedHashSet<>(producer.sources); sources.addAll(donor.proof.sources);
                            Route route = new Route(session.replay,conclusions.get(observation),donor.origin,new Support(sources,depth+1),donor,observation,producer,null);
                            changed |= add(applications.get(observation),route,maximumDepth,work);
                            changed |= add(pool,route,maximumDepth,work);
                        }
                }
                for (Relation relation : correspondence.relations) {
                    Pattern stored = pattern(relation.occurrence.stored,work);
                    for (ContextWitnessProjection application : relation.applications) for (Route donor : new ArrayList<>(applications.get(application))) {
                        work.run(); Route route = new Route(session.replay,stored,donor.origin,donor.proof,donor,null,null,relation);
                        changed |= add(pool,route,maximumDepth,work);
                    }
                }
            } while (changed);
            for (List<Route> routes : applications.values()) { work.run(); if (routes.isEmpty()) return incomplete("unreconstructed-witness-chain-premise"); }
            return new Result(true,"Observed single-premise witness routes reconstructed",pool);
        } catch (IllegalStateException failure) { return incomplete(failure.getMessage()); }
    }
}
