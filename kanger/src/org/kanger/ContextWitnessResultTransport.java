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
        final Cut roundCut;
        final ContextWitnessEmptyTarget emptyTarget;
        final PrivatePool privateTarget;
        private Packet(List<Item> items) { this(items,null,Collections.emptySet(),false); }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep) {
            this(items,basis,producers,observedStep,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut) {
            this(items,basis,producers,observedStep,emptyCut,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut,Cut coveredCut) {
            this(items,basis,producers,observedStep,emptyCut,coveredCut,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut,Cut coveredCut,Cut roundCut) {
            this(items,basis,producers,observedStep,emptyCut,coveredCut,roundCut,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut,Cut coveredCut,Cut roundCut,ContextWitnessEmptyTarget emptyTarget) {
            this(items,basis,producers,observedStep,emptyCut,coveredCut,roundCut,emptyTarget,null);
        }
        private Packet(List<Item> items,UUID basis,Set<Source> producers,boolean observedStep,Cut emptyCut,Cut coveredCut,Cut roundCut,ContextWitnessEmptyTarget emptyTarget,PrivatePool privateTarget) {
            this.privateTarget=privateTarget;
            this.emptyTarget = emptyTarget;
            this.roundCut = roundCut;
            this.items = Collections.unmodifiableList(new ArrayList<>(items)); this.basis = basis;
            this.producers = Collections.unmodifiableSet(new LinkedHashSet<>(producers)); this.observedStep = observedStep;
            this.emptyCut = emptyCut;
            this.coveredCut = coveredCut;
        }
    }
    /** Sealed branch-local evidence. Its raw items are never a shared family Packet. */
    static final class PrivatePool {
        final Packet common;
        final ContextSignedWitnessFrameAudit.Receipt signedSeeds;
        final ContextProductionNativeBinding.Snapshot x,snapshot;
        final List<ContextProductionInputs.Declaration> declarations;
        final Cut cut;
        final Packet local;
        final Set<Source> sources;
        private PrivatePool(Packet common,ContextProductionNativeBinding.Snapshot x,ContextProductionNativeBinding.Snapshot snapshot,Packet local,Set<Source> sources,List<ContextProductionInputs.Declaration> declarations) {
            this(common,x,snapshot,local,sources,declarations,null);
        }
        private PrivatePool(Packet common,ContextProductionNativeBinding.Snapshot x,ContextProductionNativeBinding.Snapshot snapshot,Packet local,Set<Source> sources,List<ContextProductionInputs.Declaration> declarations,ContextSignedWitnessFrameAudit.Receipt signedSeeds) {
            this.signedSeeds=signedSeeds;this.common=common;this.x=x;this.snapshot=snapshot;this.cut=Cut.of(snapshot);this.local=local;
            this.declarations=Collections.unmodifiableList(new ArrayList<>(declarations));
            this.sources=Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        }
    }
    /** A ground unit is entailed by its whole native-bound primary row; never mint a clause Source. */
    static boolean privateSeedClause(Clause clause,Runnable work) {
        work.run();if(!clause.variables.isEmpty()||clause.literals.size()!=1||!clause.literals.get(0).positive)return false;
        for(ContextProductionCapture.Slot slot:clause.literals.get(0).arguments){work.run();if(slot.variable>=0||slot.value==null)return false;}
        return true;
    }
    static int privateSeedLiterals(ContextProductionNativeBinding.Entry entry,Runnable work) {
        if(entry.bundle!=null)return entry.bundle.atoms.size();
        int count=0;for(Clause clause:entry.clauses)if(privateSeedClause(clause,work))++count;
        return count;
    }
    static boolean privateSeedOnly(ContextProductionNativeBinding.Entry entry,int literals) {
        return entry.bundle!=null||literals>0&&literals==entry.clauses.size();
    }
    static PrivatePool privatePool(Packet common,ContextProductionNativeBinding.Snapshot x,ContextProductionNativeBinding.Snapshot target,int maximumWork) {
        return privatePool(common,x,target,maximumWork,false);
    }
    static PrivatePool privatePoolSigned(Packet common,ContextProductionNativeBinding.Snapshot x,ContextProductionNativeBinding.Snapshot target,int maximumWork) {
        return privatePool(common,x,target,maximumWork,true);
    }
    private static PrivatePool privatePool(Packet common,ContextProductionNativeBinding.Snapshot x,ContextProductionNativeBinding.Snapshot target,int maximumWork,boolean signedCommon) {
        Runnable work=budget(maximumWork);ContextProductionFamilyAssembly.identity(x,true,work);ContextProductionFamilyAssembly.identity(target,false,work);
        if(!Cut.of(x).equals(parentX(common.items,work)))throw new IllegalStateException("Private pool has another X view");
        if(x.primary.size()+target.primary.size()>64)throw new IllegalStateException("Private whole declaration limit");
        for(Item item:common.items){work.run();if(!signedCommon&&!item.atom.positive)throw new IllegalStateException("Positive common witness pool required");}
        family(common,Collections.singletonList(Cut.of(target)),maximumWork);
        List<Item> local=new ArrayList<>(common.items);Set<Source> sources=new LinkedHashSet<>();
        for(ContextProductionNativeBinding.Entry entry:target.primary) {
            work.run();if(privateSeedLiterals(entry,work)==0)continue;
            if(!sources.add(entry.source))throw new IllegalStateException("Repeated private primary seed Source");
            Support proof=new Support(Collections.singleton(entry.source),0);
            if(entry.bundle==null) {
                for(Clause clause:entry.clauses){
                    if(!privateSeedClause(clause,work))continue;
                    ContextProductionCapture.Atom atom=clause.literals.get(0);List<Slot> slots=new ArrayList<>();
                    for(ContextProductionCapture.Slot slot:atom.arguments){work.run();slots.add(new Slot(null,slot.value));}
                    add(local,new Atom(atom.predicate,true,slots),proof,work);
                }
                continue;
            }
            for(ContextWitnessBundle.Atom atom:entry.bundle.atoms) {
                work.run();if(!atom.positive)throw new IllegalStateException("Positive private witness bundle required");
                List<Slot> slots=new ArrayList<>();Origin root=null;
                for(ContextWitnessBundle.Slot slot:atom.arguments) {
                    work.run();Origin origin=slot.witness<0?null:new Origin(entry.source,slot.witness);
                    if(origin!=null){if(root!=null&&!root.equals(origin))throw new IllegalStateException("Private literal with multiple witnesses outside transport scope");root=origin;}
                    slots.add(new Slot(origin,slot.value));
                }
                add(local,new Atom(atom.predicate,true,slots),proof,work);
            }
        }
        if(sources.isEmpty())throw new IllegalStateException("Private positive primary seed required");
        Set<Origin> rank=new HashSet<>();for(Item item:local)for(Slot slot:item.atom.arguments){work.run();if(slot.origin!=null)rank.add(slot.origin);}
        if(rank.size()>8)throw new IllegalStateException("Private carrier witness rank limit");
        List<ContextProductionInputs.Declaration> declarations=new ArrayList<>();
        for(ContextProductionNativeBinding.Snapshot view:Arrays.asList(x,target))for(ContextProductionNativeBinding.Entry entry:view.primary){work.run();if(entry.bundle==null)declarations.add(new ContextProductionInputs.Declaration(entry.source,entry.statement));}
        return new PrivatePool(common,x,target,new Packet(local),sources,declarations);
    }
    /** A local continuation receipt, deliberately not a shared-family coverage receipt. */
    static final class PrivateAdvance {
        final PrivatePool before, after;
        final Packet output;
        final boolean localChanged, commonChanged;
        final int obligations;
        final Set<Source> producers;
        private PrivateAdvance(PrivatePool before,PrivatePool after,Packet output,int obligations,Set<Source> producers,Runnable work) {
            this.before=before;this.after=after;this.output=output;this.obligations=obligations;
            this.producers=Collections.unmodifiableSet(new LinkedHashSet<>(producers));
            localChanged=!sameItems(before.local.items,after.local.items,work);
            commonChanged=!sameItems(before.common.items,output.items,work);
        }
    }
    /** Rebase only a sealed local continuation, keeping private proofs at their original cut. */
    static PrivatePool rebasePrivate(PrivatePool previous,Packet common,int maximumWork) {
        return rebasePrivate(previous,common,maximumWork,false);
    }
    static PrivatePool rebasePrivateSigned(PrivatePool previous,Packet common,int maximumWork) {
        return rebasePrivate(previous,common,maximumWork,true);
    }
    private static PrivatePool rebasePrivate(PrivatePool previous,Packet common,int maximumWork,boolean signedCommon) {
        Runnable work=budget(maximumWork);
        if(!retains(previous.common,common,work))throw new IllegalStateException("Private rebase lost a common input proof");
        PrivatePool fresh=privatePool(common,previous.x,previous.snapshot,maximumWork,signedCommon);
        List<Item> local=new ArrayList<>(fresh.local.items);
        for(Item item:previous.local.items)for(Support proof:item.proofs){work.run();add(local,item.atom,proof,work);}
        privateRank(local,work);
        return new PrivatePool(common,previous.x,previous.snapshot,new Packet(local),previous.sources,previous.declarations);
    }
    /** Select whole declarations from the owning native snapshots; never accept alleged caller statements. */
    private static List<Input> privateDefinitions(PrivatePool pool,Set<Source> selected,int maximumWork,int maximumEvents,Runnable work)throws Exception {
        if(selected.isEmpty()||selected.size()>64)throw new IllegalStateException("Bounded nonempty private producer selection required");
        List<Input> all=ContextWitnessDeclarationCut.capturePrivate(pool,pool.declarations,work,maximumWork,maximumEvents);
        List<Input> definitions=new ArrayList<>();Set<Source> remaining=new HashSet<>(selected);
        for(Input input:all){work.run();if(remaining.remove(input.source))definitions.add(input);}
        if(!remaining.isEmpty())throw new IllegalStateException("Private producer outside owning whole declaration snapshots");
        return definitions;
    }
    /** Checks the complete selected local proof pool; unchanged common contents alone prove nothing. */
    static int verifyPrivateClosed(PrivatePool pool,Set<Source> selected,int maximumDepth,int maximumWork,int maximumEvents)throws Exception {
        Runnable work=budget(maximumWork);
        return ContextWitnessHornClosure.verify(pool.local,selected.isEmpty()
                ? ContextWitnessDeclarationCut.capturePrivate(pool,Collections.emptyList(),work,maximumWork,maximumEvents)
                : privateDefinitions(pool,selected,maximumWork,maximumEvents,work),maximumDepth,work);
    }
    /** A dormant or already closed local theory needs no new native chain application. */
    static PrivateAdvance closedPrivate(PrivatePool pool,Set<Source> selected,int maximumDepth,int maximumWork,int maximumEvents)throws Exception {
        Runnable work=budget(maximumWork);
        List<Input> definitions=selected.isEmpty() ? ContextWitnessDeclarationCut.capturePrivate(pool,Collections.emptyList(),work,maximumWork,maximumEvents)
                : privateDefinitions(pool,selected,maximumWork,maximumEvents,work);
        int obligations=ContextWitnessHornClosure.verify(pool.local,definitions,maximumDepth,work);
        Set<Source> producers=new LinkedHashSet<>();for(Input definition:definitions){work.run();producers.add(definition.source);}
        return new PrivateAdvance(pool,pool,privateShared(pool.common,pool.local,work),obligations,producers,work);
    }
    private static Packet privateShared(Packet common,Packet local,Runnable work) {
        List<Item> shared=new ArrayList<>(common.items);
        for(Item item:local.items)for(Support proof:item.proofs){work.run();if(usesX(proof))add(shared,item.atom,proof,work);}
        Packet output=new Packet(shared);parentX(output.items,work);return output;
    }
    /** One native pass with input-enabled obligations over local before/after pools, not just X exports. */
    static PrivateAdvance advancePrivate(PrivatePool pool,Set<Source> selected,int maximumDepth,int maximumWork,int maximumEvents)throws Exception {
        Runnable work=budget(maximumWork);
        List<Input> definitions=privateDefinitions(pool,selected,maximumWork,maximumEvents,work);
        ContextWitnessHornClosure.validate(pool.local,definitions,maximumDepth,work);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,maximumWork,maximumEvents)) {
            for(ContextProductionInputs.Declaration declaration:pool.declarations)if(selected.contains(declaration.source)){
                work.run();pass.rule(declaration.source,declaration.statement);
            }
            ContextWitnessUnaryChainVerifier.Result verified=pass.inspect(maximumDepth,maximumWork);
            if(!verified.reconstructed)throw new IllegalStateException(verified.reason);
            ContextWitnessHornClosure.sameDeclarations(definitions,pass.producerInputs(work),work);
            Map<Root,Origin> origins=new HashMap<>();
            for(Map.Entry<Origin,Root> entry:pass.bindings.entrySet()){
                work.run();if(origins.put(entry.getValue(),entry.getKey())!=null)throw new IllegalStateException("Ambiguous private carried origin");
            }
            List<Item> local=new ArrayList<>(pool.local.items);
            for(Route route:verified.routes){work.run();if(route.proof.depth>0)add(local,transportAtom(route,origins,pass.namespace,work),route.proof,work);}
            privateRank(local,work);Packet after=new Packet(local);
            int obligations=ContextWitnessHornClosure.verifyRound(pool.local,after,definitions,maximumDepth,work);
            // No roundCut/coveredCut/basis until the owning round receipt is requested.
            Packet output=privateShared(pool.common,after,work);
            PrivatePool next=new PrivatePool(pool.common,pool.x,pool.snapshot,after,pool.sources,pool.declarations);
            return new PrivateAdvance(pool,next,output,obligations,pass.producerSources(work),work);
        }
    }
    /** Only a verified local input-enabled continuation can issue this common-output round receipt. */
    static Packet privateRoundOutput(PrivateAdvance step,int maximumWork) {
        Runnable work=budget(maximumWork);
        parentX(step.output.items,work);
        if(!retains(step.before.common,step.output,work))throw new IllegalStateException("Private round lost common input");
        return new Packet(step.output.items,step.before.common.identity,step.producers,step.localChanged,
                step.localChanged?null:step.before.cut,null,step.before.cut,null,step.before);
    }
    /** Sound observed facts only. This is deliberately not PrivateAdvance or a covered round STEP. */
    static final class DerivedTransfer {
        final boolean transferred,commonChanged,localChanged;
        final String reason;
        final Packet before,output,local;
        final PrivatePool privateBefore,privateAfter;
        final Cut parent,target;
        final ContextUniversalDerivedRoutes.Result proof;
        private DerivedTransfer(boolean transferred,String reason,Packet before,Packet output,Packet local,
                PrivatePool privateBefore,PrivatePool privateAfter,ContextUniversalDerivedRoutes.Result proof,Runnable work) {
            this.transferred=transferred;this.reason=reason;this.before=before;this.output=output;this.local=local;
            this.privateBefore=privateBefore;this.privateAfter=privateAfter;this.proof=proof;
            parent=proof==null||proof.input==null?null:proof.input.parent;target=proof==null||proof.input==null?null:proof.input.target;
            commonChanged=transferred&&!sameItems(before.items,output.items,work);
            localChanged=transferred&&!sameItems(privateBefore==null?before.items:privateBefore.local.items,local.items,work);
        }
    }
    private static Atom derivedAtom(ContextUniversalDerivedRoutes.Route route,Map<Root,Origin> origins,Runnable work) {
        if(!Objects.equals(route.physical.root,route.origin))throw new IllegalStateException("Noncanonical derived conclusion cannot be transported");
        Origin origin=origins.get(route.origin);
        if(route.origin!=null&&origin==null)throw new IllegalStateException("Missing carried derived transport origin");
        List<Slot> arguments=new ArrayList<>();
        for(Argument argument:route.physical.arguments) {
            work.run();if(argument.root!=null&&!argument.root.equals(route.origin))throw new IllegalStateException("Different derived argument origin");
            arguments.add(new Slot(argument.root==null?null:origin,argument.value));
        }
        return new Atom(route.physical.predicate,route.physical.positive,arguments);
    }
    static DerivedTransfer transferDerived(ContextUniversalDerivedRoutes.Result proof,Packet input,int maximumDepth,int maximumWork) {
        return transferDerived(proof,input,null,maximumDepth,maximumWork);
    }
    static DerivedTransfer transferDerivedPrivate(ContextUniversalDerivedRoutes.Result proof,PrivatePool pool,int maximumDepth,int maximumWork) {
        return transferDerived(proof,pool==null?null:pool.common,pool,maximumDepth,maximumWork);
    }
    private static DerivedTransfer transferDerived(ContextUniversalDerivedRoutes.Result proof,Packet input,PrivatePool pool,int maximumDepth,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded derived transfer required");
        Runnable work=budget(maximumWork);
        try {
            if(proof==null||!proof.reconstructed||proof.input==null||!proof.input.bound||input==null)
                throw new IllegalStateException("Sealed reconstructed derived input required");
            ContextUniversalDerivedReplay.Result replay=proof.input;Packet local=pool==null?input:pool.local;
            if(replay.privateInput!=(pool!=null)||!input.identity.equals(proof.basis)||!local.identity.equals(proof.localBasis))
                throw new IllegalStateException("Derived transport has a different common/local input barrier");
            if(!replay.parent.equals(parentX(input.items,work)))throw new IllegalStateException("Derived transport changed parent X");
            if(pool!=null&&(!pool.cut.equals(replay.target)||!Cut.of(pool.x).equals(replay.parent)))
                throw new IllegalStateException("Derived transport changed owning private frame");
            for(Item item:local.items)for(Support support:item.proofs){work.run();if(support.depth>maximumDepth)throw new IllegalStateException("Derived input depth limit");}
            List<Item> after=new ArrayList<>(local.items);
            List<ContextUniversalDerivedRoutes.Route> routes=new ArrayList<>(proof.conclusions);routes.addAll(proof.theorems);
            for(ContextUniversalDerivedRoutes.Route route:routes) {
                work.run();if(route.support.depth>maximumDepth)throw new IllegalStateException("Derived transport depth limit");
                // Only canonical event conclusions and checked ground theorems cross.
                // Stored native occurrences never establish global root aliases.
                if(pool==null&&!usesX(route.support))continue;
                add(after,derivedAtom(route,replay.carried,work),route.support,work);
            }
            privateRank(after,work);Packet retained=new Packet(after);
            Packet output=pool==null?retained:privateShared(input,retained,work);
            parentX(output.items,work);
            PrivatePool next=pool==null?null:new PrivatePool(input,pool.x,pool.snapshot,retained,pool.sources,pool.declarations);
            return new DerivedTransfer(true,"Observed derived facts transported; schema gaps and round coverage remain open",input,output,retained,pool,next,proof,work);
        }catch(Exception failure){return new DerivedTransfer(false,failure.getMessage(),input,null,null,pool,null,proof,work);}
    }
    /** One synchronous proof-product barrier, without any claim of complete branch traversal or admission. */
    static final class DerivedBarrier {
        final boolean merged,changed,localChanged;
        final String reason;
        final Packet packet;
        final Map<Cut,DerivedTransfer> branches;
        final Map<Cut,PrivatePool> privatePools;
        private DerivedBarrier(boolean merged,String reason,Packet packet,boolean changed,boolean localChanged,
                Map<Cut,DerivedTransfer> branches,Map<Cut,PrivatePool> privatePools) {
            this.merged=merged;this.reason=reason;this.packet=packet;this.changed=changed;this.localChanged=localChanged;
            this.branches=Collections.unmodifiableMap(new LinkedHashMap<>(branches));
            this.privatePools=Collections.unmodifiableMap(new LinkedHashMap<>(privatePools));
        }
    }
    static DerivedBarrier mergeDerived(Family family,Map<Cut,DerivedTransfer> outputs,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded derived barrier required");
        Runnable work=budget(maximumWork);
        try {
            if(family==null||outputs==null||!outputs.keySet().equals(new HashSet<>(family.expected)))
                throw new IllegalStateException("Incomplete or unexpected derived family");
            Map<Cut,DerivedTransfer> branches=new LinkedHashMap<>();List<Item> merged=new ArrayList<>(family.input.items);boolean localChanged=false;
            for(Cut cut:family.expected) {
                work.run();DerivedTransfer branch=outputs.get(cut);
                if(branch==null||!branch.transferred||branch.before!=family.input||!cut.equals(branch.target)||!family.parent.equals(branch.parent))
                    throw new IllegalStateException("Derived branch has a different owning cut/common barrier");
                if(!retains(family.input,branch.output,work))throw new IllegalStateException("Derived branch lost a common input proof");
                for(Item item:branch.output.items)for(Support proof:item.proofs){work.run();add(merged,item.atom,proof,work);}
                branches.put(cut,branch);localChanged|=branch.localChanged;
            }
            // No branch sees another branch's same-round products. Private rebasing
            // happens only after every output has passed the common-input check.
            Packet packet=new Packet(merged);parentX(packet.items,work);privateRank(packet.items,work);
            Map<Cut,PrivatePool> pools=new LinkedHashMap<>();
            for(Map.Entry<Cut,DerivedTransfer> entry:branches.entrySet())if(entry.getValue().privateAfter!=null) {
                work.run();pools.put(entry.getKey(),rebasePrivate(entry.getValue().privateAfter,packet,maximumWork));
            }
            return new DerivedBarrier(true,"Synchronous observed derived products merged; closure, all-cut qualification and admission remain open",packet,
                !sameItems(family.input.items,packet.items,work),localChanged,branches,pools);
        }catch(Exception failure){return new DerivedBarrier(false,failure.getMessage(),null,false,false,Collections.emptyMap(),Collections.emptyMap());}
    }
    /** Combined checked typed facts only; no round/discovery/admission authority. */
    static final class TypedTransfer {
        final boolean transferred,commonChanged,localChanged;
        final String reason;
        final Packet before,output,local;
        final PrivatePool privateBefore,privateAfter;
        final Cut parent,target;
        final ContextUniversalFrozenEvents.Result proof;
        final ContextUniversalFrozenInstances.Result instances;
        private TypedTransfer(boolean transferred,String reason,Packet before,Packet output,Packet local,PrivatePool privateBefore,
                PrivatePool privateAfter,ContextUniversalFrozenEvents.Result proof,ContextUniversalFrozenInstances.Result instances,Runnable work) {
            this.transferred=transferred;this.reason=reason;this.before=before;this.output=output;this.local=local;
            this.privateBefore=privateBefore;this.privateAfter=privateAfter;this.proof=proof;this.instances=instances;
            ContextUniversalFrozenClosure.Result theory=proof==null||proof.input==null||proof.input.input==null?null:proof.input.input.theory;
            parent=theory==null?null:theory.parent;target=theory==null?null:theory.target;
            commonChanged=transferred&&!sameItems(before.items,output.items,work);
            localChanged=transferred&&!sameItems(privateBefore==null?before.items:privateBefore.local.items,local.items,work);
        }
    }
    private static Atom typedAtom(ContextUniversalInputResiduals.Residual fact,Set<Origin> known,Runnable work) {
        return typedAtom(fact,known,false,work);
    }
    private static Atom typedAtom(ContextUniversalInputResiduals.Residual fact,Set<Origin> known,boolean signed,Runnable work) {
        if(fact.literals.size()!=1||!fact.variables.isEmpty()||!signed&&!fact.literals.get(0).positive)
            throw new IllegalStateException("Fixed positive typed transport fact required");
        ContextUniversalInputResiduals.Literal literal=fact.literals.get(0);List<Slot> arguments=new ArrayList<>();
        for(ContextUniversalInputResiduals.Term term:literal.arguments) {
            work.run();if(term.variable>=0||(term.origin==null)==(term.value==null)||term.origin!=null&&!known.contains(term.origin))
                throw new IllegalStateException("Unbound or nonfixed typed transport argument");
            arguments.add(new Slot(term.origin,term.value));
        }
        return new Atom(literal.predicate,literal.positive,arguments);
    }
    static TypedTransfer transferTyped(ContextUniversalFrozenEvents.Result proof,Packet input,int maximumDepth,int maximumWork) {
        return transferTyped(proof,null,input,null,maximumDepth,maximumWork);
    }
    static TypedTransfer transferTypedPrivate(ContextUniversalFrozenEvents.Result proof,PrivatePool pool,int maximumDepth,int maximumWork) {
        return transferTyped(proof,null,pool==null?null:pool.common,pool,maximumDepth,maximumWork);
    }
    static TypedTransfer transferTypedInstances(ContextUniversalFrozenEvents.Result proof,ContextUniversalFrozenInstances.Result instances,Packet input,int maximumDepth,int maximumWork) {
        if(instances==null)throw new IllegalArgumentException("Actual instance receipt required");
        return transferTyped(proof,instances,input,null,maximumDepth,maximumWork);
    }
    static TypedTransfer transferTypedInstancesPrivate(ContextUniversalFrozenEvents.Result proof,ContextUniversalFrozenInstances.Result instances,PrivatePool pool,int maximumDepth,int maximumWork) {
        if(instances==null)throw new IllegalArgumentException("Actual instance receipt required");
        return transferTyped(proof,instances,pool==null?null:pool.common,pool,maximumDepth,maximumWork);
    }
    private static TypedTransfer transferTyped(ContextUniversalFrozenEvents.Result proof,ContextUniversalFrozenInstances.Result instances,Packet input,PrivatePool pool,int maximumDepth,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded typed transfer required");
        Runnable work=budget(maximumWork);
        try {
            if(proof==null||!proof.reconstructed||proof.input==null||!proof.input.projected||input==null)
                throw new IllegalStateException("Sealed typed unit/event proof required");
            if(instances!=null&&(!instances.proved||instances.input!=proof.input))throw new IllegalStateException("Instances changed exact sealed unit frame");
            ContextUniversalFrozenReplay.Result replay=proof.input.input;
            ContextUniversalInputResiduals.Frame frame=replay.theory.frame;Packet local=pool==null?input:pool.local;
            if(frame.common!=input||frame.local!=local||frame.pool!=pool||replay.theory.privateInput!=(pool!=null))
                throw new IllegalStateException("Typed transfer changed exact common/local input frame");
            if(!replay.theory.parent.equals(parentX(input.items,work)))throw new IllegalStateException("Typed transfer changed X cut");
            if(pool!=null&&(!pool.cut.equals(replay.theory.target)||!Cut.of(pool.x).equals(replay.theory.parent)))
                throw new IllegalStateException("Typed transfer changed owning private cut");
            Set<Origin> known=new HashSet<>();for(Origin origin:replay.carried.values()){work.run();known.add(origin);}
            List<Item> after=new ArrayList<>(local.items);
            for(ContextUniversalFrozenUnits.Unit unit:proof.input.units) {
                work.run();if(pool==null&&!usesX(unit.support))continue;
                add(after,typedAtom(unit.conclusion,known,work),unit.support,work);
            }
            for(ContextUniversalFrozenEvents.Conclusion conclusion:proof.conclusions) {
                work.run();if(pool==null&&!usesX(conclusion.support))continue;
                add(after,typedAtom(conclusion.fact,known,work),conclusion.support,work);
            }
            if(instances!=null)for(ContextUniversalFrozenInstances.Instance instance:instances.instances)for(ContextUniversalFrozenEvents.Donor donor:instance.proofs) {
                work.run();if(pool==null&&!usesX(donor.support))continue;
                add(after,typedAtom(instance.fact,known,work),donor.support,work);
            }
            // Compare minimum fact/exact-Source grades only after input, fixed
            // theorems, event and instance proofs have all participated. Recipe limits
            // are not fact lower bounds. Stored occurrences never add aliases.
            for(Item item:after)for(Support support:item.proofs){work.run();if(support.depth>maximumDepth)throw new IllegalStateException("Uncovered typed fact depth limit");}
            privateRank(after,work);Packet retained=new Packet(after);
            if(!retains(local,retained,work))throw new IllegalStateException("Typed transfer lost a local input proof");
            Packet output=pool==null?retained:privateShared(input,retained,work);parentX(output.items,work);
            PrivatePool next=pool==null?null:new PrivatePool(input,pool.x,pool.snapshot,retained,pool.sources,pool.declarations);
            return new TypedTransfer(true,"Minimum typed fixed/event/instance products transported; full round qualification remains open",input,output,retained,pool,next,proof,instances,work);
        }catch(Exception failure){return new TypedTransfer(false,failure.getMessage(),input,null,null,pool,null,proof,instances,work);}
    }
    /** Fixed signed unit products only; no all-cut collection or family admission authority. */
    static final class ConstraintTransfer {
        final boolean transferred,commonChanged,localChanged;
        final String reason;
        final ContextUniversalFrozenConstraints.Result proof;
        final Packet before,output,local;
        final PrivatePool privateBefore,privateAfter;
        final Cut parent,target;
        final ContextUniversalOriginBindings.Receipt bindings;final ContextUniversalGroupedLocalRounds.Certificate certificate;
        private ConstraintTransfer(boolean transferred,String reason,ContextUniversalFrozenConstraints.Result proof,Packet before,Packet output,Packet local,PrivatePool privateBefore,PrivatePool privateAfter,Runnable work) {this(transferred,reason,proof,before,output,local,privateBefore,privateAfter,work,null,null);}
        private ConstraintTransfer(boolean transferred,String reason,ContextUniversalFrozenConstraints.Result proof,Packet before,Packet output,Packet local,PrivatePool privateBefore,PrivatePool privateAfter,Runnable work,ContextUniversalOriginBindings.Receipt bindings,ContextUniversalGroupedLocalRounds.Certificate certificate) {
            this.bindings=transferred?bindings:null;this.certificate=transferred?certificate:null;this.transferred=transferred;this.reason=reason;this.proof=proof;this.before=before;this.output=output;this.local=local;this.privateBefore=privateBefore;this.privateAfter=privateAfter;
            parent=proof==null||proof.input==null?null:proof.input.parent;target=proof==null||proof.input==null?null:proof.input.target;
            commonChanged=transferred&&!sameItems(before.items,output.items,work);
            localChanged=transferred&&!sameItems(privateBefore==null?before.items:privateBefore.local.items,local.items,work);
        }
    }
    static ConstraintTransfer transferConstraints(ContextUniversalFrozenConstraints.Result proof,Packet input,int maximumDepth,int maximumWork) {
        return transferConstraints(proof,input,null,maximumDepth,maximumWork);
    }
    static ConstraintTransfer transferConstraintsPrivate(ContextUniversalFrozenConstraints.Result proof,PrivatePool pool,int maximumDepth,int maximumWork) {
        return transferConstraints(proof,pool==null?null:pool.common,pool,maximumDepth,maximumWork);
    }
    /** Explicit local-stability-bound transport; previous APIs retain their original Origin scope. */
    static ConstraintTransfer transferConstraintsCertified(ContextUniversalGroupedLocalRounds.Certificate certificate,int maximumDepth,int maximumWork) {
        ContextUniversalFrozenConstraints.Result proof=certificate==null?null:certificate.output;
        return transferConstraints(proof,proof==null?null:proof.common,proof==null?null:proof.pool,maximumDepth,maximumWork,certificate);
    }
    static final class SessionTransfer {
        final ConstraintTransfer transfer;final ContextUniversalValidationSession.Metrics metrics;
        private SessionTransfer(ConstraintTransfer transfer,ContextUniversalValidationSession.Metrics metrics){this.transfer=transfer;this.metrics=metrics;}
    }
    /** One original transport budget, closed before the transfer/diagnostics are returned. */
    static SessionTransfer transferConstraintsCertifiedWithValidationReuse(ContextUniversalGroupedLocalRounds.Certificate certificate,int maximumDepth,int maximumWork,int capacity) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded signed constraint transfer required");
        ContextUniversalFrozenConstraints.Result proof=certificate==null?null:certificate.output;ContextUniversalValidationSession.Scope scope=ContextUniversalValidationSession.openWitness(maximumWork,capacity);ConstraintTransfer transfer;
        try{transfer=transferConstraints(proof,proof==null?null:proof.common,proof==null?null:proof.pool,maximumDepth,maximumWork,certificate,scope.work);}finally{scope.close();}
        return new SessionTransfer(transfer,scope.metrics());
    }
    private static ConstraintTransfer transferConstraints(ContextUniversalFrozenConstraints.Result proof,Packet input,PrivatePool pool,int maximumDepth,int maximumWork) {return transferConstraints(proof,input,pool,maximumDepth,maximumWork,null);}
    private static ConstraintTransfer transferConstraints(ContextUniversalFrozenConstraints.Result proof,Packet input,PrivatePool pool,int maximumDepth,int maximumWork,ContextUniversalGroupedLocalRounds.Certificate certificate) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded signed constraint transfer required");
        return transferConstraints(proof,input,pool,maximumDepth,maximumWork,certificate,budget(maximumWork));
    }
    private static ConstraintTransfer transferConstraints(ContextUniversalFrozenConstraints.Result proof,Packet input,PrivatePool pool,int maximumDepth,int maximumWork,ContextUniversalGroupedLocalRounds.Certificate certificate,Runnable work) {
        try {
            if(proof==null||!proof.closed||!proof.signedUnits||proof.xRefuted||proof.foreignRefuted)throw new IllegalStateException("Unrefuted sealed signed-unit closure required");
            Packet local=pool==null?input:pool.local;
            if(input==null||proof.common!=input||proof.pool!=pool||proof.local!=local)throw new IllegalStateException("Different exact signed transfer frame");
            ContextUniversalFrozenConstraints.check(proof,64,work);
            Set<Origin> known=new HashSet<>();for(Item item:local.items)for(Slot slot:item.atom.arguments){work.run();if(slot.origin!=null)known.add(slot.origin);}
            // The sealed symbolic input was rechecked above, including actual historical binding Items.
            // It carries rigid Origins even when their raw private seed is absent from this branch.
            if(proof.exchange!=null)for(ContextUniversalSignedUnitExchange.Unit unit:proof.exchange.units)for(Origin origin:unit.clause.origins){work.run();known.add(origin);}
            ContextUniversalOriginBindings.Receipt bindings=null;
            if(certificate!=null){if(certificate.output!=proof)throw new IllegalStateException("Different actual local stability output");ContextUniversalGroupedLocalRounds.check(certificate,work);bindings=ContextUniversalOriginBindings.checked(proof,work);for(ContextUniversalOriginBindings.Route route:bindings.routes){work.run();known.add(route.origin);}}
            List<Item> after=new ArrayList<>(local.items);
            for(ContextUniversalFrozenConstraints.Proof unit:proof.proofs) {
                work.run();if(unit.clause==null||unit.clause.literals.size()!=1||!unit.clause.variables.isEmpty()||pool==null&&!usesX(unit.support))continue;
                add(after,typedAtom(unit.clause,known,true,work),unit.support,work);
            }
            for(Item item:after)for(Support support:item.proofs){work.run();if(support.depth>maximumDepth)throw new IllegalStateException("Uncovered signed fact depth limit");}
            privateRank(after,work);Packet retained=new Packet(after);if(!retains(local,retained,work))throw new IllegalStateException("Signed transfer lost an input proof");
            Packet output=pool==null?retained:privateShared(input,retained,work);parentX(output.items,work);
            PrivatePool next=pool==null?null:new PrivatePool(input,pool.x,pool.snapshot,retained,pool.sources,pool.declarations);
            return new ConstraintTransfer(true,"Checked fixed signed units transported; symbolic/all-cut qualification remains open",proof,input,output,retained,pool,next,work,bindings,certificate);
        }catch(Exception failure){return new ConstraintTransfer(false,failure.toString(),proof,input,null,null,pool,null,work);}
    }
    /** Supplied signed products only; qualification of the next common barrier remains separate. */
    static final class ConstraintBarrier {
        final boolean merged,changed,localChanged;
        final String reason;
        final Packet packet;
        final Map<Cut,ConstraintTransfer> branches;
        final Map<Cut,PrivatePool> privatePools;
        private ConstraintBarrier(boolean merged,String reason,Packet packet,boolean changed,boolean localChanged,Map<Cut,ConstraintTransfer> branches,Map<Cut,PrivatePool> privatePools) {
            this.merged=merged;this.reason=reason;this.packet=packet;this.changed=changed;this.localChanged=localChanged;
            this.branches=Collections.unmodifiableMap(new LinkedHashMap<>(branches));this.privatePools=Collections.unmodifiableMap(new LinkedHashMap<>(privatePools));
        }
    }
    static ConstraintBarrier mergeConstraints(Family family,Map<Cut,ConstraintTransfer> outputs,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded signed barrier required");Runnable work=budget(maximumWork);
        try {
            if(family==null||outputs==null||!outputs.keySet().equals(new HashSet<>(family.expected))||!family.expected.contains(family.parent))throw new IllegalStateException("Exact supplied cuts including self-X required");
            Map<Cut,ConstraintTransfer> branches=new LinkedHashMap<>();ContextProductionNativeBinding.Snapshot x=null;boolean localChanged=false;
            for(Cut cut:family.expected) {
                work.run();ConstraintTransfer b=outputs.get(cut);
                if(b==null||!b.transferred||b.before!=family.input||!cut.equals(b.target)||!family.parent.equals(b.parent)||b.proof==null||!b.proof.closed||!b.proof.signedUnits||b.proof.xRefuted||b.proof.foreignRefuted)throw new IllegalStateException("Incomplete/refuted or mixed-frame signed branch");
                if(x==null)x=b.proof.input.x;else if(x!=b.proof.input.x)throw new IllegalStateException("Different actual shared X snapshot");
                if(cut.localX?b.proof.input.foreign!=null||b.privateBefore!=null:b.proof.input.foreign==null||!Cut.of(b.proof.input.foreign).equals(cut))throw new IllegalStateException("Different actual native signed cut");
                if(b.privateBefore==null?b.privateAfter!=null||b.local!=b.output:b.privateAfter==null||b.privateAfter.common!=family.input||b.privateAfter.local!=b.local||b.privateAfter.x!=x||b.privateAfter.snapshot!=b.proof.input.foreign)throw new IllegalStateException("Different signed private continuation");
                if(!retains(family.input,b.output,work))throw new IllegalStateException("Signed branch lost common proof");branches.put(cut,b);localChanged|=b.localChanged;
            }
            // No merge or private rebase before all supplied outputs have passed validation.
            List<Item> merged=new ArrayList<>(family.input.items);for(ConstraintTransfer b:branches.values())for(Item item:b.output.items)for(Support support:item.proofs){work.run();add(merged,item.atom,support,work);}
            Packet packet=new Packet(merged);parentX(packet.items,work);privateRank(packet.items,work);Map<Cut,PrivatePool> pools=new LinkedHashMap<>();
            for(Map.Entry<Cut,ConstraintTransfer> e:branches.entrySet())if(e.getValue().privateAfter!=null){work.run();pools.put(e.getKey(),rebasePrivateSigned(e.getValue().privateAfter,packet,maximumWork));}
            return new ConstraintBarrier(true,"All supplied signed products merged; next-barrier qualification remains required",packet,!sameItems(family.input.items,packet.items,work),localChanged,branches,pools);
        }catch(Exception failure){return new ConstraintBarrier(false,failure.toString(),null,false,false,Collections.emptyMap(),Collections.emptyMap());}
    }
    /** Explicit first signed-witness barrier. Never used by legacy merge/rebase paths. */
    static ConstraintBarrier mergeSignedWitnessInitial(ContextSignedWitnessInitialEntry.Entry entry,Map<Cut,ConstraintTransfer> outputs,int maximumWork) {
        Runnable work=budget(maximumWork);
        try {
            if(entry==null||entry.receiver.status!=ContextSignedWitnessReceiver.Status.CHECKED||outputs==null||!outputs.keySet().equals(entry.receiver.checks.keySet()))throw new IllegalStateException("Complete actual signed initial cut ledger required");
            ContextSignedWitnessFrameAudit.Receipt seeds=entry.receiver.frame;
            Map<Cut,ConstraintTransfer> branches=new LinkedHashMap<>();boolean localChanged=false;
            for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:entry.receiver.checks.entrySet()){
                work.run();ConstraintTransfer b=outputs.get(e.getKey());
                if(b==null||b.proof!=e.getValue()||b.before!=entry.common||b.privateBefore!=entry.pools.get(e.getKey())||b.bindings!=null||b.certificate!=null)throw new IllegalStateException("Different actual initial signed transfer owner");
                ContextUniversalSignedProductAudit.Result audit=ContextUniversalSignedProductAudit.bind(e.getValue(),b,65536,maximumWork);if(!audit.bound)throw new IllegalStateException(audit.reason);
                branches.put(e.getKey(),b);localChanged|=!sameItems(e.getValue().local.items,b.local.items,work);
            }
            List<Item> shared=new ArrayList<>(entry.common.items);for(ConstraintTransfer b:branches.values())for(Item item:b.output.items)for(Support proof:item.proofs){work.run();add(shared,item.atom,proof,work);}
            parentX(shared,work);privateRank(shared,work);Packet common=new Packet(shared);Map<Cut,PrivatePool> pools=new LinkedHashMap<>();
            for(Map.Entry<Cut,PrivatePool> e:entry.pools.entrySet()){
                work.run();PrivatePool old=e.getValue();if(old.signedSeeds!=seeds||old.common!=entry.common)throw new IllegalStateException("Different original signed seed scope");
                List<Item> local=new ArrayList<>(common.items);for(Item item:branches.get(e.getKey()).local.items)for(Support proof:item.proofs){work.run();add(local,item.atom,proof,work);}privateRank(local,work);
                pools.put(e.getKey(),new PrivatePool(common,old.x,old.snapshot,new Packet(local),old.sources,old.declarations,seeds));
            }
            return new ConstraintBarrier(true,"First signed-witness all-cut barrier; next receiver qualification still required",common,!sameItems(entry.common.items,common.items,work),localChanged,branches,pools);
        }catch(Exception failure){return new ConstraintBarrier(false,failure.toString(),null,false,false,Collections.emptyMap(),Collections.emptyMap());}
    }
    /** Explicit audited successor products; no permission for later receiver inference. */
    static ConstraintBarrier mergeSignedWitnessSuccessor(ContextSignedWitnessNextReceiver.Result next,Map<Cut,ConstraintTransfer> outputs,int maximumWork) {return mergeSignedWitnessSuccessor(next,outputs,maximumWork,0);}
    static ConstraintBarrier mergeSignedWitnessSuccessor(ContextSignedWitnessNextReceiver.Result next,Map<Cut,ConstraintTransfer> outputs,int maximumWork,int capacity) {
        if(capacity<0||capacity>4096)throw new IllegalArgumentException("Bounded successor validation reuse required");
        Runnable work=budget(maximumWork);
        try {
            if(next==null||next.status!=ContextSignedWitnessNextReceiver.Status.CHECKED||next.scope==null||outputs==null||!outputs.keySet().equals(next.checks.keySet()))throw new IllegalStateException("Complete checked successor required");
            ContextSignedWitnessContinuationScope.Receipt scope=next.scope;ConstraintBarrier prior=scope.current();Packet input=prior.packet;
            if(!next.checks.keySet().equals(scope.barrier.result.initial.entry.receiver.checks.keySet()))throw new IllegalStateException("Different complete successor cut ledger");
            Map<Cut,ConstraintTransfer> branches=new LinkedHashMap<>();boolean localChanged=false;
            for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:next.checks.entrySet()){
                work.run();ConstraintTransfer b=outputs.get(e.getKey());if(b==null||b.proof!=e.getValue()||b.before!=input||b.privateBefore!=prior.privatePools.get(e.getKey()))throw new IllegalStateException("Different actual successor product owner");
                ContextSignedWitnessNextReceiver.productOwner(next,e.getKey(),b);
                ContextSignedWitnessContinuationScope.frame(scope,e.getValue().input,input,b.privateBefore);ContextUniversalSignedProductAudit.Result audit=capacity==0?ContextUniversalSignedProductAudit.bind(e.getValue(),b,65536,maximumWork):ContextUniversalSignedProductAudit.bindWithValidationReuse(e.getValue(),b,65536,maximumWork,capacity).result;if(!audit.bound)throw new IllegalStateException(audit.reason);branches.put(e.getKey(),b);localChanged|=!sameItems(e.getValue().local.items,b.local.items,work);
            }
            List<Item> shared=new ArrayList<>(input.items);for(ConstraintTransfer b:branches.values())for(Item item:b.output.items)for(Support proof:item.proofs){work.run();add(shared,item.atom,proof,work);}parentX(shared,work);privateRank(shared,work);Packet common=new Packet(shared);
            Map<Cut,PrivatePool> pools=new LinkedHashMap<>();for(Map.Entry<Cut,PrivatePool> e:prior.privatePools.entrySet()){work.run();PrivatePool old=e.getValue();if(old.signedSeeds!=scope.seeds||old.common!=input)throw new IllegalStateException("Different original successor private scope");List<Item> local=new ArrayList<>(common.items);for(Item item:branches.get(e.getKey()).local.items)for(Support proof:item.proofs){work.run();add(local,item.atom,proof,work);}privateRank(local,work);pools.put(e.getKey(),new PrivatePool(common,old.x,old.snapshot,new Packet(local),old.sources,old.declarations,scope.seeds));}
            return new ConstraintBarrier(true,"Audited successor signed-witness barrier; later receiver qualification required",common,!sameItems(input.items,common.items,work),localChanged,branches,pools);
        }catch(Exception failure){return new ConstraintBarrier(false,failure.toString(),null,false,false,Collections.emptyMap(),Collections.emptyMap());}
    }
    static final class TypedBarrier {
        final boolean merged,changed,localChanged;
        final String reason;
        final Packet packet;
        final Map<Cut,TypedTransfer> branches;
        final Map<Cut,PrivatePool> privatePools;
        private TypedBarrier(boolean merged,String reason,Packet packet,boolean changed,boolean localChanged,
                Map<Cut,TypedTransfer> branches,Map<Cut,PrivatePool> privatePools) {
            this.merged=merged;this.reason=reason;this.packet=packet;this.changed=changed;this.localChanged=localChanged;
            this.branches=Collections.unmodifiableMap(new LinkedHashMap<>(branches));this.privatePools=Collections.unmodifiableMap(new LinkedHashMap<>(privatePools));
        }
    }
    static TypedBarrier mergeTyped(Family family,Map<Cut,TypedTransfer> outputs,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded typed barrier required");Runnable work=budget(maximumWork);
        try {
            if(family==null||outputs==null||!outputs.keySet().equals(new HashSet<>(family.expected)))throw new IllegalStateException("Incomplete or unexpected typed family");
            List<Item> merged=new ArrayList<>(family.input.items);Map<Cut,TypedTransfer> branches=new LinkedHashMap<>();boolean localChanged=false;
            for(Cut cut:family.expected) {
                work.run();TypedTransfer branch=outputs.get(cut);
                if(branch==null||!branch.transferred||branch.before!=family.input||!cut.equals(branch.target)||!family.parent.equals(branch.parent))
                    throw new IllegalStateException("Typed branch changed owning cut/common barrier");
                if(!retains(family.input,branch.output,work))throw new IllegalStateException("Typed branch lost a common input proof");
                for(Item item:branch.output.items)for(Support support:item.proofs){work.run();add(merged,item.atom,support,work);}
                branches.put(cut,branch);localChanged|=branch.localChanged;
            }
            Packet packet=new Packet(merged);parentX(packet.items,work);privateRank(packet.items,work);
            Map<Cut,PrivatePool> pools=new LinkedHashMap<>();
            for(Map.Entry<Cut,TypedTransfer> entry:branches.entrySet())if(entry.getValue().privateAfter!=null) {
                work.run();pools.put(entry.getKey(),rebasePrivate(entry.getValue().privateAfter,packet,maximumWork));
            }
            return new TypedBarrier(true,"Synchronous typed products merged; discovery/all-cut qualification remain open",packet,
                !sameItems(family.input.items,packet.items,work),localChanged,branches,pools);
        }catch(Exception failure){return new TypedBarrier(false,failure.getMessage(),null,false,false,Collections.emptyMap(),Collections.emptyMap());}
    }
    private static void privateRank(List<Item> items,Runnable work) {
        Set<Origin> rank=new HashSet<>();
        for(Item item:items)for(Slot slot:item.atom.arguments){work.run();if(slot.origin!=null)rank.add(slot.origin);}
        if(rank.size()>8)throw new IllegalStateException("Private carrier witness rank limit");
    }
    private static Atom transportAtom(Route route,Map<Root,Origin> origins,UUID namespace,Runnable work) {
        if(!route.replay.equals(namespace)||!Objects.equals(route.physical.root,route.origin))throw new IllegalStateException("Non-primary carrier cannot be transported");
        Origin origin=origins.get(route.origin);
        if(origin==null&&route.origin!=null)throw new IllegalStateException("Unbound transport origin");
        List<Slot> arguments=new ArrayList<>();
        for(Argument argument:route.physical.arguments){work.run();arguments.add(new Slot(argument.root==null?null:origin,argument.value));}
        return new Atom(route.physical.predicate,route.physical.positive,arguments);
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
        final String trustedGroup;
        final Map<UUID,Cut> members;
        private Cut(Source source) {
            context=source.context;revision=source.revision;view=source.view;localX=source.localX;
            trustedGroup=null;members=Collections.emptyMap();
        }
        static Cut of(Source source) { return new Cut(source); }
        private Cut(ContextProductionInventory inventory) {
            context=inventory.context;revision=inventory.revision;view="inventory:"+inventory.fingerprint;localX=inventory.localX;
            trustedGroup=null;members=Collections.emptyMap();
        }
        static Cut of(ContextProductionInventory inventory) { return new Cut(inventory); }
        private Cut(ContextProductionNativeBinding.Snapshot snapshot) {
            context=snapshot.inventory.context;revision=snapshot.inventory.revision;view="inventory:"+snapshot.inventory.fingerprint;localX=false;
            trustedGroup=snapshot.trustedGroup;Map<UUID,Cut> bound=new LinkedHashMap<>();
            for(ContextProductionNativeBinding.Snapshot member:snapshot.members)bound.put(member.inventory.context,of(member.inventory));
            members=Collections.unmodifiableMap(bound);
        }
        static Cut of(ContextProductionNativeBinding.Snapshot snapshot) {
            return snapshot.trustedGroup==null ? of(snapshot.inventory) : new Cut(snapshot);
        }
        boolean contains(Cut actual) {
            return members.isEmpty() ? equals(actual) : Objects.equals(members.get(actual.context),actual);
        }
        Set<UUID> contexts() { return members.isEmpty()?Collections.singleton(context):members.keySet(); }
        void claimContexts(Set<UUID> seen,Runnable work) {
            for(UUID id:contexts()){work.run();if(!seen.add(id))throw new IllegalArgumentException("Repeated physical witness family Context");}
        }
        public boolean equals(Object other) {
            if (!(other instanceof Cut)) return false; Cut cut=(Cut)other;
            return context.equals(cut.context)&&revision==cut.revision&&view.equals(cut.view)&&localX==cut.localX
                &&Objects.equals(trustedGroup,cut.trustedGroup)&&members.equals(cut.members);
        }
        public int hashCode() { return Objects.hash(context,revision,view,localX,trustedGroup,members); }
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
                    if (output.roundCut != null && !cut.equals(output.roundCut))
                        throw new IllegalStateException("Round witness branch has a different target cut");
                    if (!parent.equals(parentX(output.items,work))) throw new IllegalStateException("Witness branch changed parent X");
                    boolean matched = output.emptyTarget != null;
                    if (matched) output.emptyTarget.validate(cut,work);
                    if(output.privateTarget!=null) {
                        if(!cut.equals(output.privateTarget.cut)||output.privateTarget.common!=input)
                            throw new IllegalStateException("Private round has another owning cut/common input");
                        matched=true;
                    }
                    for (Source producer : output.producers) {
                        work.run(); Cut actual = Cut.of(producer);
                        if (producer.localX) {
                            if (!parent.equals(actual)) throw new IllegalStateException("Witness branch changed X producer cut");
                            if (cut.localX) matched = true;
                        } else {
                            if (cut.localX || !cut.contains(actual)) throw new IllegalStateException("Witness branch does not match expected foreign cut");
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
            if (cut == null || cut.localX && !parent.equals(cut)
                    || !cut.localX && cut.contexts().contains(parent.context))
                throw new IllegalArgumentException("One exact cut per family context required");
            cut.claimContexts(contexts,work);
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
    /** A shorter proof with the same whole source set may replace a carried one. */
    static boolean retains(Packet input,Packet output,Runnable work) {
        for (Item item : input.items) {
            Item found = null;
            for (Item candidate : output.items) { work.run(); if (item.atom.same(candidate.atom,work)) { found = candidate; break; } }
            if (found == null) return false;
            for (Support proof : item.proofs) {
                boolean present = false;
                for (Support candidate : found.proofs) { work.run(); if (proof.sources.equals(candidate.sources) && candidate.depth <= proof.depth) { present = true; break; } }
                if (!present) return false;
            }
        }
        return true;
    }
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
    /** Only the fixed counter factory is exposed to the bounded validation owner. */
    static Runnable validationBudget(int maximumWork){return budget(maximumWork);}
    private static Runnable budget(int maximumWork) {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded transport required");
        int[] remaining = {maximumWork};
        return () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness transport work limit"); };
    }
    private static boolean usesX(Support support) { return support.sources.stream().anyMatch(s -> s.localX); }
    /** Sound primary seeds from a completely native-bound X snapshot; no artificial productive step. */
    static Result primarySeeds(ContextProductionNativeBinding.Snapshot snapshot,int maximumWork) {
        Runnable work = budget(maximumWork);
        try {
            if (!snapshot.inventory.localX) throw new IllegalStateException("Primary seed snapshot must be local X");
            List<Item> items = new ArrayList<>(); Set<Origin> rank = new HashSet<>();
            for (ContextProductionNativeBinding.Entry entry : snapshot.primary) {
                work.run(); Support proof = new Support(Collections.singleton(entry.source),0);
                if (entry.bundle != null) {
                    for (ContextWitnessBundle.Atom atom : entry.bundle.atoms) {
                        List<Slot> arguments = new ArrayList<>();
                        for (ContextWitnessBundle.Slot slot : atom.arguments) {
                            work.run(); Origin origin = slot.witness < 0 ? null : new Origin(entry.source,slot.witness);
                            if (origin != null) rank.add(origin); arguments.add(new Slot(origin,slot.value));
                        }
                        add(items,new Atom(atom.predicate,atom.positive,arguments),proof,work);
                    }
                } else for (Clause clause : entry.clauses) {
                    work.run(); if (!clause.variables.isEmpty() || clause.literals.size() != 1) continue;
                    ContextProductionCapture.Atom atom = clause.literals.get(0); List<Slot> arguments = new ArrayList<>();
                    for (ContextProductionCapture.Slot slot : atom.arguments) { work.run(); arguments.add(new Slot(null,slot.value)); }
                    add(items,new Atom(atom.predicate,atom.positive,arguments),proof,work);
                }
            }
            if (items.isEmpty()) throw new IllegalStateException("No supported primary X literal seeds");
            if (rank.size() > 8) throw new IllegalStateException("Primary X seed witness rank limit");
            parentX(items,work); return new Result(true,"Whole primary X witness and ground seeds with original depth0 sources",new Packet(items));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
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
    static Result exportDisjointEmpty(Packet input,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        ContextWitnessDisjointCoverage.Result checked = ContextWitnessDisjointCoverage.inspect(
                input,cut,declarations,maximumDepth,maximumWork,maximumEvents);
        if (!checked.covered) return new Result(false,checked.reason,null);
        return new Result(true,checked.reason,new Packet(input.items,input.identity,checked.sources,false,cut));
    }
    static Result exportDormantEmpty(Packet input,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        ContextWitnessDormantCoverage.Result checked = ContextWitnessDormantCoverage.inspect(
                input,cut,declarations,maximumDepth,maximumWork,maximumEvents);
        if (!checked.covered) return new Result(false,checked.reason,null);
        return new Result(true,checked.reason,new Packet(input.items,input.identity,checked.sources,false,cut));
    }
    /** Sound observed proofs plus exhaustive positive Horn proof-pool closure, bound to the owning inputs. */
    static Result exportCoveredNext(ContextWitnessSeedReplay round,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        return exportVerifiedNext(round,cut,declarations,maximumDepth,maximumWork,maximumEvents,false);
    }
    /** Full coverage of the exact immutable round input, without claiming output saturation. */
    static Result exportRoundNext(ContextWitnessSeedReplay round,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        return exportRoundNext(round,cut,declarations,maximumDepth,maximumWork,maximumEvents,null);
    }
    static Result exportRoundNext(ContextWitnessSeedReplay round,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents,ContextWitnessEmptyTarget emptyTarget) {
        return exportVerifiedNext(round,cut,declarations,maximumDepth,maximumWork,maximumEvents,true,emptyTarget);
    }
    private static Result exportVerifiedNext(ContextWitnessSeedReplay round,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents,boolean roundStep) {
        return exportVerifiedNext(round,cut,declarations,maximumDepth,maximumWork,maximumEvents,roundStep,null);
    }
    private static Result exportVerifiedNext(ContextWitnessSeedReplay round,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents,boolean roundStep,ContextWitnessEmptyTarget emptyTarget) {
        if(round.hasPrivateSeeds())return new Result(false,"Private local replay has no family coverage authority",null);
        try {round.requireTarget(cut);}catch(IllegalStateException failure){return new Result(false,failure.getMessage(),null);}
        Result observed = exportNext(round,maximumDepth,maximumWork);
        if (!observed.exported) return observed;
        Runnable work = budget(maximumWork);
        try {
            List<Input> definitions = ContextWitnessDeclarationCut.capture(round.input,cut,declarations,work,maximumWork,maximumEvents,emptyTarget);
            ContextWitnessHornClosure.sameDeclarations(definitions,round.producerInputs(work),work);
            int obligations = roundStep ? ContextWitnessHornClosure.verifyRound(round.input,observed.packet,definitions,maximumDepth,work)
                    : ContextWitnessHornClosure.verify(observed.packet,definitions,maximumDepth,work);
            return new Result(true,(roundStep ? "Input-enabled positive Horn round covered; obligations=" : "Observed positive Horn proof pool closed; obligations=")+obligations,
                    new Packet(observed.packet.items,round.inputIdentity,observed.packet.producers,true,null,roundStep ? null : cut,roundStep ? cut : null,emptyTarget));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
    /** An already closed sound input needs no new native event, even when rule bodies are enabled. */
    static Result exportHornUnchanged(Packet input,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents) {
        return exportHornUnchanged(input,cut,declarations,maximumDepth,maximumWork,maximumEvents,null);
    }
    static Result exportHornUnchanged(Packet input,Cut cut,List<ContextProductionInputs.Declaration> declarations,
            int maximumDepth,int maximumWork,int maximumEvents,ContextWitnessEmptyTarget emptyTarget) {
        Runnable work = budget(maximumWork);
        try {
            if (maximumDepth < 0) throw new IllegalArgumentException("Bounded Horn unchanged proof required");
            List<Input> definitions = ContextWitnessDeclarationCut.capture(input,cut,declarations,work,maximumWork,maximumEvents,emptyTarget);
            int obligations = ContextWitnessHornClosure.verify(input,definitions,maximumDepth,work);
            Set<Source> sources = new LinkedHashSet<>();
            for (Input definition : definitions) { work.run(); sources.add(definition.source); }
            return new Result(true,"Unchanged positive Horn proof pool closed; obligations="+obligations,
                    new Packet(input.items,input.identity,sources,false,cut,cut,null,emptyTarget));
        } catch (Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
    private static Result pack(List<Item> items,Map<Root,Origin> origins,UUID namespace,List<Route> routes,Runnable work) {
        boolean product = false;
        for (Route route : routes) {
            work.run(); if (route.proof.depth == 0 || !usesX(route.proof)) continue;
            if (!route.replay.equals(namespace) || !Objects.equals(route.physical.root,route.origin))
                throw new IllegalStateException("Non-primary carrier cannot be transported");
            Origin origin = origins.get(route.origin);
            if (origin == null && route.origin != null) throw new IllegalStateException("Unbound transport origin");
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
    /** Explicit data-only signed seed adapter. Not a PrivatePool, STEP or admission receipt. */
    static final class SignedSeedFrame {
        final ContextSignedWitnessPlanAudit.Receipt plan;
        final int maximumRank;
        final Packet common;
        final Map<Cut,Packet> locals;
        private SignedSeedFrame(ContextSignedWitnessPlanAudit.Receipt plan,int maximumRank,Packet common,Map<Cut,Packet> locals){this.plan=plan;this.maximumRank=maximumRank;this.common=common;this.locals=Collections.unmodifiableMap(new LinkedHashMap<>(locals));}
    }
    static final class SignedSeedResult {
        final boolean assembled;final String phase,reason;final SignedSeedFrame output;
        private SignedSeedResult(boolean assembled,String phase,String reason,SignedSeedFrame output){this.assembled=assembled;this.phase=phase;this.reason=reason;this.output=output;}
    }
    private static void signedSeedShape(Atom atom,int maximumRank,Set<Origin> rank,Runnable work){
        work.run();if(atom.arguments.size()>8)throw new IllegalStateException("Signed native seed arity limit");Origin root=null;
        for(Slot slot:atom.arguments){work.run();if(slot.origin!=null){if(root!=null&&!root.equals(slot.origin))throw new IllegalStateException("Multiple Origins in one native seed literal");root=slot.origin;rank.add(root);}else if(slot.value==null)throw new IllegalStateException("Missing ordinary native seed value");}
        if(rank.size()>maximumRank)throw new IllegalStateException("Signed native seed rank limit");
    }
    private static void signedPlanSeeds(ContextSignedWitnessPlan.Row row,List<Item> items,int maximumRank,Set<Origin> rank,Runnable work){
        if(row.original.bundle!=null){
            for(ContextSignedWitnessPlan.Seed seed:row.seeds){work.run();List<Slot> terms=new ArrayList<>();for(ContextSignedWitnessPlan.Term term:seed.arguments){work.run();terms.add(new Slot(term.origin==null?null:new Origin(term.origin.source,term.origin.binder),term.value));}Atom atom=new Atom(seed.predicate,seed.positive,terms);signedSeedShape(atom,maximumRank,rank,work);add(items,atom,seed.proof,work);}
        }else for(Clause clause:row.clauses){
            work.run();if(!clause.variables.isEmpty()||clause.literals.size()!=1)continue;ContextProductionCapture.Atom literal=clause.literals.get(0);List<Slot> terms=new ArrayList<>();for(ContextProductionCapture.Slot term:literal.arguments){work.run();if(term.variable>=0||term.value==null)throw new IllegalStateException("Ground native primary seed required");terms.add(new Slot(null,term.value));}Atom atom=new Atom(literal.predicate,literal.positive,terms);signedSeedShape(atom,maximumRank,rank,work);add(items,atom,new Support(Collections.singleton(row.original.source),0),work);
        }
    }
    static SignedSeedResult signedWitnessFrame(ContextSignedWitnessPlanAudit.Receipt plan,ContextProductionNativeBinding.Snapshot x,List<ContextProductionNativeBinding.Snapshot> foreign,int maximumRows,int maximumRank,int maximumWork,int auditWork){
        if(maximumRank<1||maximumRank>8||maximumWork<1||auditWork<1)throw new IllegalArgumentException("Explicit bounded signed seed frame required");String phase="plan-audit";
        try{
            ContextSignedWitnessPlanAudit.check(plan,x,foreign,maximumRows,auditWork);Runnable work=budget(maximumWork);List<Item> shared=new ArrayList<>();Set<Origin> commonRank=new HashSet<>();phase="common";
            for(ContextSignedWitnessPlan.Row row:plan.plan.rows)if(row.cut==x){work.run();signedPlanSeeds(row,shared,maximumRank,commonRank,work);}
            Packet common=new Packet(shared);if(!parentX(common.items,work).equals(Cut.of(x)))throw new IllegalStateException("Signed common has different actual X");phase="private";Map<Cut,Packet> locals=new LinkedHashMap<>();
            for(ContextProductionNativeBinding.Snapshot target:plan.foreign){work.run();List<Item> local=new ArrayList<>(common.items);Set<Origin> rank=new HashSet<>(commonRank);for(ContextSignedWitnessPlan.Row row:plan.plan.rows)if(row.cut==target){work.run();signedPlanSeeds(row,local,maximumRank,rank,work);}if(locals.put(Cut.of(target),new Packet(local))!=null)throw new IllegalStateException("Repeated signed seed cut");}
            phase="final-plan-audit";ContextSignedWitnessPlanAudit.check(plan,x,foreign,maximumRows,auditWork);return new SignedSeedResult(true,"assembled","Original signed primary seeds; no native inference/coverage/admission",new SignedSeedFrame(plan,maximumRank,common,locals));
        }catch(Exception failure){return new SignedSeedResult(false,phase,failure.toString(),null);}
    }

    /** Initial-only signed receiver pool. Old factories/rebases remain unchanged. */
    static PrivatePool scopedSignedPool(ContextSignedWitnessFrameAudit.Receipt receipt,ContextProductionNativeBinding.Snapshot target,int maximumWork) {
        if(receipt==null||target==null)throw new IllegalArgumentException("Actual audited private receiver required");SignedSeedFrame f=receipt.frame;
        ContextSignedWitnessFrameAudit.check(receipt,f.plan,f.plan.x,f.plan.foreign,f.plan.maximumRows,f.maximumRank,maximumWork,maximumWork);
        if(!f.plan.foreign.contains(target)||!f.locals.containsKey(Cut.of(target)))throw new IllegalStateException("Signed pool outside exact original receiver");Runnable work=budget(maximumWork);Set<Source> sources=new LinkedHashSet<>();for(Item item:f.locals.get(Cut.of(target)).items)for(Support proof:item.proofs)for(Source s:proof.sources){work.run();if(!s.localX)sources.add(s);}
        List<ContextProductionInputs.Declaration> declarations=new ArrayList<>();for(ContextProductionNativeBinding.Snapshot s:Arrays.asList(f.plan.x,target))for(ContextProductionNativeBinding.Entry e:s.primary){work.run();if(e.bundle==null)declarations.add(new ContextProductionInputs.Declaration(e.source,e.statement));}
        return new PrivatePool(f.common,f.plan.x,target,f.locals.get(Cut.of(target)),sources,declarations,receipt);
    }

}
