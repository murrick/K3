/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Direct manifest/provenance audit plus fresh native assertion replay; never runs candidate driver. */
final class ContextSignedWitnessNativeCandidateAudit {
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static Map<Source,Source> manifest(ContextProductionCandidate.Staged a,Snapshot base,List<Snapshot> foreign,String statement,Runnable work){
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
    static Set<Source> translate(Support support,Map<Source,Source> originals,Runnable work){Set<Source> sources=new LinkedHashSet<>();for(Source source:support.sources){work.run();Source original=originals.get(source);require(original!=null,"Unmapped native proof Source");require(sources.add(original),"Aliased translated proof Source");}return Collections.unmodifiableSet(sources);}

 static Map<Cut,ContextUniversalFrozenConstraints.Result> checks(ContextSignedWitnessNativeBranch.Receipt branch){return branch.initialRejection!=null?branch.initialRejection.checks:branch.termination.result.terminal.checks;}
 private static void products(ContextSignedWitnessNativeBranch.Receipt b,Map<Source,Source> originals,Runnable work){
  if(b.status!=ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED)return;
  ContextSignedWitnessContinuationScope.Receipt output=b.termination.result.output;
  for(Item i:output.current().packet.items)for(Support s:i.proofs){work.run();translate(s,originals,work);}
  for(PrivatePool p:output.current().privatePools.values())for(Item i:p.local.items)for(Support s:i.proofs){work.run();translate(s,originals,work);}
  for(ContextUniversalSignedUnitExchange.Unit u:output.units().units){work.run();translate(u.support,originals,work);}
  for(ContextUniversalCompoundExchange.ResidualProof c:output.compounds().clauses){work.run();translate(c.support,originals,work);}
 }
 static void check(ContextSignedWitnessNativeCandidate.Receipt r,Snapshot base,List<Snapshot> foreign,String statement,ContextSignedWitnessNativeCandidate.Policy p,int maximumWork,int capacity){
  require(r!=null&&p!=null&&r.policy==p&&maximumWork>0&&capacity>=1&&capacity<=4096,"Exact bounded signed candidate receipt/policy required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);ContextProductionCandidate.Staged a=r.artifact;Map<Source,Source> originals=manifest(a,base,foreign,statement,work);require(r.originals.equals(originals),"Different complete original Source translation");
  ContextProductionCandidate.StagingResult replay=ContextProductionCandidate.stage(base,foreign,statement,p.rows,p.preparationWork,p.events);require(replay.prepared,"Fresh candidate staging replay refused: "+replay.reason);Entry actual=null,fresh=null;for(Entry e:a.staged.primary){work.run();if(e.source.equals(a.candidate))actual=e;}for(Entry e:replay.artifact.staged.primary){work.run();if(e.source.equals(replay.artifact.candidate))fresh=e;}require(actual!=null&&fresh!=null,"Actual candidate occurrence required");ContextProductionNativeBinding.sameEntry(fresh,actual,work);
  require(r.branch!=null,"Actual owned signed branch required");ContextSignedWitnessNativeBranchAudit.check(r.branch,a.staged,foreign,p.branch,maximumWork,capacity);products(r.branch,originals,work);
  Set<Cut> expected=new LinkedHashSet<>();if(r.branch.status==ContextSignedWitnessNativeBranch.Status.REFUTED)for(Map.Entry<Cut,ContextUniversalFrozenConstraints.Result> e:checks(r.branch).entrySet())if(!e.getValue().emptyProofs.isEmpty()){work.run();expected.add(e.getKey());List<ContextSignedWitnessNativeCandidate.Attribution> values=r.oppositions.get(e.getKey());require(values!=null&&values.size()==e.getValue().emptyProofs.size(),"Every actual empty proof must retain causal attribution");for(int i=0;i<values.size();++i){work.run();ContextSignedWitnessNativeCandidate.Attribution v=values.get(i);ContextUniversalFrozenConstraints.Proof proof=e.getValue().emptyProofs.get(i);require(v!=null&&v.proof==proof&&proof.clause==null&&v.candidate.equals(a.candidate)&&v.usesCandidate==proof.support.sources.contains(a.candidate)&&v.originalSources.equals(translate(proof.support,originals,work)),"Different actual empty/candidate use/original Source attribution");}}
  require(r.oppositions.keySet().equals(expected)&&(r.branch.status!=ContextSignedWitnessNativeBranch.Status.REFUTED||!expected.isEmpty()),"No lost/extra/refuted-without-proof cut attribution");
 }
}
