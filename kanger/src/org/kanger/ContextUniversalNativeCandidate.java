/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalNativeBranch.Status;

/** Exact detached single-candidate diagnostic. STABLE_SUPPLIED is never admission/publication permission. */
final class ContextUniversalNativeCandidate {
    static final class Policy {
        final ContextUniversalNativeBranch.Policy branch;final int rows,preparationWork,events;
        Policy(ContextUniversalNativeBranch.Policy branch,int rows,int preparationWork,int events){if(branch==null||rows<1||rows>10000||preparationWork<1||events<1)throw new IllegalArgumentException("Bounded actual candidate policy required");this.branch=branch;this.rows=rows;this.preparationWork=preparationWork;this.events=events;}
    }
    static final class Attribution {
        final ContextUniversalNativeBranch.Rejection rejection;final ContextUniversalFrozenConstraints.Proof proof;
        final Source candidate;final boolean usesCandidate;final Set<Source> originalSources;
        private Attribution(ContextUniversalNativeBranch.Rejection rejection,ContextUniversalFrozenConstraints.Proof proof,Source candidate,Map<Source,Source> originals,Runnable work){this.rejection=rejection;this.proof=proof;this.candidate=candidate;usesCandidate=proof.support.sources.contains(candidate);originalSources=translate(proof.support,originals,work);}
    }
    static final class Receipt {
        final ContextProductionCandidate.Staged artifact;final ContextUniversalNativeBranch.Receipt branch;final Policy policy;final Map<Source,Source> originals;
        private Receipt(ContextProductionCandidate.Staged artifact,ContextUniversalNativeBranch.Receipt branch,Policy policy,Map<Source,Source> originals){this.artifact=artifact;this.branch=branch;this.policy=policy;this.originals=Collections.unmodifiableMap(new LinkedHashMap<>(originals));}
    }
    static final class Result {
        final Status status;final String phase,reason;final Receipt output;final Map<ContextWitnessResultTransport.Cut,List<Attribution>> oppositions;
        private Result(Status status,String phase,String reason,Receipt output,Map<ContextWitnessResultTransport.Cut,List<Attribution>> oppositions){this.status=status;this.phase=phase;this.reason=reason;this.output=output;Map<ContextWitnessResultTransport.Cut,List<Attribution>> copy=new LinkedHashMap<>();for(Map.Entry<ContextWitnessResultTransport.Cut,List<Attribution>> e:oppositions.entrySet())copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));this.oppositions=Collections.unmodifiableMap(copy);}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static Map<Source,Source> manifest(ContextProductionCandidate.Staged a,Snapshot base,List<Snapshot> foreign,String statement,Runnable work){
        require(a!=null&&a.identity!=null&&base!=null&&foreign!=null&&statement!=null&&a.base==base&&a.foreign.equals(foreign)&&statement.equals(a.statement),"Different actual candidate request/base/family");
        ContextProductionFamilyAssembly.identity(base,true,work);ContextProductionFamilyAssembly.identity(a.staged,true,work);require(a.staged.inventory.initialization.equals(Collections.singletonList("candidate:"+a.identity)),"Different actual native staging identity");
        require(base.inventory.context.equals(a.staged.inventory.context)&&base.inventory.revision==a.staged.inventory.revision&&a.staged.primary.size()==base.primary.size()+1&&a.rebased.size()==base.primary.size(),"Complete append-only native candidate inventory required");
        Map<Source,Entry> staged=new LinkedHashMap<>();for(Entry e:a.staged.primary){work.run();require(staged.put(e.source,e)==null,"Repeated staged Source");}
        Entry candidate=staged.get(a.candidate);require(candidate!=null&&a.candidate.localX&&a.candidate.view.equals("inventory:"+a.staged.inventory.fingerprint),"Exact actual new candidate occurrence required");
        Map<Source,Source> originals=new LinkedHashMap<>();originals.put(a.candidate,a.candidate);Set<Source> old=new HashSet<>();
        for(Entry original:base.primary){work.run();old.add(original.source);Source rebuilt=a.rebased.get(original.source);Entry actual=staged.get(rebuilt);require(actual!=null&&originals.put(rebuilt,original.source)==null,"Missing/aliased original native Source rebasing");ContextProductionNativeBinding.sameEntry(original,actual,work);}
        require(a.rebased.keySet().equals(old)&&originals.keySet().equals(staged.keySet()),"Unaccounted old/new primary native Source");
        Set<UUID> physical=new HashSet<>();ContextProductionFamilyAssembly.claimContexts(base,physical,work);
        for(Snapshot cut:foreign){work.run();require(cut!=null,"Actual foreign candidate snapshot required");ContextProductionFamilyAssembly.identity(cut,false,work);ContextProductionFamilyAssembly.claimContexts(cut,physical,work);for(Entry e:cut.primary){work.run();require(originals.put(e.source,e.source)==null,"Aliased physical foreign Source");}}
        return originals;
    }
    private static Set<Source> translate(Support support,Map<Source,Source> originals,Runnable work){Set<Source> sources=new LinkedHashSet<>();for(Source source:support.sources){work.run();Source original=originals.get(source);require(original!=null,"Unmapped native proof Source");require(sources.add(original),"Aliased translated proof Source");}return Collections.unmodifiableSet(sources);}
    private static void products(ContextUniversalNativeBranch.Receipt branch,Map<Source,Source> originals,Runnable work){
        org.kanger.ContextUniversalGroupedRounds.Result trace=branch.chain.trace;
        for(ContextWitnessResultTransport.Item item:trace.output.packet.items)for(Support s:item.proofs)translate(s,originals,work);
        for(ContextWitnessResultTransport.PrivatePool pool:trace.output.privatePools.values())for(ContextWitnessResultTransport.Item item:pool.local.items)for(Support s:item.proofs)translate(s,originals,work);
        for(ContextUniversalSignedUnitExchange.Unit unit:trace.symbolicOutput.units)translate(unit.support,originals,work);
        for(ContextUniversalCompoundExchange.ResidualProof clause:trace.compoundOutput.clauses)translate(clause.support,originals,work);
    }
    /** Preparation, provenance authentication and branch operations retain separate original budgets. */
    static Result qualify(Snapshot base,List<Snapshot> foreign,String statement,Policy policy){
        String phase="preflight";
        try {
            require(base!=null&&foreign!=null&&foreign.size()<=15&&policy!=null,"Actual bounded native candidate request required");List<Snapshot> cuts=new ArrayList<>(foreign);new Policy(policy.branch,policy.rows,policy.preparationWork,policy.events);
            phase="staging";ContextProductionCandidate.StagingResult staged=ContextProductionCandidate.stage(base,cuts,statement,policy.rows,policy.preparationWork,policy.events);require(staged.prepared,staged.reason);
            phase="source-binding";Runnable work=ContextGroundProductionRounds.budget(policy.branch.work);ContextProductionCandidate.Staged a=staged.artifact;Map<Source,Source> originals=manifest(a,base,cuts,statement,work);
            phase="native-branch";ContextUniversalNativeBranch.Result evaluated=ContextUniversalNativeBranch.run(a.staged,cuts,policy.branch);require(evaluated.status!=Status.INCOMPLETE,evaluated.phase+": "+evaluated.reason);
            if(evaluated.status==Status.REFUTED){Map<ContextWitnessResultTransport.Cut,List<Attribution>> oppositions=new LinkedHashMap<>();for(Map.Entry<ContextWitnessResultTransport.Cut,ContextUniversalNativeBranch.Rejection> e:evaluated.refutations.entrySet()){List<Attribution> proofs=new ArrayList<>();for(ContextUniversalFrozenConstraints.Proof proof:e.getValue().proofs)proofs.add(new Attribution(e.getValue(),proof,a.candidate,originals,work));oppositions.put(e.getKey(),proofs);}require(!oppositions.isEmpty(),"Complete attributed candidate rejection required");return new Result(Status.REFUTED,phase,"Supplied staged branch refuted; usesCandidate is explicit, no stable/candidate prefix",null,oppositions);}
            phase="product-attribution";require(evaluated.output!=null&&evaluated.output.x==a.staged&&evaluated.output.foreign.equals(cuts)&&evaluated.output.policy==policy.branch,"Different actual staged branch verdict");products(evaluated.output,originals,work);
            return new Result(Status.STABLE_SUPPLIED,"stable-supplied-candidate","Exact staged native candidate/supplied branch bound; no write or full-language eligibility",new Receipt(a,evaluated.output,policy,originals),Collections.emptyMap());
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,Collections.emptyMap());}
    }
    /** Fresh complete staging replay authenticates requested assertion semantics, then both branch receipts. */
    static void check(Receipt receipt,Snapshot expectedBase,List<Snapshot> expectedForeign,String expectedStatement,Policy expectedPolicy,int maximumWork,int capacity){
        require(receipt!=null&&expectedPolicy!=null&&receipt.policy==expectedPolicy,"Actual candidate receipt/policy required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);ContextProductionCandidate.Staged a=receipt.artifact;Map<Source,Source> originals=manifest(a,expectedBase,expectedForeign,expectedStatement,work);require(receipt.originals.equals(originals),"Different exact original Source translation");
        ContextProductionCandidate.StagingResult replay=ContextProductionCandidate.stage(expectedBase,expectedForeign,expectedStatement,expectedPolicy.rows,expectedPolicy.preparationWork,expectedPolicy.events);require(replay.prepared,"Independent native staging replay refused: "+replay.reason);Entry fresh=null,actual=null;for(Entry e:replay.artifact.staged.primary)if(e.source.equals(replay.artifact.candidate))fresh=e;for(Entry e:a.staged.primary)if(e.source.equals(a.candidate))actual=e;require(fresh!=null&&actual!=null,"Actual replayed candidate occurrence required");ContextProductionNativeBinding.sameEntry(fresh,actual,work);
        ContextUniversalNativeBranch.check(receipt.branch,a.staged,a.foreign,expectedPolicy.branch,maximumWork,capacity);products(receipt.branch,originals,work);
    }
}
