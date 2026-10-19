/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundProductionRounds.Collision;
import org.kanger.enums.QueryPass;

/** Sealed transport of observed ordinary signed clauses. No saturation/admission authority. */
public final class ContextOrdinaryResidualRounds {
    /** Immutable per-run capacity policy; never inferred from a partial result. */
    public static final class LedgerLimits {
        public static final LedgerLimits DEFAULT = new LedgerLimits(64,256);
        public final int clauses,proofs;
        public LedgerLimits(int clauses,int proofs) {
            if(clauses<1 || proofs<1) throw new IllegalArgumentException("Positive residual ledger limits required");
            this.clauses=clauses; this.proofs=proofs;
        }
    }
    public static final class Product {
        public final Clause clause;
        public final Support proof;
        private Product(Clause clause,Support proof) { this.clause = clause; this.proof = proof; }
    }
    public static final class Frontier {
        public final UUID identity = UUID.randomUUID(),basis;
        public final Snapshot x;
        public final List<Snapshot> cuts;
        public final List<Product> products;
        public final LedgerLimits ledgerLimits;
        private Frontier(Snapshot x,List<Snapshot> cuts,List<Product> products,UUID basis,LedgerLimits ledgerLimits) {
            this.ledgerLimits=ledgerLimits;
            this.x = x; this.cuts = Collections.unmodifiableList(new ArrayList<>(cuts));
            this.products = Collections.unmodifiableList(new ArrayList<>(products)); this.basis = basis;
        }
    }
    public static final class Receipt {
        public final boolean verified;
        public final String reason;
        public final List<Product> products;
        public final List<Collision> collisions;
        final Frontier input;
        final Snapshot target;
        final List<Product> owners,localProofs;
        private Receipt(boolean verified,String reason,Frontier input,Snapshot target,List<Product> products,List<Collision> collisions) {
            this(verified,reason,input,target,products,collisions,Collections.emptyList(),Collections.emptyList());
        }
        private Receipt(boolean verified,String reason,Frontier input,Snapshot target,List<Product> products,List<Collision> collisions,List<Product> owners,List<Product> localProofs) {
            this.owners = Collections.unmodifiableList(new ArrayList<>(owners));
            this.localProofs = Collections.unmodifiableList(new ArrayList<>(localProofs));
            this.verified = verified; this.reason = reason; this.input = input; this.target = target;
            this.products = Collections.unmodifiableList(new ArrayList<>(products));
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
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
    public static Frontier start(Snapshot x,List<Snapshot> foreign,int maximumWork) {
        return start(x,foreign,maximumWork,LedgerLimits.DEFAULT);
    }
    public static Frontier start(Snapshot x,List<Snapshot> foreign,int maximumWork,LedgerLimits ledgerLimits) {
        if(ledgerLimits==null) throw new IllegalArgumentException("Explicit residual ledger limits required");
        ContextGroundProductionRounds.Frontier scope = ContextGroundProductionRounds.start(x,foreign,maximumWork);
        return new Frontier(x,scope.cuts,Collections.emptyList(),null,ledgerLimits);
    }
    private static boolean add(List<Product> pool,Clause clause,Support proof,int maximumDepth,Runnable work,LedgerLimits ledgerLimits) {
        work.run();
        if (clause.literals.size() == 1 && !clause.variables.isEmpty()) throw new IllegalStateException("Universal residual unit needs separate support");
        boolean sameClause = false; int distinct = 0;
        for (int i=0;i<pool.size();++i) {
            work.run(); Product old = pool.get(i);
            if (old.clause.equivalent(clause)) {
                sameClause = true;
                if (old.proof.sources.equals(proof.sources)) {
                    if (old.proof.depth <= proof.depth) return false;
                    if (proof.depth > maximumDepth) throw new IllegalStateException("Residual cumulative depth limit");
                    pool.set(i,new Product(clause,proof)); return true;
                }
            }
            boolean first = true;
            for (int j=0;j<i;++j) { work.run(); if (pool.get(j).clause.equivalent(old.clause)) { first = false; break; } }
            if (first) ++distinct;
        }
        if (proof.depth > maximumDepth) throw new IllegalStateException("Residual cumulative depth limit");
        if (pool.size() >= ledgerLimits.proofs) throw new IllegalStateException("Residual proof limit: proofs="+pool.size()+", clauses="+distinct);
        if (!sameClause && distinct >= ledgerLimits.clauses) throw new IllegalStateException("Residual clause limit: clauses="+distinct+", proofs="+pool.size());
        pool.add(new Product(clause,proof)); return true;
    }
    private static List<Collision> collisions(List<Product> pool,Runnable work) {
        List<Collision> result = new ArrayList<>();
        for (int i=0;i<pool.size();++i) for (int j=i+1;j<pool.size();++j) {
            work.run(); Product a = pool.get(i),b = pool.get(j);
            if (a.clause.literals.size() != 1 || b.clause.literals.size() != 1
                    || a.proof.sources.stream().noneMatch(s -> s.localX) && b.proof.sources.stream().noneMatch(s -> s.localX)) continue;
            Fact left = a.clause.literals.get(0).fact(),right = b.clause.literals.get(0).fact();
            if (left == null || right == null) continue;
            Collision collision = ContextGroundProductionRounds.opposition(left,a.proof,right,b.proof,work);
            if (collision != null) result.add(collision);
        }
        return result;
    }
    private static boolean derive(Residual event,List<List<Support>> donors,int index,Set<Source> sources,int depth,List<Product> pool,int maximumDepth,Runnable work,LedgerLimits ledgerLimits) {
        work.run();
        if (index == donors.size()) {
            if (depth == Integer.MAX_VALUE) throw new IllegalStateException("Residual depth overflow");
            return add(pool,event.conclusion,new Support(sources,depth+1),maximumDepth,work,ledgerLimits);
        }
        boolean changed = false;
        for (Support donor : donors.get(index)) {
            Set<Source> next = new LinkedHashSet<>(sources); next.addAll(donor.sources);
            changed |= derive(event,donors,index+1,next,Math.max(depth,donor.depth),pool,maximumDepth,work,ledgerLimits);
        }
        return changed;
    }
    private static List<List<Support>> donors(Residual event,List<Product> pool,Runnable work) {
        List<List<Support>> result = new ArrayList<>();
        for (Fact fact : event.premises) {
            List<Support> matches = new ArrayList<>(); Clause unit = new Clause(Collections.singletonList(fact));
            for (Product product : pool) { work.run(); if (product.clause.equivalent(unit)) matches.add(product.proof); }
            if (matches.isEmpty()) return null; result.add(matches);
        }
        return result;
    }
    private static void authenticate(Residual event,List<Product> owners,Runnable work) {
        if (event.producerSupports.isEmpty()) throw new IllegalStateException("Residual has no owner proof");
        for (Support producer : event.producerSupports) {
            boolean found = false;
            for (Product owner : owners) {
                work.run(); if (owner.proof.equals(producer) && ContextOrdinaryResidualVerifier.instance(owner.clause,event,work)) { found = true; break; }
            }
            if (!found) throw new IllegalStateException("Residual whole owner/instance mismatch");
        }
    }
    public static Receipt inspect(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents) {
        if (input == null || maximumDepth < 0 || maximumEvents < 1) throw new IllegalArgumentException("Bounded residual input required");
        return observe(input,target,maximumDepth,ContextGroundProductionRounds.budget(maximumWork),new int[]{maximumEvents},false);
    }
    /** Both private passes share one work/event allowance and the same immutable family input. */
    public static Receipt inspectWithParameters(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents) {
        if (input == null || maximumDepth < 0 || maximumEvents < 1) throw new IllegalArgumentException("Bounded residual parameter input required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork); int[] events = {maximumEvents};
        try {
            Receipt ordinary = observe(input,target,maximumDepth,work,events,false);
            if (!ordinary.verified || !ordinary.collisions.isEmpty()) return ordinary;
            Receipt parameter = observe(input,target,maximumDepth,work,events,true);
            if (!parameter.verified || !parameter.collisions.isEmpty()) return parameter;
            return combine(input,target,Arrays.asList(ordinary,parameter),ordinary.owners,maximumDepth,work);
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    /** Proof-only views: the final receipt retains the complete original owner scope. */
    public static Receipt inspectWithProbes(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes) {
        if (input == null || maximumDepth < 0 || maximumEvents < 1 || maximumProbes < 1) throw new IllegalArgumentException("Bounded residual probes required");
        return probes(input,target,maximumDepth,ContextGroundProductionRounds.budget(maximumWork),new int[]{maximumEvents},maximumProbes);
    }
    private static Receipt probes(Frontier input,Snapshot target,int maximumDepth,Runnable work,int[] events,int maximumProbes) {
        try {
            Receipt ordinary = observe(input,target,maximumDepth,work,events,false);
            if (!ordinary.verified || !ordinary.collisions.isEmpty()) return ordinary;
            Receipt parameter = observe(input,target,maximumDepth,work,events,true);
            if (!parameter.verified || !parameter.collisions.isEmpty()) return parameter;
            Receipt basis = combine(input,target,Arrays.asList(ordinary,parameter),ordinary.owners,maximumDepth,work);
            if (!basis.collisions.isEmpty()) return basis;
            List<Receipt> evidence = new ArrayList<>(); evidence.add(basis); int probes = 0;
            // Plan from the sealed complete baseline, not from another probe's newly learned output.
            for (Product owner : basis.localProofs) if (owner.clause.literals.size() > 1)
                for (Product donor : basis.localProofs) {
                    work.run(); if (donor.clause.literals.size() != 1) continue;
                    Fact fact = donor.clause.literals.get(0).fact(); if (fact == null) continue;
                    boolean relevant = false;
                    for (Atom atom : owner.clause.literals) if (ContextOrdinaryResidualCoverage.match(atom,fact,new HashMap<>(),work)) { relevant = true; break; }
                    if (!relevant) continue;
                    if (++probes > maximumProbes) throw new IllegalStateException("Residual isolated probe limit");
                    Receipt observed = observe(input,target,maximumDepth,work,events,true,Arrays.asList(owner,donor));
                    if (!observed.verified || !observed.collisions.isEmpty()) return observed;
                    evidence.add(observed);
                }
            return combine(input,target,evidence,basis.owners,maximumDepth,work);
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    /** Adds checked ground resolutions from a sealed baseline; new conclusions wait for the next barrier. */
    public static Receipt inspectWithCompositions(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes) {
        if (input == null || maximumDepth < 0 || maximumEvents < 1 || maximumProbes < 1)
            throw new IllegalArgumentException("Bounded composition observation required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork); int[] events = {maximumEvents};
        try {
            Receipt basis = probes(input,target,maximumDepth,work,events,maximumProbes);
            if (!basis.verified || !basis.collisions.isEmpty()) return basis;
            return compositions(basis,maximumDepth,work,events);
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    /** One checked flat-universal compound sweep. Universal unit results refuse the entire receipt. */
    public static Receipt inspectWithUniversalCompositions(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes) {
        if (input == null || maximumDepth < 0 || maximumEvents < 1 || maximumProbes < 1)
            throw new IllegalArgumentException("Bounded universal composition observation required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork); int[] events = {maximumEvents};
        try {
            Receipt basis = probes(input,target,maximumDepth,work,events,maximumProbes);
            if (!basis.verified || !basis.collisions.isEmpty()) return basis;
            List<Product> pool = new ArrayList<>(basis.localProofs);
            List<Clause> clauses = new ArrayList<>();
            for (Product product : basis.localProofs) {
                work.run(); if (product.clause.literals.size() < 2) continue;
                boolean duplicate = false;
                for (Clause old : clauses) { work.run(); if (old.equivalent(product.clause)) { duplicate = true; break; } }
                if (!duplicate) clauses.add(product.clause);
            }
            List<ContextUniversalCompositionProof.Evidence> evidence = new ArrayList<>();
            // Only the immutable baseline supplies parents; newly proved conclusions wait.
            for (int i=0;i<clauses.size();++i) for (int j=i;j<clauses.size();++j) {
                Clause left = clauses.get(i),right = clauses.get(j);
                for (int a=0;a<left.literals.size();++a) for (int b=0;b<right.literals.size();++b) {
                    work.run(); Atom x = left.literals.get(a),y = right.literals.get(b);
                    if (x.positive == y.positive || !x.predicate.equals(y.predicate) || x.arguments.size() != y.arguments.size()) continue;
                    try { left.resolve(right,a,b,work); } catch (Clause.UnificationFailure noMatch) { continue; }
                    ContextUniversalCompositionProof.Evidence proof = ContextUniversalCompositionProof.prove(basis,left,a,right,b,maximumDepth,work);
                    if (!proof.proved) throw new IllegalStateException(proof.reason);
                    evidence.add(proof);
                    for (ContextUniversalCompositionProof.Proof step : proof.proofs)
                        add(pool,step.conclusion,step.support,maximumDepth,work,input.ledgerLimits);
                }
            }
            ContextUniversalCompositionCoverage.Audit audit = ContextUniversalCompositionCoverage.audit(basis,evidence,maximumDepth,work);
            if (!audit.covered) throw new IllegalStateException(audit.reason);
            List<Collision> opposed = collisions(pool,work);
            if (!opposed.isEmpty()) return new Receipt(true,"Checked flat universal composition opposition",input,target,Collections.emptyList(),opposed);
            List<Product> exported = new ArrayList<>();
            for (Product product : pool) {
                work.run(); if (product.proof.depth > 0 && product.proof.sources.stream().anyMatch(source -> source.localX)) exported.add(product);
            }
            return new Receipt(true,"Audited flat universal compound sweep; unit transport, closure/admission unknown",input,target,exported,Collections.emptyList(),basis.owners,pool);
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    private static Receipt compositions(Receipt basis,int maximumDepth,Runnable work,int[] events) {
        Frontier input = basis.input; Snapshot target = basis.target;
        try {
            if (events[0] < 1) throw new IllegalStateException("Composition shared observation event limit");
            User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
            mind.setQueryPass(QueryPass.ACCEPT); mind.setFloodControlLimit(maximumDepth);
            List<Product> pool = new ArrayList<>(basis.localProofs);
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,events[0])) {
                capture.observeCompositionCandidates(); List<Clause> restored = new ArrayList<>();
                for (Product product : basis.localProofs) {
                    boolean duplicate = false;
                    for (Clause clause : restored) { work.run(); if (clause.equivalent(product.clause)) { duplicate = true; break; } }
                    if (duplicate) continue;
                    List<Support> supports = new ArrayList<>();
                    for (Product alternative : basis.localProofs) {
                        work.run(); if (alternative.clause.equivalent(product.clause)) supports.add(alternative.proof);
                    }
                    capture.replayDerived(mind,product.clause,supports); restored.add(product.clause);
                }
                mind.link(null,false); ContextProductionCapture.Snapshot observed = capture.snapshot();
                events[0] -= observed.eventCount;
                if (!observed.gaps.isEmpty() || observed.inputs.size() != restored.size())
                    throw new IllegalStateException("Incomplete composition observation: "+observed.gaps);
                for (int i=0;i<restored.size();++i) {
                    work.run(); if (!restored.get(i).equivalent(observed.inputs.get(i).clause))
                        throw new IllegalStateException("Composition whole-clause replay mismatch");
                }
                for (CompositionCandidate candidate : observed.compositionCandidates) {
                    ContextGroundCompositionProof.Evidence proof = ContextGroundCompositionProof.prove(basis,candidate,maximumDepth,work);
                    if (!proof.proved) throw new IllegalStateException(proof.reason);
                    for (ContextGroundCompositionProof.Proof step : proof.proofs) add(pool,step.conclusion,step.support,maximumDepth,work,input.ledgerLimits);
                }
            }
            List<Collision> opposed = collisions(pool,work);
            if (!opposed.isEmpty()) return new Receipt(true,"Checked ground composition opposition",input,target,Collections.emptyList(),opposed);
            List<Product> exported = new ArrayList<>();
            for (Product product : pool) {
                work.run(); if (product.proof.depth > 0 && product.proof.sources.stream().anyMatch(source -> source.localX)) exported.add(product);
            }
            return new Receipt(true,"Observed reductions and checked ground compositions; coverage/admission unknown",input,target,exported,Collections.emptyList(),basis.owners,pool);
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    /** Bounded private cut closure. Only X-dependent products leave the cut at a family barrier. */
    public static Receipt inspectWithLocalCompositions(Frontier input,Snapshot target,int maximumDepth,int maximumWork,int maximumEvents,int maximumProbes,int maximumLocalRounds) {
        if (input == null || maximumDepth < 0 || maximumEvents < 1 || maximumProbes < 1 || maximumLocalRounds < 1)
            throw new IllegalArgumentException("Bounded local composition closure required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork); int[] events = {maximumEvents};
        try {
            Receipt current = probes(input,target,maximumDepth,work,events,maximumProbes);
            if (!current.verified || !current.collisions.isEmpty()) return current;
            for (Product product : current.localProofs) {
                work.run(); if (!product.clause.variables.isEmpty()) throw new IllegalStateException("Local composition closure requires ground clauses");
            }
            List<Product> originalOwners = current.owners;
            for (int round=0;round<maximumLocalRounds;++round) {
                Receipt composed = compositions(current,maximumDepth,work,events);
                if (!composed.verified || !composed.collisions.isEmpty()) return composed;
                // Replay actual proved carriers locally; retain every cause/depth and create no primary Source.
                Receipt reduced = observe(input,target,maximumDepth,work,events,false,composed.localProofs);
                if (!reduced.verified || !reduced.collisions.isEmpty()) return reduced;
                Receipt next = combine(input,target,Arrays.asList(composed,reduced),originalOwners,maximumDepth,work);
                if (!next.collisions.isEmpty()) return next;
                if (sameRecipes(current.localProofs,next.localProofs,work)) {
                    ContextOrdinaryResidualCoverage.check(next.localProofs,next.localProofs,next.localProofs,maximumDepth,work);
                    ContextGroundCompositionCoverage.check(next.localProofs,next.localProofs,maximumDepth,work);
                    return new Receipt(true,"Private ground reductions/compositions closed; family saturation/admission unknown",input,target,next.products,Collections.emptyList(),originalOwners,next.localProofs);
                }
                current = next;
            }
            throw new IllegalStateException("Private composition local round limit");
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    private static boolean sameRecipes(List<Product> a,List<Product> b,Runnable work) {
        if (a.size() != b.size()) return false;
        for (Product left : a) {
            boolean found = false;
            for (Product right : b) {
                work.run(); if (left.proof.equals(right.proof) && left.clause.equivalent(right.clause)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }
    private static Receipt combine(Frontier input,Snapshot target,List<Receipt> observations,List<Product> owners,int maximumDepth,Runnable work) {
        List<Product> pool = new ArrayList<>();
        for (Receipt receipt : observations) for (Product product : receipt.localProofs)
            add(pool,product.clause,product.proof,maximumDepth,work,input.ledgerLimits);
        List<Collision> opposed = collisions(pool,work);
        if (!opposed.isEmpty()) return new Receipt(true,"Combined sourced parameter/probe opposition",input,target,Collections.emptyList(),opposed);
        List<Product> products = new ArrayList<>();
        for (Product product : pool) {
            work.run(); if (product.proof.depth > 0 && product.proof.sources.stream().anyMatch(source -> source.localX)) products.add(product);
        }
        return new Receipt(true,"Observed ordinary, parameter or isolated proofs; coverage/saturation unknown",input,target,products,Collections.emptyList(),owners,pool);
    }
    private static Receipt observe(Frontier input,Snapshot target,int maximumDepth,Runnable work,int[] remainingEvents,boolean parameterPass) {
        return observe(input,target,maximumDepth,work,remainingEvents,parameterPass,null);
    }
    private static Receipt observe(Frontier input,Snapshot target,int maximumDepth,Runnable work,int[] remainingEvents,boolean parameterPass,List<Product> isolated) {
        try {
            if (!input.cuts.contains(target)) throw new IllegalStateException("Residual cut outside sealed scope");
            List<Entry> declarations = ContextGroundCutEvidence.declarations(input.x,target == input.x ? null : target,work);
            List<Product> carriers = isolated == null ? input.products : isolated;
            List<Product> owners = new ArrayList<>();
            for (Product carried : carriers) add(owners,carried.clause,carried.proof,maximumDepth,work,input.ledgerLimits);
            if (isolated == null) for (Entry entry : declarations) for (Clause clause : entry.clauses)
                add(owners,clause,new Support(Collections.singleton(entry.source),0),maximumDepth,work,input.ledgerLimits);
            List<Collision> opposed = collisions(owners,work);
            if (!opposed.isEmpty()) return new Receipt(true,"Actual sourced unit opposition",input,target,Collections.emptyList(),opposed);
            User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); mind.setQueryPass(QueryPass.ACCEPT); mind.setFloodControlLimit(maximumDepth);
            if (remainingEvents[0] < 1) throw new IllegalStateException("Residual shared observation event limit");
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,remainingEvents[0])) {
                capture.observeResidualSubsets();
                // Replay each semantic carrier once; keep its independent original recipes together.
                List<Clause> restored = new ArrayList<>();
                for (Product carried : carriers) {
                    boolean duplicate = false;
                    for (Clause clause : restored) { work.run(); if (clause.equivalent(carried.clause)) { duplicate = true; break; } }
                    if (duplicate) continue;
                    List<Support> supports = new ArrayList<>();
                    for (Product alternative : carriers) { work.run(); if (alternative.clause.equivalent(carried.clause)) supports.add(alternative.proof); }
                    capture.replayDerived(mind,carried.clause,supports); restored.add(carried.clause);
                }
                if (isolated == null) for (Entry entry : declarations) { work.run(); capture.replay(mind,entry.source,entry.statement); }
                List<SemanticTermSnapshot> parameters = Collections.emptyList();
                if (parameterPass) {
                    List<Clause> family = new ArrayList<>(),ownerClauses = new ArrayList<>();
                    for (Snapshot cut : input.cuts) for (Entry entry : cut.primary) for (Clause clause : entry.clauses) { work.run(); family.add(clause); }
                    for (Product carried : input.products) { work.run(); family.add(carried.clause); }
                    for (Product owner : owners) { work.run(); ownerClauses.add(owner.clause); family.add(owner.clause); }
                    parameters = ContextOrdinaryResidualParameters.seed(mind,family,ownerClauses,work);
                    capture.verifyParametersFresh(parameters);
                }
                mind.link(null,false);
                ContextProductionCapture.Snapshot observed = capture.snapshot();
                remainingEvents[0] -= observed.eventCount;
                if (observed.inputs.size() != restored.size()+(isolated == null ? declarations.size() : 0)) throw new IllegalStateException("Residual replay input incomplete");
                for (int i=0;i<restored.size();++i)
                    if (!restored.get(i).equivalent(observed.inputs.get(i).clause)) throw new IllegalStateException("Residual carrier roundtrip mismatch");
                if (isolated == null) for (int i=0;i<declarations.size();++i) ContextGroundCutEvidence.sameShapes(declarations.get(i),observed.inputs.get(i+restored.size()),work);
                for (String gap : observed.gaps) if (!gap.startsWith("compound-residual:") && !gap.startsWith("unbound-waiter-composition:"))
                    throw new IllegalStateException("Residual observation gap: "+gap);
                List<Residual> events = new ArrayList<>(observed.residuals);
                for (Application app : observed.applications) events.add(new Residual(new Clause(Collections.singletonList(app.conclusion)),app.premises,app.producerSupports,app.steps));
                for (Residual event : events) authenticate(event,owners,work);
                List<Product> pool = new ArrayList<>(owners); boolean changed;
                do {
                    changed = false;
                    for (Residual event : events) {
                        List<List<Support>> donorProofs = donors(event,pool,work);
                        if (donorProofs == null) continue;
                        for (Support producer : event.producerSupports)
                            changed |= derive(event,donorProofs,0,new LinkedHashSet<>(producer.sources),producer.depth,pool,maximumDepth,work,input.ledgerLimits);
                    }
                } while (changed);
                for (Residual event : events) if (donors(event,pool,work) == null) throw new IllegalStateException("Residual native premise unproved");
                if (!parameters.isEmpty()) {
                    List<Product> generalized = new ArrayList<>();
                    for (Product product : pool) add(generalized,product.clause.generalize(parameters),product.proof,maximumDepth,work,input.ledgerLimits);
                    pool = generalized;
                }
                opposed = collisions(pool,work);
                if (!opposed.isEmpty()) return new Receipt(true,"Reconstructed sourced unit opposition",input,target,Collections.emptyList(),opposed);
                List<Product> products = new ArrayList<>();
                for (Product product : pool) {
                    work.run(); if (product.proof.depth > 0 && product.proof.sources.stream().anyMatch(s -> s.localX)) products.add(product);
                }
                return new Receipt(true,"Observed signed residual proofs; coverage/saturation unknown",input,target,products,Collections.emptyList(),owners,pool);
            }
        } catch (Exception failure) { return new Receipt(false,failure.getMessage(),input,target,Collections.emptyList(),Collections.emptyList()); }
    }
    public static Barrier merge(Frontier input,List<Receipt> receipts,int maximumWork) {
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            List<Receipt> outputs = new ArrayList<>(receipts);
            if (outputs.size() != input.cuts.size()) throw new IllegalStateException("Incomplete residual family barrier");
            Set<Snapshot> seen = new HashSet<>(); Receipt failed = null;
            for (Receipt output : outputs) {
                work.run(); if (output == null || output.input != input || !input.cuts.contains(output.target) || !seen.add(output.target))
                    throw new IllegalStateException("Residual receipt scope/input/evidence mismatch");
                if (!output.verified && failed == null) failed = output;
            }
            // Report a failure only after binding every receipt to the complete same-input family.
            if (failed != null) throw new IllegalStateException("Residual receipt incomplete: "+failed.reason);
            List<Collision> opposed = new ArrayList<>();
            for (Receipt output : outputs) { work.run(); opposed.addAll(output.collisions); }
            if (!opposed.isEmpty()) return new Barrier(Status.COLLISION,"Proved residual cut collision",null,false,opposed);
            List<Product> pool = new ArrayList<>(input.products); boolean changed = false;
            for (Receipt output : outputs) for (Product product : output.products) changed |= add(pool,product.clause,product.proof,Integer.MAX_VALUE,work,input.ledgerLimits);
            opposed = collisions(pool,work);
            if (!opposed.isEmpty()) return new Barrier(Status.COLLISION,"Opposite shared residual unit proofs",null,false,opposed);
            return new Barrier(Status.MERGED,"Observed residual barrier; coverage/saturation unknown",new Frontier(input.x,input.cuts,pool,input.identity,input.ledgerLimits),changed,Collections.emptyList());
        } catch (Exception failure) { return new Barrier(Status.INCOMPLETE,failure.getMessage(),null,false,Collections.emptyList()); }
    }
}
