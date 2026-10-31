/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalNativeRows.*;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalInputResiduals.Literal;
import org.kanger.ContextWitnessResultTransport.*;

/** Whole-row rigid-Origin producer/constraint projection; not native/family qualification. */
final class ContextUniversalFrozenConstraints {
    static final class Proof {
        /** Null only for a successfully checked empty resolvent. */
        final Residual clause;
        final Support support;
        final Row original;
        final Part part;
        final Item input;
        final Support inputProof;
        final Proof unit,horn;
        final int pivot;
        final ContextUniversalSignedUnitExchange.Unit exchanged;
        final ContextUniversalCompoundExchange.ResidualProof compound;
        final ContextUniversalGroupedCompletion.Receipt grouped;
        final List<Integer> groupedConclusions;
        final Map<Integer,Proof> groupedDonors;
        final Map<Integer,Residual> groupedInstances;
        final Proof producer;final List<Proof> donors;final int conclusion;final boolean symbolicHeadRecipe,groundedMixedRecipe,instantiatedUnitRecipe;final List<Residual> instantiatedDonors;
        private Proof(Row row,Part part,Runnable work){grouped=null;groupedConclusions=Collections.emptyList();groupedDonors=Collections.emptyMap();groupedInstances=Collections.emptyMap();instantiatedUnitRecipe=false;groundedMixedRecipe=false;instantiatedDonors=Collections.emptyList();symbolicHeadRecipe=false;conclusion=-1;producer=null;donors=Collections.emptyList();compound=null;exchanged=null;original=row;this.part=part;clause=ContextUniversalInputResiduals.schema(part.clause,work);support=new Support(Collections.singleton(row.original.source),0);input=null;inputProof=null;unit=null;horn=null;pivot=-1;}
        private Proof(Item input,Support support,Runnable work){grouped=null;groupedConclusions=Collections.emptyList();groupedDonors=Collections.emptyMap();groupedInstances=Collections.emptyMap();instantiatedUnitRecipe=false;groundedMixedRecipe=false;instantiatedDonors=Collections.emptyList();symbolicHeadRecipe=false;conclusion=-1;producer=null;donors=Collections.emptyList();compound=null;exchanged=null;this.input=input;inputProof=support;clause=ContextUniversalInputResiduals.fact(input,work);this.support=support;original=null;part=null;unit=null;horn=null;pivot=-1;}
        private Proof(Residual clause,Proof unit,Proof horn,int pivot,Runnable work){grouped=null;groupedConclusions=Collections.emptyList();groupedDonors=Collections.emptyMap();groupedInstances=Collections.emptyMap();instantiatedUnitRecipe=false;groundedMixedRecipe=false;instantiatedDonors=Collections.emptyList();symbolicHeadRecipe=false;conclusion=-1;producer=null;donors=Collections.emptyList();compound=null;exchanged=null;this.clause=clause;this.unit=unit;this.horn=horn;this.pivot=pivot;original=null;part=null;input=null;inputProof=null;Set<Source> sources=new LinkedHashSet<>();for(Source source:unit.support.sources){work.run();sources.add(source);}for(Source source:horn.support.sources){work.run();sources.add(source);}int depth=Math.max(unit.support.depth,horn.support.depth);if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Constraint proof depth overflow");support=new Support(sources,depth+1);}
        private Proof(ContextUniversalSignedUnitExchange.Unit unit){grouped=null;groupedConclusions=Collections.emptyList();groupedDonors=Collections.emptyMap();groupedInstances=Collections.emptyMap();instantiatedUnitRecipe=false;groundedMixedRecipe=false;instantiatedDonors=Collections.emptyList();symbolicHeadRecipe=false;conclusion=-1;producer=null;donors=Collections.emptyList();compound=null;exchanged=unit;clause=unit.clause;support=unit.support;original=null;part=null;input=null;inputProof=null;this.unit=null;horn=null;pivot=-1;}
        private Proof(ContextUniversalCompoundExchange.ResidualProof compound){grouped=null;groupedConclusions=Collections.emptyList();groupedDonors=Collections.emptyMap();groupedInstances=Collections.emptyMap();instantiatedUnitRecipe=false;groundedMixedRecipe=false;instantiatedDonors=Collections.emptyList();symbolicHeadRecipe=false;conclusion=-1;producer=null;donors=Collections.emptyList();exchanged=null;this.compound=compound;clause=compound.clause;support=compound.support;original=null;part=null;input=null;inputProof=null;unit=null;horn=null;pivot=-1;}
        private Proof(Residual clause,Proof producer,List<Proof> donors,Runnable work){this(clause,producer,donors,-1,work);}
        private Proof(Residual clause,Proof producer,List<Proof> donors,int conclusion,Runnable work){this(clause,producer,donors,conclusion,false,work);}
        private Proof(Residual clause,Proof producer,List<Proof> donors,int conclusion,boolean symbolicHeadRecipe,Runnable work){this(clause,producer,donors,conclusion,symbolicHeadRecipe,false,Collections.emptyList(),work);}
        private Proof(Residual clause,Proof producer,List<Proof> donors,int conclusion,boolean symbolicHeadRecipe,boolean groundedMixedRecipe,List<Residual> instances,Runnable work){this(clause,producer,donors,conclusion,symbolicHeadRecipe,groundedMixedRecipe,false,instances,work);}
        private Proof(Residual clause,Proof producer,List<Proof> donors,int conclusion,boolean symbolicHeadRecipe,boolean groundedMixedRecipe,boolean instantiatedUnitRecipe,List<Residual> instances,Runnable work){grouped=null;groupedConclusions=Collections.emptyList();groupedDonors=Collections.emptyMap();groupedInstances=Collections.emptyMap();this.instantiatedUnitRecipe=instantiatedUnitRecipe;this.groundedMixedRecipe=groundedMixedRecipe;instantiatedDonors=Collections.unmodifiableList(new ArrayList<>(instances));this.symbolicHeadRecipe=symbolicHeadRecipe;this.conclusion=conclusion;this.clause=clause;this.producer=producer;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));compound=null;exchanged=null;original=null;part=null;input=null;inputProof=null;unit=null;horn=null;pivot=-1;Set<Source> sources=new LinkedHashSet<>(producer.support.sources);int depth=producer.support.depth;for(Proof donor:donors){work.run();depth=Math.max(depth,donor.support.depth);for(Source source:donor.support.sources){work.run();sources.add(source);}}if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Application depth overflow");support=new Support(sources,depth+1);}
        private Proof(ContextUniversalGroupedCompletion.Receipt receipt){grouped=receipt;groupedConclusions=receipt.conclusionSlots;groupedDonors=receipt.donors;groupedInstances=receipt.instances;clause=receipt.clause;support=receipt.support;producer=receipt.history.producer;donors=Collections.unmodifiableList(new ArrayList<>(receipt.donors.values()));conclusion=-1;symbolicHeadRecipe=false;groundedMixedRecipe=false;instantiatedUnitRecipe=false;instantiatedDonors=Collections.emptyList();original=null;part=null;input=null;inputProof=null;unit=null;horn=null;pivot=-1;exchanged=null;compound=null;}
        /** Only used after authenticated rejection export; carries no owning receipt/frame. */
        private Proof(Residual clause,Proof producer,List<Integer> heads,Map<Integer,Proof> parents,Map<Integer,Residual> instances,Runnable work){grouped=null;groupedConclusions=Collections.unmodifiableList(new ArrayList<>(heads));groupedDonors=Collections.unmodifiableMap(new LinkedHashMap<>(parents));groupedInstances=Collections.unmodifiableMap(new LinkedHashMap<>(instances));this.clause=clause;this.producer=producer;donors=Collections.unmodifiableList(new ArrayList<>(parents.values()));conclusion=-1;symbolicHeadRecipe=false;groundedMixedRecipe=false;instantiatedUnitRecipe=false;instantiatedDonors=Collections.emptyList();original=null;part=null;input=null;inputProof=null;unit=null;horn=null;pivot=-1;exchanged=null;compound=null;Map<Integer,Residual> clauses=new LinkedHashMap<>();Set<Source> sources=new LinkedHashSet<>(producer.support.sources);int grade=producer.support.depth;for(Map.Entry<Integer,Proof> e:parents.entrySet()){work.run();clauses.put(e.getKey(),e.getValue().clause);grade=Math.max(grade,e.getValue().support.depth);for(Source source:e.getValue().support.sources){work.run();sources.add(source);}}if(grade==Integer.MAX_VALUE)throw new IllegalStateException("Grouped rejection grade overflow");support=new Support(sources,grade+1);ContextUniversalInputResiduals.GroupedApplication binding=ContextUniversalInputResiduals.applyGroupedUnits(producer.clause,new LinkedHashSet<>(heads),clauses,work);if(binding==null||!ContextUniversalInputResiduals.sameOrdered(binding.conclusion,clause,work)||!binding.instances.keySet().equals(instances.keySet()))throw new IllegalStateException("Different flattened grouped application");for(int slot:instances.keySet()){work.run();if(!ContextUniversalInputResiduals.sameOrdered(binding.instances.get(slot),instances.get(slot),work))throw new IllegalStateException("Different flattened grouped instance");}}
    }
    static final class Result {
        final boolean closed,xRefuted,foreignRefuted,signedUnits,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits;
        final String reason;
        final ContextUniversalNativeRows.Result input;
        final Packet common,local;
        final PrivatePool pool;
        final ContextUniversalSignedUnitExchange.Exchange exchange;
        final ContextUniversalCompoundExchange.Exchange compounds;
        final Result groupedBase;final List<ContextUniversalGroupedCompletion.Receipt> groupedInputs;
        final int rounds,obligations;
        final List<Proof> proofs,emptyProofs;
        private Result(boolean closed,String reason,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,boolean signedUnits,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,int rounds,int obligations,List<Proof> proofs) {this(closed,reason,input,common,pool,exchange,null,signedUnits,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,rounds,obligations,proofs);}
        private Result(boolean closed,String reason,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,boolean signedUnits,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,int rounds,int obligations,List<Proof> proofs) {this(closed,reason,input,common,pool,exchange,compounds,null,Collections.emptyList(),signedUnits,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,rounds,obligations,proofs);}
        private Result(boolean closed,String reason,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,Result groupedBase,List<ContextUniversalGroupedCompletion.Receipt> groupedInputs,boolean signedUnits,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,int rounds,int obligations,List<Proof> proofs) {
            this.groupedBase=groupedBase;this.groupedInputs=Collections.unmodifiableList(new ArrayList<>(groupedInputs));this.compounds=compounds;this.instantiatedUnits=instantiatedUnits;this.groundedMixed=groundedMixed;this.symbolicHeads=symbolicHeads;this.signedApplications=signedApplications;this.applications=applications;this.exchange=exchange;this.closed=closed;this.signedUnits=signedUnits;this.reason=reason;this.input=input;this.common=common;this.pool=pool;local=pool==null?common:pool.local;this.rounds=rounds;this.obligations=obligations;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));List<Proof> empty=new ArrayList<>();boolean x=false,foreign=false;
            for(Proof proof:proofs)if(proof.clause==null){empty.add(proof);if(proof.support.sources.stream().anyMatch(s->s.localX))x=true;else foreign=true;}
            emptyProofs=Collections.unmodifiableList(empty);xRefuted=x;foreignRefuted=foreign;
        }
    }
    private static boolean same(Residual a,Residual b,Runnable work){return a==null?b==null:b!=null&&a.equivalent(b,work);}
    private static void shape(Residual clause,Runnable work) {
        if(clause==null)return;
        if(clause.literals.isEmpty()||clause.literals.size()>8||clause.variables.size()>3||clause.origins.size()>8)throw new IllegalStateException("Outside bounded rigid-Origin Horn shape");
        int heads=0;for(Literal literal:clause.literals){work.run();if(literal.positive)++heads;if(literal.arguments.size()>8)throw new IllegalStateException("Rigid-Origin Horn arity limit");}
        if(heads>1)throw new IllegalStateException("Horn producer or headless constraint required");
    }
    private static boolean unit(Proof p,boolean signed){return p.clause!=null&&p.clause.literals.size()==1&&(signed||p.clause.literals.get(0).positive);}
    private static final class Bucket {
        final Residual clause;final Map<Set<Source>,Proof> proofs=new LinkedHashMap<>();Bucket(Residual clause){this.clause=clause;}
    }
    private static final class Ledger {
        final List<Bucket> buckets=new ArrayList<>();final int clauses,alternatives;
        Ledger(int clauses,int alternatives){this.clauses=clauses;this.alternatives=alternatives;}
        boolean add(Proof proof,int depth,Runnable work) {
            shape(proof.clause,work);Bucket found=null;for(Bucket b:buckets){work.run();if(same(b.clause,proof.clause,work)){found=b;break;}}
            Proof old=found==null?null:found.proofs.get(proof.support.sources);
            if(old!=null&&old.support.depth<=proof.support.depth)return false;
            if(proof.support.depth>depth)return false; // Final pair audit must still find a shorter exact-Source cover.
            if(found==null){if(buckets.size()==clauses)throw new IllegalStateException("Constraint clause limit");found=new Bucket(proof.clause);buckets.add(found);}
            if(old==null&&found.proofs.size()==alternatives)throw new IllegalStateException("Constraint Source alternative limit");
            found.proofs.put(proof.support.sources,proof);return true;
        }
        List<Proof> all(Runnable work){List<Proof> out=new ArrayList<>();for(Bucket b:buckets)for(Proof p:b.proofs.values()){work.run();out.add(p);}return out;}
        Ledger copy(Runnable work){Ledger next=new Ledger(clauses,alternatives);for(Bucket b:buckets){work.run();Bucket copy=new Bucket(b.clause);copy.proofs.putAll(b.proofs);next.buckets.add(copy);}return next;}
    }
    private static void verify(Proof p,ContextUniversalNativeRows.Result input,Packet local,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,Result groupedBase,List<ContextUniversalGroupedCompletion.Receipt> groupedInputs,boolean signed,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,Set<Proof> checked,int depth,Runnable work) {
        work.run();if(checked.contains(p))return;shape(p.clause,work);if(p.support.depth>depth)throw new IllegalStateException("Constraint proof exceeds depth bound");
        if(p.exchanged!=null) {
            if(!signed||exchange==null||!exchange.units.contains(p.exchanged)||p.clause!=p.exchanged.clause||p.support!=p.exchanged.support)throw new IllegalStateException("Different actual symbolic unit input");
        }else if(p.compound!=null) {
            if(!signed||compounds==null||!compounds.clauses.contains(p.compound)||p.clause!=p.compound.clause||p.support!=p.compound.support)throw new IllegalStateException("Different actual compound input");
        }else if(p.original!=null) {
            boolean found=false;for(Row row:input.rows){work.run();if(row==p.original)found=true;}
            if(!found||!p.original.parts.contains(p.part)||!p.clause.equivalent(ContextUniversalInputResiduals.schema(p.part.clause,work),work)||p.part.role!=Role.PRODUCER&&p.part.role!=Role.CONSTRAINT||p.support.depth!=0||!p.support.sources.equals(Collections.singleton(p.original.original.source)))throw new IllegalStateException("Different original constraint/producer root");
        }else if(p.input!=null) {
            boolean found=false;for(Item item:local.items){work.run();if(item==p.input)found=true;}
            if(!found||!p.input.proofs.contains(p.inputProof)||!p.support.equals(p.inputProof)||!p.clause.equivalent(ContextUniversalInputResiduals.fact(p.input,work),work))throw new IllegalStateException("Different actual carried input proof");
        }else if(p.grouped!=null||!p.groupedConclusions.isEmpty()) {
            if(!signed||!applications||!signedApplications||!instantiatedUnits||groupedBase==null||p.grouped==null||!groupedInputs.contains(p.grouped)||p.grouped.history.receiver!=groupedBase)throw new IllegalStateException("Explicit actual grouped seed required");
            ContextUniversalGroupedCompletion.check(p.grouped,depth,work);
            if(p.producer!=p.grouped.history.producer||!p.groupedConclusions.equals(p.grouped.conclusionSlots)||!p.groupedDonors.equals(p.grouped.donors)||!p.groupedInstances.equals(p.grouped.instances)||p.clause!=p.grouped.clause||p.support!=p.grouped.support||!p.donors.equals(new ArrayList<>(p.grouped.donors.values())))throw new IllegalStateException("Different actual grouped kernel parents/instances/Source/grade");
        }else if(p.producer!=null) {
            if(!applications||!signed)throw new IllegalStateException("Explicit positive application mode required");
            verify(p.producer,input,local,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,checked,depth,work);List<Residual> clauses=new ArrayList<>();for(Proof donor:p.donors){work.run();verify(donor,input,local,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,checked,depth,work);clauses.add(donor.clause);}
            if(p.conclusion>=0&&!signedApplications)throw new IllegalStateException("Explicit signed application mode required");
            if(p.symbolicHeadRecipe&&(!symbolicHeads||p.conclusion<0))throw new IllegalStateException("Explicit symbolic-head application mode required");
            if(p.groundedMixedRecipe&&(!groundedMixed||p.symbolicHeadRecipe||p.instantiatedUnitRecipe||p.conclusion<0))throw new IllegalStateException("Explicit grounded mixed application mode required");
            if(p.instantiatedUnitRecipe&&(!instantiatedUnits||p.symbolicHeadRecipe||p.groundedMixedRecipe||p.conclusion<0))throw new IllegalStateException("Explicit instantiated-unit application mode required");
            ContextUniversalInputResiduals.GroundedApplication mixed=p.instantiatedUnitRecipe?ContextUniversalInputResiduals.applyInstantiatedUnits(p.producer.clause,p.conclusion,clauses,work):p.groundedMixedRecipe?ContextUniversalInputResiduals.applyGroundedMixedUnits(p.producer.clause,p.conclusion,clauses,work):null;
            if(p.groundedMixedRecipe||p.instantiatedUnitRecipe){if(mixed==null||mixed.instances.size()!=p.instantiatedDonors.size())throw new IllegalStateException("Missing actual grounded donor instances");for(int i=0;i<mixed.instances.size();++i){work.run();if(!same(mixed.instances.get(i),p.instantiatedDonors.get(i),work))throw new IllegalStateException("Different grounded donor instance");}}
            else if(!p.instantiatedDonors.isEmpty())throw new IllegalStateException("Unexpected donor instances");
            Residual expected=p.groundedMixedRecipe||p.instantiatedUnitRecipe?(mixed==null?null:mixed.conclusion):p.symbolicHeadRecipe?ContextUniversalInputResiduals.applySymbolicHeadFixedUnits(p.producer.clause,p.conclusion,clauses,work):p.conclusion<0?ContextUniversalInputResiduals.applyFixedPositiveUnits(p.producer.clause,clauses,work):ContextUniversalInputResiduals.applyFixedSignedUnits(p.producer.clause,p.conclusion,clauses,work);Proof label=new Proof(expected,p.producer,p.donors,p.conclusion,p.symbolicHeadRecipe,p.groundedMixedRecipe,p.instantiatedUnitRecipe,p.instantiatedDonors,work);
            if(expected==null||!same(expected,p.clause,work)||!label.support.equals(p.support))throw new IllegalStateException("Different positive hyperapplication/Source proof");
        }else {
            if(p.unit==null||p.horn==null||!unit(p.unit,signed))throw new IllegalStateException("Missing constraint resolution parents");verify(p.unit,input,local,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,checked,depth,work);verify(p.horn,input,local,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,checked,depth,work);
            Residual expected=resolve(p.unit,p.horn,p.pivot,signed,work);Proof label=new Proof(expected,p.unit,p.horn,p.pivot,work);
            if(!same(expected,p.clause,work)||!label.support.equals(p.support))throw new IllegalStateException("Different constraint resolvent/Source proof");
        }
        checked.add(p);
    }
    static void check(Result result,int maximumDepth,Runnable work) {
        ContextUniversalValidationSession.Token session=ContextUniversalValidationSession.begin(result,maximumDepth,work);if(session!=null&&session.reused)return;boolean valid=false;
        try {
            if(result==null||!result.closed||result.input==null||!result.input.classified||!result.input.withinShape)throw new IllegalStateException("Closed whole rigid-Origin constraint frame required");
            if(result.input.signedSeeds!=null&&!result.signedUnits)throw new IllegalStateException("Explicit signed closure profile required");
            if(result.input.signedContinuation!=null)ContextSignedWitnessContinuationScope.authenticate(result.input.signedContinuation,result.input,result.common,result.pool,result.exchange,result.compounds,work);
            frame(result.input,result.common,result.pool,maximumDepth,Integer.MAX_VALUE,work);
            if(result.exchange!=null){if(!result.signedUnits)throw new IllegalStateException("Signed symbolic input required");ContextUniversalSignedUnitExchange.check(result.exchange,result.input.x,result.common,maximumDepth,work);}
            if(result.compounds!=null){if(!result.signedUnits)throw new IllegalStateException("Signed compound input required");compoundFrame(result.compounds,result.input,result.common,maximumDepth,work);}
            groupedFrame(result.groupedBase,result.groupedInputs,result.input,result.common,result.pool,result.exchange,result.compounds,work);
            Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof proof:result.proofs)verify(proof,result.input,result.local,result.exchange,result.compounds,result.groupedBase,result.groupedInputs,result.signedUnits,result.applications,result.signedApplications,result.symbolicHeads,result.groundedMixed,result.instantiatedUnits,checked,maximumDepth,work);
            groupedCoverage(result.groupedInputs,result.proofs,work);
            valid=true;
        }finally{ContextUniversalValidationSession.finish(session,valid);}
    }
    private static void covered(Proof expected,List<Proof> proofs,Runnable work) {
        for(Proof proof:proofs){work.run();if(same(expected.clause,proof.clause,work)&&expected.support.sources.equals(proof.support.sources)&&proof.support.depth<=expected.support.depth)return;}
        throw new IllegalStateException("Uncovered authenticated input/ordinary closure obligation");
    }
    /** Replay root inventory and ordinary closure obligations, not just parent authentication. */
    static void checkSaturation(Result result,int maximumDepth,Runnable work) {
        check(result,maximumDepth,work);
        for(Row row:result.input.rows)for(Part part:row.parts){work.run();if(part.role!=Role.WITNESS)covered(new Proof(row,part,work),result.proofs,work);}
        for(Item item:result.local.items)for(Support support:item.proofs){work.run();covered(new Proof(item,support,work),result.proofs,work);}
        if(result.exchange!=null)for(ContextUniversalSignedUnitExchange.Unit unit:result.exchange.units){work.run();covered(new Proof(unit),result.proofs,work);}
        if(result.compounds!=null)for(ContextUniversalCompoundExchange.ResidualProof compound:result.compounds.clauses){work.run();covered(new Proof(compound),result.proofs,work);}
        steps(result.proofs,result.signedUnits,result.applications,result.signedApplications,result.symbolicHeads,result.groundedMixed,result.instantiatedUnits,expected->covered(expected,result.proofs,work),work);
    }
    /** Flatten checked import receipts into rejection DAGs; no owning intermediate Result/Packet escapes. */
    private static Proof rejection(Proof proof,Map<Proof,Proof> copied,Runnable work) {
        work.run();Proof prior=copied.get(proof);if(prior!=null)return prior;Proof out=proof;
        if(proof.exchanged!=null)out=rejection(proof.exchanged.proof,copied,work);
        else if(proof.compound!=null)out=rejection(proof.compound.proof,copied,work);
        else if(proof.grouped!=null||!proof.groupedConclusions.isEmpty()){Map<Integer,Proof> parents=new LinkedHashMap<>();for(Map.Entry<Integer,Proof> e:proof.groupedDonors.entrySet()){work.run();parents.put(e.getKey(),rejection(e.getValue(),copied,work));}out=new Proof(proof.clause,rejection(proof.producer,copied,work),proof.groupedConclusions,parents,proof.groupedInstances,work);}
        else if(proof.producer!=null){List<Proof> donors=new ArrayList<>();for(Proof donor:proof.donors){work.run();donors.add(rejection(donor,copied,work));}out=new Proof(proof.clause,rejection(proof.producer,copied,work),donors,proof.conclusion,proof.symbolicHeadRecipe,proof.groundedMixedRecipe,proof.instantiatedUnitRecipe,proof.instantiatedDonors,work);}
        else if(proof.unit!=null)out=new Proof(proof.clause,rejection(proof.unit,copied,work),rejection(proof.horn,copied,work),proof.pivot,work);
        if(!out.support.equals(proof.support)||!same(out.clause,proof.clause,work))throw new IllegalStateException("Different flattened rejection proof");
        copied.put(proof,out);return out;
    }
    static List<Proof> rejectionEvidence(Result result,int maximumWork) {
        if(maximumWork<1)throw new IllegalArgumentException("Bounded rejection evidence required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        if(result==null||!result.xRefuted&&!result.foreignRefuted)throw new IllegalStateException("Checked refutation required");check(result,64,work);
        Map<Proof,Proof> copied=new IdentityHashMap<>();List<Proof> out=new ArrayList<>();for(Proof proof:result.emptyProofs){work.run();out.add(rejection(proof,copied,work));}
        return Collections.unmodifiableList(out);
    }
    private static Residual resolve(Proof unit,Proof horn,int pivot,boolean signed,Runnable work) {
        return signed?ContextUniversalInputResiduals.resolveSignedUnit(unit.clause,horn.clause,pivot,work)
            :ContextUniversalInputResiduals.resolveConstraint(unit.clause,horn.clause,pivot,work);
    }
    private interface Step { void accept(Proof proof); }
    private static void steps(List<Proof> input,boolean signed,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,Step output,Runnable work) {
        for(Proof unit:input) {
            work.run();if(!unit(unit,signed))continue;
            for(Proof horn:input) {
            work.run();if(horn.clause==null)continue;Literal head=unit.clause.literals.get(0);
            for(int pivot=0;pivot<horn.clause.literals.size();++pivot) {
                work.run();Literal body=horn.clause.literals.get(pivot);if(body.positive==head.positive||!head.predicate.equals(body.predicate)||head.arguments.size()!=body.arguments.size())continue;
                Residual residual;try{residual=resolve(unit,horn,pivot,signed,work);}catch(ContextUniversalInputResiduals.UnificationFailure incompatible){continue;}
                output.accept(new Proof(residual,unit,horn,pivot,work));
            }
            }
        }
        if(applications)applications(input,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,output,work);
    }
    private static void applications(List<Proof> input,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,Step output,Runnable work) {
        for(Proof producer:input) {
            work.run();if(producer.clause==null||producer.clause.literals.size()<3)continue;if(signedApplications){for(int conclusion=0;conclusion<producer.clause.literals.size();++conclusion){work.run();List<Literal> body=new ArrayList<>();for(int i=0;i<producer.clause.literals.size();++i)if(i!=conclusion)body.add(producer.clause.literals.get(i));join(producer,body,input,0,new ArrayList<>(),conclusion,symbolicHeads,UnitRecipe.FIXED,output,work);if(groundedMixed)join(producer,body,input,0,new ArrayList<>(),conclusion,false,UnitRecipe.MIXED,output,work);if(instantiatedUnits)join(producer,body,input,0,new ArrayList<>(),conclusion,false,UnitRecipe.INSTANTIATED,output,work);}continue;}int heads=0;List<Literal> body=new ArrayList<>();
            for(Literal literal:producer.clause.literals){work.run();if(literal.positive)++heads;else body.add(literal);}if(heads!=1||body.size()<2)continue;
            join(producer,body,input,0,new ArrayList<>(),-1,false,UnitRecipe.FIXED,output,work);
        }
    }
    private enum UnitRecipe { FIXED, MIXED, INSTANTIATED }
    private static void join(Proof producer,List<Literal> body,List<Proof> input,int slot,List<Proof> chosen,int conclusion,boolean symbolicHeads,UnitRecipe recipe,Step output,Runnable work) {
        work.run();if(slot==body.size()) {
            List<Residual> clauses=new ArrayList<>();for(Proof donor:chosen){work.run();clauses.add(donor.clause);}Residual result;
            if(recipe!=UnitRecipe.FIXED){try{ContextUniversalInputResiduals.GroundedApplication binding=recipe==UnitRecipe.MIXED?ContextUniversalInputResiduals.applyGroundedMixedUnits(producer.clause,conclusion,clauses,work):ContextUniversalInputResiduals.applyInstantiatedUnits(producer.clause,conclusion,clauses,work);if(binding!=null)output.accept(new Proof(binding.conclusion,producer,chosen,conclusion,false,recipe==UnitRecipe.MIXED,recipe==UnitRecipe.INSTANTIATED,binding.instances,work));}catch(ContextUniversalInputResiduals.UnificationFailure incompatible){return;}return;}
            try{result=symbolicHeads?ContextUniversalInputResiduals.applySymbolicHeadFixedUnits(producer.clause,conclusion,clauses,work):conclusion<0?ContextUniversalInputResiduals.applyFixedPositiveUnits(producer.clause,clauses,work):ContextUniversalInputResiduals.applyFixedSignedUnits(producer.clause,conclusion,clauses,work);}catch(ContextUniversalInputResiduals.UnificationFailure incompatible){return;}
            if(result!=null)output.accept(new Proof(result,producer,chosen,conclusion,symbolicHeads,work));return;
        }
        Literal formal=body.get(slot);for(Proof donor:input){work.run();if(!unit(donor,conclusion>=0)||(recipe==UnitRecipe.FIXED&&!donor.clause.variables.isEmpty()))continue;Literal actual=donor.clause.literals.get(0);if(actual.positive==formal.positive||!actual.predicate.equals(formal.predicate)||actual.arguments.size()!=formal.arguments.size())continue;chosen.add(donor);join(producer,body,input,slot+1,chosen,conclusion,symbolicHeads,recipe,output,work);chosen.remove(chosen.size()-1);}
    }
    private static Packet frame(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int depth,int maximumWork,Runnable work) {
        if(common==null)throw new IllegalStateException("Actual common input required");
        ContextWitnessResultTransport.family(common,input.parent.equals(input.target)?Collections.singletonList(input.parent):Arrays.asList(input.parent,input.target),maximumWork);
        if(pool!=null&&(pool.common!=common||pool.x!=input.x||pool.snapshot!=input.foreign||!pool.cut.equals(input.target)))throw new IllegalStateException("Different exact private native/common frame");
        if(input.signedContinuation!=null)ContextSignedWitnessContinuationScope.frame(input.signedContinuation,input,common,pool);
        else if(input.signedSeeds!=null){SignedSeedFrame f=input.signedSeeds.frame;if(common!=f.common||input.x!=f.plan.x||input.foreign!=null&&(pool==null||pool.signedSeeds!=input.signedSeeds)||input.foreign==null&&pool!=null)throw new IllegalStateException("Different authenticated signed receiver scope");}
        else if(pool!=null&&pool.signedSeeds!=null)throw new IllegalStateException("Signed pool requires explicit signed row profile");
        Packet local=pool==null?common:pool.local;
        for(Row row:input.rows)for(Part part:row.parts) {
            work.run();if(part.role==Role.UNSUPPORTED)throw new IllegalStateException("Unsupported whole original row");
            if(part.role!=Role.WITNESS)continue;
            for(ContextWitnessBundle.Atom atom:row.original.bundle.atoms) {
                work.run();boolean found=false;
                for(Item item:local.items)if(input.signedSeeds==null?ContextUniversalFrozenCoverage.bundleAtom(atom,item,row.original.source,work):ContextUniversalFrozenCoverage.signedBundleAtom(atom,item,row.original.source,work))for(Support support:item.proofs){work.run();if(support.depth==0&&support.sources.equals(Collections.singleton(row.original.source)))found=true;}
                if(!found)throw new IllegalStateException("Missing actual full original witness row in local frame");
            }
        }
        for(Item item:local.items)for(Support support:item.proofs){work.run();if(support.depth>depth)throw new IllegalStateException("Carried input proof exceeds exploration bound");}
        return local;
    }
    static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,false,null,false,false,false,false,false);
    }
    static Result closeSigned(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,null,false,false,false,false,false);
    }
    /** Explicit symbolic input; existing positive/fixed-only entry points retain their scope. */
    static Result closeSignedUnits(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(exchange==null)throw new IllegalArgumentException("Actual symbolic exchange required");
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,false,false,false,false,false);
    }
    /** Explicit binary signed closure plus fixed positive hyperapplications; no native completeness claim. */
    static Result closeSignedApplications(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,true,false,false,false,false);
    }
    /** Explicit fixed signed applications, including negative conclusions and negative donors. */
    static Result closeSignedFixedApplications(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,true,true,false,false,false);
    }
    /** Fixed signed donors with a possibly universal conclusion; no symbolic donor instantiation. */
    static Result closeSignedSymbolicHeads(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,true,true,true,false,false);
    }
    /** Explicit grounded mixed signed donors; fixed/symbolic-head recipes retain their own scope. */
    static Result closeSignedGroundedMixed(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,true,true,true,true,false);
    }
    /** Explicit actual unit instances with fixed donor arguments and possibly universal head. */
    static Result closeSignedInstantiatedUnits(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,true,true,true,true,true);
    }
    private static void compoundFrame(ContextUniversalCompoundExchange.Exchange compounds,ContextUniversalNativeRows.Result input,Packet common,int depth,Runnable work) {
        ContextUniversalCompoundExchange.check(compounds,input.x,common,depth,work);
        Result prior=compounds.checks.get(input.target);
        if(prior==null||prior.input.foreign!=input.foreign)throw new IllegalStateException("Actual supplied compound receiver required");
    }
    /** Explicit compound imports with honest binary sequential grades; no original-producer completion. */
    static Result closeSignedCompounds(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(compounds==null)throw new IllegalArgumentException("Actual compound exchange required");
        return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,compounds,true,true,true,true,true);
    }
    static final class SessionClosure {
        final Result result;final ContextUniversalValidationSession.Metrics metrics;
        private SessionClosure(Result result,ContextUniversalValidationSession.Metrics metrics){this.result=result;this.metrics=metrics;}
    }
    /** One original kernel budget covers authentication, ledger construction and ALL semantic passes. */
    static SessionClosure closeSignedCompoundsWithValidationReuse(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,int capacity) {
        if(compounds==null)throw new IllegalArgumentException("Actual compound exchange required");
        if(maximumWork<1)throw new IllegalArgumentException("Bounded ground transport required");
        if(maximumRounds<1||maximumRounds>64||maximumDepth<0||maximumDepth>64||maximumClauses<1||maximumClauses>512||maximumAlternatives<1||maximumAlternatives>256)throw new IllegalArgumentException("Bounded ordinary constraint closure required");
        ContextUniversalValidationSession.Scope scope=ContextUniversalValidationSession.open(maximumWork,capacity);Result result;
        try{result=close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,exchange,compounds,null,Collections.emptyList(),true,true,true,true,true,scope.work);}finally{scope.close();}
        return new SessionClosure(result,scope.metrics());
    }
    private static void groupedFrame(Result base,List<ContextUniversalGroupedCompletion.Receipt> seeds,ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,ContextUniversalSignedUnitExchange.Exchange units,ContextUniversalCompoundExchange.Exchange compounds,Runnable work) {
        if(base==null){if(!seeds.isEmpty())throw new IllegalStateException("Actual grouped base required");return;}
        if(!base.closed||base.input!=input||base.common!=common||base.pool!=pool||base.exchange!=units||base.compounds!=compounds||compounds==null||seeds.size()>512)throw new IllegalStateException("Different actual grouped base/frame or seed bound");check(base,64,work);
        for(ContextUniversalGroupedCompletion.Receipt seed:seeds){work.run();if(seed==null||seed.history.receiver!=base)throw new IllegalStateException("All grouped seeds must share the actual base receiver");ContextUniversalGroupedCompletion.check(seed,64,work);}
    }
    private static void groupedCoverage(List<ContextUniversalGroupedCompletion.Receipt> seeds,List<Proof> proofs,Runnable work) {
        for(ContextUniversalGroupedCompletion.Receipt seed:seeds){work.run();boolean found=false;for(Proof proof:proofs){work.run();if(same(seed.clause,proof.clause,work)&&seed.support.sources.equals(proof.support.sources)&&proof.support.depth<=seed.support.depth){found=true;break;}}if(!found)throw new IllegalStateException("Uncovered explicit grouped seed Source/depth obligation");}
    }
    /** Old grammar plus an exact explicit seed manifest; NOT automatic grouped candidate enumeration. */
    static Result closeSignedGrouped(Result base,List<ContextUniversalGroupedCompletion.Receipt> seeds,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork) {
        if(base==null||seeds==null)throw new IllegalArgumentException("Actual grouped base and explicit seeds required");
        return close(base.input,base.common,base.pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,base.exchange,base.compounds,base,new ArrayList<>(seeds),true,true,true,true,true);
    }
    static Result closeSignedGroupedShared(Result base,List<ContextUniversalGroupedCompletion.Receipt> seeds,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,Runnable work) {
        if(base==null||seeds==null||work==null)throw new IllegalArgumentException("Actual shared-work grouped base/seeds required");
        return close(base.input,base.common,base.pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,true,base.exchange,base.compounds,base,new ArrayList<>(seeds),true,true,true,true,true,work);
    }
    private static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,boolean signed,ContextUniversalSignedUnitExchange.Exchange exchange,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits) {return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,signed,exchange,null,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits);}
    private static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,boolean signed,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits) {return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,signed,exchange,compounds,null,Collections.emptyList(),applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits);}
    private static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,boolean signed,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,Result groupedBase,List<ContextUniversalGroupedCompletion.Receipt> groupedInputs,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits) {return close(input,common,pool,maximumRounds,maximumDepth,maximumClauses,maximumAlternatives,maximumWork,signed,exchange,compounds,groupedBase,groupedInputs,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,ContextGroundProductionRounds.budget(maximumWork));}
    private static Result close(ContextUniversalNativeRows.Result input,Packet common,PrivatePool pool,int maximumRounds,int maximumDepth,int maximumClauses,int maximumAlternatives,int maximumWork,boolean signed,ContextUniversalSignedUnitExchange.Exchange exchange,ContextUniversalCompoundExchange.Exchange compounds,Result groupedBase,List<ContextUniversalGroupedCompletion.Receipt> groupedInputs,boolean applications,boolean signedApplications,boolean symbolicHeads,boolean groundedMixed,boolean instantiatedUnits,Runnable work) {
        if(maximumRounds<1||maximumRounds>64||maximumDepth<0||maximumDepth>64||maximumClauses<1||maximumClauses>512||maximumAlternatives<1||maximumAlternatives>256||maximumWork<1)throw new IllegalArgumentException("Bounded ordinary constraint closure required");
        try {
            if(input==null||!input.classified||!input.withinShape)throw new IllegalStateException("Whole supported native row inventory required");
            if(input.signedSeeds!=null&&!signed)throw new IllegalStateException("Explicit signed closure required for signed row profile");
            if(input.signedContinuation!=null)ContextSignedWitnessContinuationScope.authenticate(input.signedContinuation,input,common,pool,exchange,compounds,work);
            Packet local=frame(input,common,pool,maximumDepth,maximumWork,work);
            if(exchange!=null)ContextUniversalSignedUnitExchange.check(exchange,input.x,common,maximumDepth,work);
            if(compounds!=null)compoundFrame(compounds,input,common,maximumDepth,work);
            groupedFrame(groupedBase,groupedInputs,input,common,pool,exchange,compounds,work);
            Ledger current=new Ledger(maximumClauses,maximumAlternatives);
            for(Row row:input.rows)for(Part part:row.parts){work.run();if(part.role!=Role.WITNESS)current.add(new Proof(row,part,work),maximumDepth,work);}
            for(Item item:local.items)for(Support support:item.proofs){work.run();current.add(new Proof(item,support,work),maximumDepth,work);}
            if(exchange!=null)for(ContextUniversalSignedUnitExchange.Unit unit:exchange.units){work.run();current.add(new Proof(unit),maximumDepth,work);}
            if(compounds!=null)for(ContextUniversalCompoundExchange.ResidualProof compound:compounds.clauses){work.run();current.add(new Proof(compound),maximumDepth,work);}
            for(ContextUniversalGroupedCompletion.Receipt seed:groupedInputs){work.run();current.add(new Proof(seed),maximumDepth,work);}
            for(int round=1;round<=maximumRounds;++round) {
                List<Proof> before=current.all(work);Ledger next=current.copy(work);boolean[] changed={false};
                steps(before,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,proof->{changed[0]|=next.add(proof,maximumDepth,work);},work);
                if(!changed[0]) {
                    List<Proof> proofs=next.all(work);int[] obligations={0};
                    steps(proofs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,expected->{work.run();++obligations[0];boolean found=false;for(Proof proof:proofs){work.run();if(same(proof.clause,expected.clause,work)&&proof.support.sources.equals(expected.support.sources)&&proof.support.depth<=expected.support.depth){found=true;break;}}if(!found)throw new IllegalStateException("Uncovered exact Source/depth constraint obligation");},work);
                    groupedCoverage(groupedInputs,proofs,work);
                    Set<Proof> checked=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof proof:proofs)verify(proof,input,local,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,checked,maximumDepth,work);
                    return new Result(true,groupedBase==null?"Whole rigid-Origin producer/constraint pairs covered; native discovery, all-cut qualification and admission remain open":"Existing constraint grammar and explicit grouped seed manifest covered; automatic grouped enumeration, native/family qualification and admission remain open",input,common,pool,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,round,obligations[0],proofs);
                }
                current=next;
            }
            throw new IllegalStateException("Constraint closure round limit");
        }catch(Exception failure){return new Result(false,failure.toString(),input,common,pool,exchange,compounds,groupedBase,groupedInputs,signed,applications,signedApplications,symbolicHeads,groundedMixed,instantiatedUnits,0,0,Collections.emptyList());}
    }
}
