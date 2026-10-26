/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextUniversalFrozenBranch.Result;

/** Requested instances transport as proved facts, never as completeness stamps or borrowed X labels. */
public final class ContextUniversalInstanceProductsRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static ContextUniversalFrozenBranch.Limits full(){return ContextUniversalFrozenBranchRunner.full();}
    static Result old(Snapshot x,Snapshot a,Packet p,PrivatePool pool){Result r=ContextUniversalFrozenBranch.run(x,a,p,pool,full());require(r.produced,r.phase+" "+r.reason);return r;}
    static Result run(Snapshot x,Snapshot a,Packet p,PrivatePool pool){Result r=ContextUniversalFrozenBranch.runWithPremises(x,a,p,pool,full());require(r.produced,r.phase+" "+r.reason);require(r.premises!=null&&r.premises.prepared&&r.premises.instances==r.products.transfer.instances,"owning generated receipt retained to product audit");require(r.products.coverage.input==r.premises.input.input.theory,"one actual owning ledger");ContextUniversalFrozenBranchRunner.unmarked(r.products.transfer.output);return r;}
    static boolean has(Packet p,String pred){return p.items.stream().anyMatch(i->i.atom.predicate.equals(pred));}
    static void no(TypedTransfer r,String why){require(!r.transferred&&r.output==null&&r.local==null&&r.privateAfter==null,why+": "+r.reason);}
    static ContextUniversalFrozenInstances.Result prove(Result b,String... values)throws Exception {List<Residual> facts=new ArrayList<>();for(String v:values)facts.add(ContextUniversalFrozenInstancesRunner.request(v));return ContextUniversalFrozenInstances.prove(b.products.transfer.proof.input,facts,16,64,65536,WORK);}
    static void explicit()throws Exception {
        Snapshot x=bind(true,"!anchor(1); !@u p(u,1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);Result b=old(x,null,input,null);require(!has(b.products.transfer.output,"p"),"concrete baseline is absent from old observed-only transfer");
        ContextUniversalFrozenInstances.Result i=prove(b,"!p(7,1);","!p(8,1);");require(i.proved,"sealed arbitrary ordinary instance proofs");
        TypedTransfer t=ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,i,input,1,WORK);require(t.transferred&&t.instances==i&&has(t.output,"p"),"previously unobserved instances transported with exact proof receipt");
        ContextUniversalFrozenProducts.Result audit=ContextUniversalFrozenProducts.bind(b.products.coverage,t,65536,WORK);require(audit.bound,audit.reason);
        require(audit.products.stream().filter(p->p.instance!=null).count()==2,"both instance products have actual donor provenance");
        for(ContextUniversalFrozenProducts.Product p:audit.products)if(p.instance!=null){require(p.expected.depth==1&&p.localProof.depth==1&&p.sharedProof.depth==1,"instance grade retained without reset or extra application");require(p.expected.sources.equals(Collections.singleton(x.primary.stream().filter(e->e.statement.contains("@u")).findFirst().get().source)),"no anchor or demand Source contamination");}
        no(ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,i,input,0,WORK),"late combined fact depth failure atomic");
        no(ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,i,input,16,1),"work cap atomic");
        ContextUniversalFrozenInstances.Result bad=prove(b,"!p(7,1);","!unknown(1);");no(ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,bad,input,16,WORK),"later failed demand cannot return early products");
        Result copy=old(x,null,input,null);no(ContextWitnessResultTransport.transferTypedInstances(copy.products.transfer.proof,i,input,16,WORK),"equivalent but different sealed unit projection cannot substitute");
        Packet other=ContextPrivateWitnessPoolRunner.common(x);no(ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,i,other,16,WORK),"equal common contents are not actual frame");
        require(!ContextUniversalFrozenProducts.bind(copy.products.coverage,t,65536,WORK).bound,"product audit rejects different ledger");
        require(!ContextUniversalFrozenProducts.bind(b.products.coverage,t,1,WORK).bound,"product cap includes every instance obligation");
        require(!has(input,"p"),"original input unchanged");
    }
    static void generated()throws Exception {
        Snapshot x=bind(true,"!anchor(1); !@u p(u,1);"),a=bind(false,"!p(7,1)&&missing(8,1)->done(1); !@v p(v,1)&&unknown(v,1)->tail(v,1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);Result b=run(x,a,input,null);
        require(has(b.products.transfer.output,"p")&&!has(b.products.transfer.output,"done"),"proved premise can be retained while full conditional remains inactive");
        require(b.premises.obligations.stream().anyMatch(o->o.enabled&&o.fact.literals.get(0).predicate.equals("p")),"fixed enabled premise generated automatically");
        require(b.premises.obligations.stream().anyMatch(o->!o.enabled&&o.fact.literals.get(0).predicate.equals("missing")),"unproved premise stays explicit, never a false collision");
        require(b.premises.symbolic>0,"free-variable premise occurrences stay symbolic, no finite sampling");
        require(b.products.products.stream().anyMatch(p->p.instance!=null&&p.fact.literals.get(0).predicate.equals("p")),"generated instance reaches bidirectional product audit");
        require(!has(input,"p"),"generated pipeline preserves immutable common barrier");
        ContextUniversalFrozenBranch.Limits zero=new ContextUniversalFrozenBranch.Limits(16,16,0,512,256,WORK,500000,65536);
        Result denied=ContextUniversalFrozenBranch.runWithPremises(x,a,input,null,zero);require(!denied.produced&&denied.products==null&&denied.premises==null&&denied.phase.equals("transfer"),"owning late failure exposes neither prepared instances nor product prefix");
        try{b.premises.obligations.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}
        ContextUniversalFrozenPremises.Result limited=ContextUniversalFrozenPremises.prepare(b.premises.input,16,1,WORK);require(!limited.prepared&&limited.instances==null&&limited.obligations.isEmpty(),"obligation/instance budget no prefix");
        ContextUniversalFrozenPremises.Result work=ContextUniversalFrozenPremises.prepare(b.premises.input,16,65536,1);require(!work.prepared&&work.instances==null&&work.obligations.isEmpty(),"generator work cap no prefix");
        Snapshot empty=bind(true,"!anchor(1);");Result e=run(empty,null,ContextPrivateWitnessPoolRunner.common(empty),null);require(e.premises.obligations.isEmpty()&&e.premises.instances.instances.isEmpty(),"empty projection not universal-family completion");
    }
    static void privateCases()throws Exception {
        String seed="!$w seed(w,1);",p="!@v p(v,1);",rule="!@w seed(w,1)&&p(w,1)->done(w,1);";
        Snapshot x=bind(true,"!anchor(1); "+rule),a=bind(false,seed+p);Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);Result r=run(x,a,common,pool);
        require(has(r.products.transfer.local,"p")&&!has(r.products.transfer.output,"p"),"private Origin instance stays local despite X-owned demanding rule");
        require(has(r.products.transfer.output,"done"),"actual X-dependent conclusion remains shareable");
        for(ContextUniversalFrozenProducts.Product product:r.products.products)if(product.instance!=null){require(product.shared==null&&product.expected.sources.stream().noneMatch(s->s.localX),"demanding conditional Source cannot anchor private universal premise");require(!product.instance.anchors.isEmpty(),"private carried root has actual input binding proof");}
        require(!has(pool.local,"p")&&pool.common==common,"old private state unchanged");
        Result self=run(x,null,common,null);Map<Cut,Result> outputs=new LinkedHashMap<>();outputs.put(Cut.of(a),r);outputs.put(Cut.of(x),self);
        ContextUniversalFrozenBranch.Barrier barrier=ContextUniversalFrozenBranch.merge(ContextWitnessResultTransport.family(common,Arrays.asList(Cut.of(a),Cut.of(x)),WORK),outputs,WORK);
        require(barrier.merged&&barrier.output.privatePools.containsKey(pool.cut),"generated private branch participates in atomic owning barrier");
        PrivatePool rebased=barrier.output.privatePools.get(pool.cut);require(rebased.common==barrier.output.packet&&has(rebased.local,"p")&&!has(barrier.output.packet,"p"),"private instance survives rebase without becoming common");
        no(ContextWitnessResultTransport.transferTypedInstances(r.products.transfer.proof,r.premises.instances,common,16,WORK),"private receipt cannot be exported through common-only API");
        Snapshot xx=bind(true,"!$w seed(w,1); "+rule),aa=bind(false,p);Packet cp=ContextPrivateWitnessPoolRunner.common(xx);Result foreign=run(xx,aa,cp,null);
        require(has(foreign.products.transfer.output,"p"),"real X-carried binding gives shareable instantiated premise");
        require(foreign.products.products.stream().filter(v->v.instance!=null).allMatch(v->v.expected.sources.stream().anyMatch(s->s.localX)),"only actual binding ancestry crosses common barrier");
    }
    static void alternatives()throws Exception {
        String p="!@u p(u,1);",loop="!@u p(u,1)->p(u,1);";Snapshot x=bind(true,"!anchor(1); "+p),a=bind(false,loop);Packet common=ContextPrivateWitnessPoolRunner.common(x);Result b=old(x,a,common,null);ContextUniversalFrozenInstances.Result i=prove(b,"!p(17,1);");
        TypedTransfer t=ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,i,common,2,WORK);require(t.transferred,t.reason);require(ContextUniversalFrozenProducts.bind(b.products.coverage,t,65536,WORK).bound,"all Source supersets audited");
        Item fact=t.output.items.stream().filter(v->v.atom.predicate.equals("p")).findFirst().get();require(fact.proofs.stream().anyMatch(v->v.sources.size()==1&&v.depth==1)&&fact.proofs.stream().anyMatch(v->v.sources.size()==2&&v.depth==2),"minimum per exact Source set, no superset pruning");
        no(ContextWitnessResultTransport.transferTypedInstances(b.products.transfer.proof,i,common,1,WORK),"shallow alternate cannot hide deeper Source obligation");
    }
    static void rounds(boolean reverse)throws Exception {
        String seed="!$w seed(w,1);",rule="!@w seed(w,1)&&p(w,1)->done(w,1);",p="!@v p(v,1);",finish="!@w p(w,1)->finish(w,1);";
        Snapshot x=bind(true,seed+rule),a=bind(false,p),b=bind(false,finish);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result ar=run(x,a,input,null),br=run(x,b,input,null),xr=run(x,null,input,null);
        require(has(ar.products.transfer.output,"p")&&!has(br.products.transfer.output,"finish"),"B cannot see another branch's same-round instance");
        Cut ac=Cut.of(a),bc=Cut.of(b),xc=Cut.of(x);List<Cut> cuts=reverse?Arrays.asList(bc,xc,ac):Arrays.asList(ac,xc,bc);
        Map<Cut,Result> outputs=new LinkedHashMap<>();for(Result r:reverse?Arrays.asList(br,xr,ar):Arrays.asList(ar,xr,br))outputs.put(r.products.transfer.target,r);
        ContextUniversalFrozenBranch.Barrier first=ContextUniversalFrozenBranch.merge(ContextWitnessResultTransport.family(input,cuts,WORK),outputs,WORK);
        require(first.merged&&has(first.output.packet,"p")&&!has(first.output.packet,"finish"),"generated products merge only at immutable common barrier");
        Result next=run(x,b,first.output.packet,null);require(has(next.products.transfer.output,"finish"),"B consumes generated instance only next round");
        Item fact=next.products.transfer.output.items.stream().filter(v->v.atom.predicate.equals("finish")).findFirst().get();
        Set<Source> expected=new HashSet<>(Arrays.asList(ContextPrivateWitnessPoolRunner.source(x,seed),ContextPrivateWitnessPoolRunner.source(a,p),ContextPrivateWitnessPoolRunner.source(b,finish)));
        require(fact.proofs.stream().anyMatch(v->v.depth==2&&v.sources.equals(expected)),"instance binding and consumer Sources preserved without unrelated demanding rule");
        require(!has(input,"p"),"initial common unchanged after generated rounds");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("instance-products-").toString());explicit();generated();privateCases();alternatives();rounds(false);rounds(true);System.out.println("CONTEXT_UNIVERSAL_INSTANCE_PRODUCTS_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
