/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalInitialEntry.*;
import org.kanger.ContextUniversalInitialEntry.Result;
import org.kanger.ContextWitnessResultTransport.*;
import static org.kanger.ContextUniversalContinuationMatrix.*;

/** Actual production factory, compared with independent pre-factory assembly and controller receipts. */
final class ContextUniversalInitialEntryMatrix {
    static Result assemble(Snapshot x,List<Snapshot> cuts){return ContextUniversalInitialEntry.assemble(x,cuts,ContextUniversalGroupedRoundsRunner.limits(16),4096);}
    static Entry ready(Result r){require(r.status==Status.READY&&r.entry!=null&&r.refutations.isEmpty(),r.phase+": "+r.reason);Entry e=r.entry;require(e.units!=null&&e.compounds!=null&&e.units.checks.equals(e.checks)&&e.compounds.checks.equals(e.checks)&&e.units.barrier==e.compounds.barrier&&e.units.common==e.common&&e.compounds.common==e.common,"both mandatory SAME-ledger carriers");for(Map.Entry<Cut,org.kanger.ContextUniversalFrozenConstraints.Result> c:e.checks.entrySet())require(c.getValue().common==e.common&&c.getValue().pool==e.pools.get(c.getKey()),"actual owning common/private frame");return e;}
    static void incomplete(Result r,String why){require(r.status==Status.INCOMPLETE&&r.entry==null&&r.refutations.isEmpty(),why+": "+r.status+"/"+r.phase+"/"+r.reason);}
    static Frame frame(Entry e){return new Frame(e.x,e.foreign,e.common,e.pools,e.units,e.compounds);}
    static void compare(Entry e,Frame oracle){require(ContextWitnessResultTransport.sameContents(e.common,oracle.input,WORK)&&ContextUniversalSignedUnitExchange.sameContents(e.units,oracle.units,WORK)&&ContextUniversalCompoundExchange.sameContents(e.compounds,oracle.compounds,WORK),"independent pre-factory exact common/unit/compound Source-grade inventory");require(e.pools.keySet().equals(oracle.pools.keySet()),"whole private inventory");for(Cut c:e.pools.keySet()){PrivatePool p=e.pools.get(c),q=oracle.pools.get(c);require(p.sources.equals(q.sources)&&p.snapshot==q.snapshot&&p.x==q.x&&ContextWitnessResultTransport.sameContents(p.local,q.local,WORK)&&p.declarations.size()==q.declarations.size(),"original private manifest and contents");}}
    static void positive()throws Exception {
        ContextUniversalSignedAlternativesMatrix.Fixture f=new ContextUniversalSignedAlternativesMatrix.Fixture(true);
        org.kanger.ContextUniversalGroupedRounds.Result previous=null;int cases=0;
        List<List<Snapshot>> orders=new ArrayList<>(ContextUniversalContinuationRefutationRunner.permutations(Arrays.asList(f.a,f.b,f.d)));
        for(List<Snapshot> members:Arrays.asList(Arrays.asList(f.b,f.d),Arrays.asList(f.d,f.b))){Snapshot g=ContextProductionNativeBinding.trusted("entry-factory",members,WORK);orders.add(Arrays.asList(f.a,g));orders.add(Arrays.asList(g,f.a));}
        for(List<Snapshot> order:orders){Frame oracle=ContextUniversalInitialCarrierMatrix.entry(f,order);Entry e=ready(assemble(f.x,order));compare(e,oracle);require(e.compounds.clauses.size()==4&&e.units.units.isEmpty()&&e.common.items.size()==1,"exact complete initial four/zero/one oracle");org.kanger.ContextUniversalGroupedRounds.Result r=ContextUniversalSeparatePrivateMatrix.continued(frame(e));f.products(r);if(previous!=null)same(previous,r);previous=r;++cases;}
        Snapshot x=bind(true,"!anchor(1); !@u @v a(u,1)&&b(u,v)->r(u,v);"),a=bind(false,"!$u $v a(u,1)&&a(v,1);");Entry two=ready(assemble(x,Collections.singletonList(a)));Set<Origin> origins=new HashSet<>();for(ContextUniversalCompoundExchange.ResidualProof p:two.compounds.clauses)origins.addAll(p.clause.origins);require(two.compounds.clauses.size()==3&&origins.size()==2&&two.pools.size()==1,"same Source distinct binder residuals retained");
        Entry self=ready(assemble(bind(true,"!anchor(1);"),Collections.emptyList()));require(self.checks.size()==1&&self.pools.isEmpty()&&self.units.units.isEmpty()&&self.compounds.clauses.isEmpty(),"self-only complete entry may have both empty carriers");
        Entry signed=ready(assemble(bind(true,"!~anchor(1);"),Collections.singletonList(bind(false,"!$u seed(u);"))));require(signed.pools.size()==1&&!signed.common.items.get(0).atom.positive,"signed common uses existing signed private materialization");
        System.out.println("INITIAL_ENTRY_POSITIVE handoffs="+cases+" independentOracle=true twoBinders=true signedCommon=true");
    }
    static void refusals()throws Exception {
        Snapshot x=bind(true,"!p(1);"),negative=bind(false,"!~p(1);"),bad=bind(false,"!@u (p(u)&&c(u)->s(u))&&(left(u)||right(u));"),unready=bind(false,"!$u $v pair(u,v);");
        Result ref=assemble(x,Collections.singletonList(negative));require(ref.status==Status.REFUTED&&ref.entry==null&&ref.refutations.size()==1,"genuine contradiction has no entry");Rejection rejection=ref.refutations.get(Cut.of(negative));require(rejection.xRefuted&&!rejection.proofs.isEmpty()&&rejection.proofs.stream().allMatch(p->p.clause==null&&p.support.sources.equals(ContextUniversalSourceAlternativesMatrix.sources(x.primary.get(0).source,negative.primary.get(0).source))),"exact sourced detached contradiction");
        for(Snapshot later:Arrays.asList(bad,unready))for(List<Snapshot> order:Arrays.asList(Arrays.asList(negative,later),Arrays.asList(later,negative)))incomplete(assemble(x,order),"later unsupported/unready cut suppresses earlier contradiction");
        for(List<Snapshot> members:Arrays.asList(Arrays.asList(negative,bad),Arrays.asList(bad,negative)))incomplete(assemble(x,Collections.singletonList(ContextProductionNativeBinding.trusted("bad-entry",members,WORK))),"trusted group cannot hide unsupported row");
        incomplete(assemble(x,Arrays.asList(negative,negative)),"duplicate cuts");incomplete(assemble(x,Collections.singletonList(x)),"X cannot be foreign");
        Snapshot donor=bind(false,"!b(1);"),g=ContextProductionNativeBinding.trusted("overlap-entry",Arrays.asList(negative,donor),WORK),h=ContextProductionNativeBinding.trusted("overlap-entry2",Arrays.asList(negative,bind(false,"!c(1);")),WORK);
        incomplete(assemble(x,Arrays.asList(g,negative)),"trusted/independent overlap");incomplete(assemble(x,Arrays.asList(g,h)),"trusted/trusted overlap");
        incomplete(assemble(bind(true,"!anchor(1); !@u left(u)||right(u);"),Collections.singletonList(donor)),"supported primary seed cannot certify whole X");
        incomplete(assemble(bind(true,"!@u p(u);"),Collections.emptyList()),"no supported primary seed is explicit incomplete");
        for(String text:Arrays.asList("!$u seed(u,1)&&~flag(u,1);","!$u $v pair(u,v);"))incomplete(assemble(x,Collections.singletonList(bind(false,text))),"unsupported/unready witness");
        ContextUniversalSignedAlternativesMatrix.Fixture f=new ContextUniversalSignedAlternativesMatrix.Fixture(true);List<Snapshot> cuts=Arrays.asList(f.b,f.d,f.a);
        for(int kind=0;kind<3;++kind){ContextUniversalFrozenBranch.Limits l=new ContextUniversalFrozenBranch.Limits(16,16,kind==1?0:16,512,kind==0?1:256,kind==2?1:WORK,65536,65536);Result r=ContextUniversalInitialEntry.assemble(f.x,cuts,l,4096);incomplete(r,"alternative/depth/work bounds expose neither unit prefix nor contradiction");}
        incomplete(ContextUniversalInitialEntry.assemble(x,Collections.singletonList(negative),ContextUniversalGroupedRoundsRunner.limits(16),0),"invalid capacity");
        Result repeat=assemble(x,Collections.singletonList(negative));require(repeat.status==Status.REFUTED&&repeat.refutations.size()==1,"refusal does not poison subsequent actual contradiction");
        System.out.println("INITIAL_ENTRY_REFUSALS allCutBeforeRejection=true overlap=true wholeRows=true boundedNoPrefix=true");
    }
}
