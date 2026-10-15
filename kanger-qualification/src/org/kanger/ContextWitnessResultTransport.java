/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessUnaryChainVerifier.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Detached, proof-labelled primary-origin results. No runtime round gate or coverage claim. */
final class ContextWitnessResultTransport {
    static final class Origin {
        final Source source;
        final int binder;
        private Origin(Source source,int binder) { this.source = source; this.binder = binder; }
        public boolean equals(Object other) { return other instanceof Origin && source.equals(((Origin)other).source) && binder == ((Origin)other).binder; }
        public int hashCode() { return Objects.hash(source,binder); }
    }
    static final class Slot {
        final Origin origin;
        final SemanticTermSnapshot value;
        private Slot(Origin origin,SemanticTermSnapshot value) { this.origin = origin; this.value = value; }
    }
    static final class Atom {
        final String predicate;
        final boolean positive;
        final List<Slot> arguments;
        private Atom(String predicate,boolean positive,List<Slot> arguments) {
            this.predicate = predicate; this.positive = positive;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }
        private boolean same(Atom other,Runnable work) {
            return positive == other.positive && shape(other,work);
        }
        boolean opposite(Atom other,Runnable work) {
            return positive != other.positive && shape(other,work);
        }
        private boolean shape(Atom other,Runnable work) {
            work.run(); if (!predicate.equals(other.predicate) || arguments.size() != other.arguments.size()) return false;
            for (int i=0;i<arguments.size();++i) {
                work.run(); Slot a = arguments.get(i), b = other.arguments.get(i);
                if (a.origin != null || b.origin != null) { if (!Objects.equals(a.origin,b.origin)) return false; }
                else if (!a.value.semanticallyEquals(b.value)) return false;
            }
            return true;
        }
    }
    static final class Item {
        final Atom atom;
        final List<Support> proofs;
        private Item(Atom atom,List<Support> proofs) { this.atom = atom; this.proofs = Collections.unmodifiableList(new ArrayList<>(proofs)); }
    }
    static final class Packet {
        final List<Item> items;
        final UUID identity = UUID.randomUUID();
        final UUID basis;
        final Set<Source> producers;
        final boolean observedStep;
        final Cut emptyCut;
        final Cut coveredCut;
        private Packet(List<Item> items) { this(items,null,Collections.emptySet(),false); }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep) {
            this(items,basis,producers,observedStep,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut) {
            this(items,basis,producers,observedStep,emptyCut,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut,Cut coveredCut) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items)); this.basis = basis;
            this.producers = Collections.unmodifiableSet(new LinkedHashSet<>(producers)); this.observedStep = observedStep;
            this.emptyCut = emptyCut;
            this.coveredCut = coveredCut;
        }
    }
    static final class Result {
        final boolean exported;
        final String reason;
        final Packet packet;
        private Result(boolean exported,String reason,Packet packet) { this.exported = exported; this.reason = reason; this.packet = packet; }
    }
    static final class Replay {
        final UUID namespace = UUID.randomUUID();
        final Mind mind;
        final Map<Origin,Long> witnesses;
        /** Derived supports retain their original sources/depth; raw carrier is NOT primary proof evidence. */
        final List<Item> seeds;
        private Replay(Mind mind,Map<Origin,Long> witnesses,List<Item> seeds) {
            this.mind = mind; this.witnesses = Collections.unmodifiableMap(new LinkedHashMap<>(witnesses));
            this.seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
        }
    }
    static final class Cut {
        final UUID context;
        final long revision;
        final String view;
        final boolean localX;
        private Cut(Source source) { context = source.context; revision = source.revision; view = source.view; localX = source.localX; }
        static Cut of(Source source) { return new Cut(source); }
        public boolean equals(Object other) {
            if (!(other instanceof Cut)) return false; Cut cut = (Cut)other;
            return context.equals(cut.context) && revision == cut.revision && view.equals(cut.view) && localX == cut.localX;
        }
        public int hashCode() { return Objects.hash(context,revision,view,localX); }
    }
    enum FamilyStatus { MERGED, CONFLICT, INCOMPLETE }
    static final class Opposition {
        final Item left, right;
        private Opposition(Item left,Item right) { this.left = left; this.right = right; }
    }
    static final class FamilyResult {
        final FamilyStatus status;
        final String reason;
        final Packet packet;
        final boolean changed;
        final List<Opposition> conflicts;
        private FamilyResult(FamilyStatus status,String reason,Packet packet,boolean changed,List<Opposition> conflicts) {
            this.status = status; this.reason = reason; this.packet = packet; this.changed = changed;
            this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
        }
    }
    /** Immutable expected family and exact common input identity. No physical parallelism. */
    static final class Family {
        final Packet input;
        final List<Cut> expected;
        private final Cut parent;
        private Family(Packet input,List<Cut> expected,Cut parent) {
            this.input = input; this.expected = Collections.unmodifiableList(new ArrayList<>(expected)); this.parent = parent;
        }
        FamilyResult finish(Map<Cut,Packet> outputs,int maximumWork) {
            Runnable work = budget(maximumWork);
            try {
                if (outputs.size() != expected.size()) throw new IllegalStateException("Incomplete witness family");
                Map<Cut,Packet> snapshot = new LinkedHashMap<>();
                for (Map.Entry<Cut,Packet> entry : outputs.entrySet()) { work.run(); snapshot.put(entry.getKey(),entry.getValue()); }
                if (!snapshot.keySet().equals(new HashSet<>(expected))) throw new IllegalStateException("Unexpected witness family cut");
                for (Cut cut : expected) {
                    work.run(); Packet output = snapshot.get(cut);
                    if (output == null || (!output.observedStep && output.emptyCut == null) || !input.identity.equals(output.basis))
                        throw new IllegalStateException("Witness branch has a different input barrier");
                    if (output.emptyCut != null && !cut.equals(output.emptyCut))
                        throw new IllegalStateException("Empty witness branch has a different target cut");
                    if (output.coveredCut != null && !cut.equals(output.coveredCut))
                        throw new IllegalStateException("Covered witness branch has a different target cut");
                    if (!parent.equals(parentX(output.items,work))) throw new IllegalStateException("Witness branch changed parent X");
                    boolean matched = false;
                    for (Source producer : output.producers) {
                        work.run(); Cut actual = Cut.of(producer);
                        if (producer.localX) {
                            if (!parent.equals(actual)) throw new IllegalStateException("Witness branch changed X producer cut");
                            if (cut.localX) matched = true;
                        } else {
                            if (cut.localX || !cut.equals(actual)) throw new IllegalStateException("Witness branch does not match expected foreign cut");
                            matched = true;
                        }
                    }
                    if (!matched) throw new IllegalStateException("Witness branch has no expected producer cut");
                }
                List<Item> merged = new ArrayList<>(input.items);
                for (Cut cut : expected) for (Item item : snapshot.get(cut).items) for (Support proof : item.proofs) {
                    work.run(); add(merged,item.atom,proof,work);
                }
                List<Opposition> conflicts = new ArrayList<>();
                for (int i=0;i<merged.size();++i) for (int j=i+1;j<merged.size();++j) {
                    work.run(); if (merged.get(i).atom.opposite(merged.get(j).atom,work)) conflicts.add(new Opposition(merged.get(i),merged.get(j)));
                }
                if (!conflicts.isEmpty()) return new FamilyResult(FamilyStatus.CONFLICT,"Proved opposite literals at one canonical witness",null,false,conflicts);
                boolean changed = !sameItems(input.items,merged,work);
                return new FamilyResult(FamilyStatus.MERGED,"All expected witness branches merged; coverage/admission still pending",
                        new Packet(merged,input.identity,Collections.emptySet(),false),changed,Collections.emptyList());
            } catch (IllegalStateException failure) {
                return new FamilyResult(FamilyStatus.INCOMPLETE,failure.getMessage(),null,false,Collections.emptyList());
            }
        }
    }
    static Family family(Packet input,List<Cut> expected,int maximumWork) {
        Runnable work = budget(maximumWork); Cut parent = parentX(input.items,work);
        if (expected.isEmpty() || expected.size() > 16) throw new IllegalArgumentException("Bounded nonempty witness family required");
        Set<UUID> contexts = new HashSet<>();
        for (Cut cut : expected) {
            work.run();
            if (cut == null || !contexts.add(cut.context) || cut.localX && !parent.equals(cut)
                    || !cut.localX && parent.context.equals(cut.context))
                throw new IllegalArgumentException("One exact cut per family context required");
        }
        return new Family(input,expected,parent);
    }
    private static Cut parentX(List<Item> items,Runnable work) {
        Cut parent = null;
        for (Item item : items) for (Support proof : item.proofs) {
            boolean uses = false;
            for (Source source : proof.sources) {
                work.run(); if (!source.localX) continue; uses = true; Cut cut = Cut.of(source);
                if (parent == null) parent = cut;
                else if (!parent.equals(cut)) throw new IllegalStateException("Incompatible family X cut");
            }
            if (!uses) throw new IllegalStateException("Foreign-only family proof");
        }
        if (parent == null) throw new IllegalStateException("Missing family X cut");
        return parent;
    }
    /** Equality of the logical/proof pool, ignoring allocation IDs and list order. Not a coverage verdict. */
    static boolean sameContents(Packet left,Packet right,int maximumWork) { return sameItems(left.items,right.items,budget(maximumWork)); }
    private static boolean sameItems(List<Item> left,List<Item> right,Runnable work) {
        if (left.size() != right.size()) return false;
        for (Item item : left) {
            boolean found = false;
            for (Item other : right) {
                work.run(); if (!item.atom.same(other.atom,work) || item.proofs.size() != other.proofs.size()) continue;
                boolean equal = true;
                for (Support proof : item.proofs) {
                    boolean present = false;
                    for (Support candidate : other.proofs) { work.run(); if (proof.equals(candidate)) { present = true; break; } }
                    if (!present) { equal = false; break; }
                }
                if (equal) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }
    private static Runnable budget(int maximumWork) {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded transport required");
        int[] remaining = {maximumWork};
        return () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness transport work limit"); };
    }
    private static boolean usesX(Support support) { return support.sources.stream().anyMatch(s -> s.localX); }
    private static void add(List<Item> items,Atom atom,Support proof,Runnable work) {
        for (int i=0;i<items.size();++i) {
            Item old = items.get(i); if (!old.atom.same(atom,work)) continue;
            List<Support> alternatives = new ArrayList<>(old.proofs);
            for (int j=0;j<alternatives.size();++j) {
                work.run(); Support prior = alternatives.get(j);
                if (prior.sources.equals(proof.sources)) {
                    if (proof.depth < prior.depth) { alternatives.set(j,proof); items.set(i,new Item(old.atom,alternatives)); }
                    return;
                }
            }
            if (alternatives.size() == 256) throw new IllegalStateException("Witness transport support limit");
            alternatives.add(proof); items.set(i,new Item(old.atom,alternatives)); return;
        }
        if (items.size() == 64) throw new IllegalStateException("Witness transport atom limit");
        items.add(new Item(atom,Collections.singletonList(proof)));
    }
    /** Re-verifies the owning journal; callers cannot supply an alleged proof result. */
    static Result export(Session session,int maximumDepth,int maximumWork) {
        Runnable work = budget(maximumWork);
        ContextWitnessUnaryChainVerifier.Result verified = ContextWitnessUnaryChainVerifier.inspectSharedOriginJoins(session,maximumDepth,maximumWork);
        if (!verified.reconstructed) return new Result(false,verified.reason,null);
        try {
            Map<Root,Origin> origins = new HashMap<>(); Set<Source> sources = new HashSet<>();
            List<Item> items = new ArrayList<>();
            for (PrimaryEvidence primary : session.primary) {
                work.run(); if (!sources.add(primary.bundle.source)) throw new IllegalStateException("Duplicate primary occurrence");
                for (int i=0;i<primary.witnessTerms.size();++i) {
                    work.run(); Root root = new Root(primary.witnessTerms.get(i),primary.nativeRule);
                    if (origins.put(root,new Origin(primary.bundle.source,i)) != null) throw new IllegalStateException("Ambiguous primary origin");
                }
                if (!primary.bundle.source.localX) continue;
                for (ContextWitnessBundle.Atom atom : primary.bundle.atoms) {
                    work.run(); List<Slot> arguments = new ArrayList<>();
                    for (ContextWitnessBundle.Slot slot : atom.arguments) {
                        work.run(); arguments.add(new Slot(slot.witness < 0 ? null : new Origin(primary.bundle.source,slot.witness),slot.value));
                    }
                    add(items,new Atom(atom.predicate,atom.positive,arguments),primary.support,work);
                }
            }
            return pack(items,origins,session.replay,verified.routes,work);
        } catch (IllegalStateException failure) { return new Result(false,failure.getMessage(),null); }
    }
    /** Re-verifies a fresh pass using its sealed imported ledger, not fabricated primary labels. */
    static Result exportNext(ContextWitnessSeedReplay round,int maximumDepth,int maximumWork) {
        Runnable work = budget(maximumWork);
        ContextWitnessUnaryChainVerifier.Result verified = round.inspect(maximumDepth,maximumWork);
        if (!verified.reconstructed) return new Result(false,verified.reason,null);
        try {
            Map<Root,Origin> origins = new HashMap<>();
            for (Map.Entry<Origin,Root> entry : round.bindings.entrySet()) {
                work.run(); if (origins.put(entry.getValue(),entry.getKey()) != null) throw new IllegalStateException("Ambiguous carried origin");
            }
            Result packed = pack(new ArrayList<>(round.seeds),origins,round.namespace,verified.routes,work);
            if (!packed.exported) return packed;
            return new Result(true,packed.reason,new Packet(packed.packet.items,round.inputIdentity,round.producerSources(work),true));
        } catch (IllegalStateException failure) { return new Result(false,failure.getMessage(),null); }
    }
    /** Only the owning structural verifier can create an empty STEP, never an absent native event. */
    static Result exportDisjointEmpty(Packet input,Cut cut,List<ContextProductionRoundsReference.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        ContextWitnessDisjointCoverage.Result checked = ContextWitnessDisjointCoverage.inspect(
                input,cut,declarations,maximumDepth,maximumWork,maximumEvents);
        if (!checked.covered) return new Result(false,checked.reason,null);
        return new Result(true,checked.reason,new Packet(input.items,input.identity,checked.sources,false,cut));
    }
    static Result exportDormantEmpty(Packet input,Cut cut,List<ContextProductionRoundsReference.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        ContextWitnessDormantCoverage.Result checked = ContextWitnessDormantCoverage.inspect(
                input,cut,declarations,maximumDepth,maximumWork,maximumEvents);
        if (!checked.covered) return new Result(false,checked.reason,null);
        return new Result(true,checked.reason,new Packet(input.items,input.identity,checked.sources,false,cut));
    }
    /** Sound observed proofs plus exhaustive positive Horn proof-pool closure, bound to the owning inputs. */
    static Result exportCoveredNext(ContextWitnessSeedReplay round,Cut cut,List<ContextProductionRoundsReference.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        Result observed = exportNext(round,maximumDepth,maximumWork);
        if (!observed.exported) return observed;
        Runnable work = budget(maximumWork);
        try {
            List<Input> definitions = ContextWitnessDeclarationCut.capture(round.input,cut,declarations,work,maximumWork,maximumEvents);
            ContextWitnessHornClosure.sameDeclarations(definitions,round.producerInputs(work),work);
            int obligations = ContextWitnessHornClosure.verify(observed.packet,definitions,maximumDepth,work);
            return new Result(true,"Observed positive Horn proof pool closed; obligations="+obligations,
                    new Packet(observed.packet.items,round.inputIdentity,observed.packet.producers,true,null,cut));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
    private static Result pack(List<Item> items,Map<Root,Origin> origins,UUID namespace,List<Route> routes,Runnable work) {
        boolean product = false;
        for (Route route : routes) {
            work.run(); if (route.proof.depth == 0 || !usesX(route.proof)) continue;
            if (!route.replay.equals(namespace) || !route.physical.root.equals(route.origin))
                throw new IllegalStateException("Non-primary carrier cannot be transported");
            Origin origin = origins.get(route.origin);
            if (origin == null) throw new IllegalStateException("Unbound transport origin");
            List<Slot> arguments = new ArrayList<>();
            for (Argument argument : route.physical.arguments) {
                work.run(); arguments.add(new Slot(argument.root == null ? null : origin,argument.value));
            }
            add(items,new Atom(route.physical.predicate,route.physical.positive,arguments),route.proof,work); product = true;
        }
        if (!product) return new Result(false,"No X-dependent witness products",null);
        Set<Origin> rank = new HashSet<>();
        for (Item item : items) for (Slot slot : item.atom.arguments) { work.run(); if (slot.origin != null) rank.add(slot.origin); }
        if (rank.size() > 8) throw new IllegalStateException("Witness transport rank limit");
        return new Result(true,"Detached X seeds and verified X-dependent products",new Packet(items));
    }
    /** Fresh private logical carrier plus an immutable ledger, not authenticated primary inputs. */
    static Replay restore(Packet packet,int maximumWork) throws Exception {
        Runnable work = budget(maximumWork); User user = new User(); Mind target = new Mind(user); user.setCurrentMind(target);
        Map<Origin,Integer> binders = new LinkedHashMap<>();
        for (Item item : packet.items) for (Slot slot : item.atom.arguments) {
            work.run(); if (slot.origin != null && !binders.containsKey(slot.origin)) binders.put(slot.origin,binders.size());
        }
        StringBuilder text = new StringBuilder("!");
        for (int i=0;i<binders.size();++i) text.append("$ctxcarry").append(i).append(' ');
        text.append('('); Queue<ITerm> values = new LinkedList<>();
        for (int i=0;i<packet.items.size();++i) {
            work.run(); if (i != 0) text.append(" && "); Atom atom = packet.items.get(i).atom;
            if (!atom.positive) text.append('~'); text.append(atom.predicate).append('(');
            for (int j=0;j<atom.arguments.size();++j) {
                work.run(); if (j != 0) text.append(','); Slot slot = atom.arguments.get(j);
                if (slot.origin != null) text.append("ctxcarry").append(binders.get(slot.origin));
                else { text.append('?'); values.add(target.getTerms().projectSemantic(slot.value.materialize())); }
            }
            text.append(')');
        }
        text.append(");"); work.run();
        Object parsed = target.compileLine(text.toString(),false,values);
        if (!(parsed instanceof Rule) || !values.isEmpty()) throw new IllegalStateException("Transport carrier parse mismatch");
        Rule rule = (Rule)parsed;
        if (rule.isQuery() || rule.isGenerated() || rule.getTree().size() != packet.items.size())
            throw new IllegalStateException("Transport carrier shape mismatch");
        Map<Origin,Long> witnesses = new LinkedHashMap<>(); Map<Long,Origin> inverse = new HashMap<>();
        for (int i=0;i<packet.items.size();++i) {
            work.run(); List<Domain> branch = rule.getTree().get(i); Atom atom = packet.items.get(i).atom;
            if (branch.size() != 1) throw new IllegalStateException("Non-conjunctive transport carrier");
            Domain actual = branch.get(0);
            if (!actual.getPredicate(target).getName(target).equals(atom.predicate) || actual.isAntc() != atom.positive
                    || actual.getRange() != atom.arguments.size()) throw new IllegalStateException("Transport atom mismatch");
            for (int j=0;j<atom.arguments.size();++j) {
                work.run(); Slot slot = atom.arguments.get(j); IArgument argument = actual.get(j); ITerm value = argument.getValue(target);
                if (argument.getType() != ArgumentType.TERM) throw new IllegalStateException("Non-primary transport slot");
                if (slot.origin == null) {
                    if (!SemanticTermSnapshot.isOrdinaryValue(value) || !slot.value.semanticallyEquals(SemanticTermSnapshot.capture(value)))
                        throw new IllegalStateException("Transport fixed value mismatch");
                } else {
                    if (!(value instanceof Term) || !value.isCVariable() || ((Term)value).isDomini()
                            || ((Term)value).getParent(target) != null || ((Term)value).getRuleId() != rule.getId())
                        throw new IllegalStateException("Transport witness mismatch");
                    Long prior = witnesses.putIfAbsent(slot.origin,value.getId()); Origin old = inverse.putIfAbsent(value.getId(),slot.origin);
                    if (prior != null && prior.longValue() != value.getId() || old != null && !old.equals(slot.origin))
                        throw new IllegalStateException("Transport witness sharing mismatch");
                }
            }
        }
        if (witnesses.size() != binders.size()) throw new IllegalStateException("Missing transport witness");
        return new Replay(target,witnesses,packet.items);
    }
}
