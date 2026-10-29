/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import java.lang.reflect.Constructor;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Adversarial correspondence mutations are test-only; production constructors remain sealed. */
public final class ContextUniversalProductSessionRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean value,String reason){++checks;if(!value)throw new AssertionError(reason);}
    static ContextUniversalSignedProductAudit.Result scoped(Result r,ConstraintTransfer t,int products,int work){
        ContextUniversalSignedProductAudit.SessionBinding session=ContextUniversalSignedProductAudit.bindWithValidationReuse(r,t,products,work,4096);
        ContextUniversalSignedProductAudit.Result actual=session.result,old=ContextUniversalSignedProductAudit.bind(r,t,products,work);
        require(session.metrics.entries==0&&actual.bound==old.bound&&actual.coverage==r&&actual.transfer==t,"fresh closed scope agrees with ordinary audit on small controls");
        require(actual.symbolic==old.symbolic&&actual.foreignOnly==old.foreignOnly&&actual.products.size()==old.products.size(),"full symbolic/private/product inventory equal");
        for(int i=0;i<actual.products.size();++i){ContextUniversalSignedProductAudit.Product a=actual.products.get(i),b=old.products.get(i);require(a.input==b.input&&a.theorem==b.theorem&&a.local==b.local&&a.shared==b.shared&&a.expected==b.expected&&a.localProof==b.localProof&&a.sharedProof==b.sharedProof,"actual bidirectional Item/Support/theorem correspondence equal");}
        return actual;
    }
    static void certified()throws Exception {
        ContextUniversalGroupedChainAuditRunner.Fixture f=new ContextUniversalGroupedChainAuditRunner.Fixture(false,true);
        ContextUniversalGroupedLocalRounds.Certificate c=f.trace.history.get(0).certificates.get(Cut.of(f.b));ConstraintTransfer t=ContextWitnessResultTransport.transferConstraintsCertified(c,16,WORK);
        ContextUniversalSignedProductAudit.SessionBinding valid=ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,WORK,4096);
        require(valid.result.bound&&valid.metrics.hits>0&&valid.metrics.verified>0&&valid.metrics.entries==0,valid.result.reason);
        ContextUniversalSignedProductAudit.SessionBinding fallback=ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,WORK,1);
        require(fallback.result.bound&&fallback.metrics.peakEntries==1&&fallback.metrics.capacityBypass>0&&fallback.metrics.entries==0,"capacity1 runs real checks");
        ContextUniversalSignedProductAudit.SessionBinding tiny=ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,1,4096);
        no(tiny.result,"previous successful session cannot satisfy fresh work1");require(tiny.metrics.work==2&&tiny.metrics.entries==0&&tiny.result.reason.contains("Ground transport work limit"),"original ground budget and failing callback retained");
        java.lang.reflect.Field bindings=ConstraintTransfer.class.getDeclaredField("bindings");bindings.setAccessible(true);Object saved=bindings.get(t);bindings.set(t,null);
        try{no(ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,WORK,4096).result,"missing actual parameter sidecar after success");}finally{bindings.set(t,saved);}
        ContextUniversalFrozenConstraints.Proof grouped=c.output.proofs.stream().filter(p->p.grouped!=null).findFirst().get();java.lang.reflect.Field grade=grouped.getClass().getDeclaredField("support");grade.setAccessible(true);Support support=grouped.support;grade.set(grouped,new Support(support.sources,support.depth+1));
        try{no(ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,WORK,4096).result,"forged grade checked fresh");}finally{grade.set(grouped,support);}
        java.lang.reflect.Field manifest=c.output.getClass().getDeclaredField("groupedInputs");manifest.setAccessible(true);Object full=manifest.get(c.output);manifest.set(c.output,Collections.emptyList());
        try{no(ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,WORK,4096).result,"whole grouped manifest omission checked fresh");}finally{manifest.set(c.output,full);}
        require(ContextUniversalSignedProductAudit.bindWithValidationReuse(c.output,t,65536,WORK,4096).result.bound,"restored certificate independently accepted");
    }
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Result close(Snapshot x,Snapshot a,Packet p,PrivatePool pool){return ContextUniversalSignedProductsRunner.signed(x,a,p,pool,16);}
    static ContextUniversalSignedProductAudit.Result audit(Result r,ConstraintTransfer t){ContextUniversalSignedProductAudit.Result a=scoped(r,t,65536,WORK);require(a.bound,a.reason);for(ContextUniversalSignedProductAudit.Product product:a.products){require(product.local.proofs.contains(product.localProof)&&product.expected.sources.equals(product.localProof.sources),"actual local Item/Support and full Sources");require(product.shared==null?product.sharedProof==null:product.shared.proofs.contains(product.sharedProof)&&product.sharedProof.equals(product.localProof),"actual shared minimum");}return a;}
    static void no(ContextUniversalSignedProductAudit.Result a,String reason){require(!a.bound&&a.products.isEmpty()&&a.symbolic==0&&a.foreignOnly==0,reason+": "+a.reason);}
    static Packet packet(List<Item> items)throws Exception{Constructor<Packet> c=Packet.class.getDeclaredConstructor(List.class);c.setAccessible(true);return c.newInstance(items);}
    static Item item(Item old,List<Support> proofs)throws Exception{Constructor<Item> c=Item.class.getDeclaredConstructor(ContextWitnessResultTransport.Atom.class,List.class);c.setAccessible(true);return c.newInstance(old.atom,proofs);}
    static ConstraintTransfer corrupt(ConstraintTransfer t,Packet p)throws Exception{Constructor<ConstraintTransfer> c=ConstraintTransfer.class.getDeclaredConstructor(boolean.class,String.class,Result.class,Packet.class,Packet.class,Packet.class,PrivatePool.class,PrivatePool.class,Runnable.class);c.setAccessible(true);return c.newInstance(true,"test-only altered product",t.proof,t.before,p,p,null,null,(Runnable)()->{});}
    static void exactAndMutations()throws Exception{
        String rule="!@w left(w,1)&&right(w,1)->p(w,1);";
        Snapshot x=bind(true,"!anchor(1); "+rule),b=bind(false,"!right(1,1); !~p(1,1);"),loop=bind(false,"!@w left(w,1)->left(w,1);");Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(b,loop),WORK);Packet common=ContextPrivateWitnessPoolRunner.common(x);Result r=close(x,group,common,null);ConstraintTransfer t=ContextUniversalSignedProductsRunner.transfer(r,common,null,3);ContextUniversalSignedProductAudit.Result a=audit(r,t);
        require(a.symbolic>0&&a.foreignOnly>0,"withheld symbolic residuals and foreign fixed units counted explicitly");
        Item negative=t.output.items.stream().filter(i->i.atom.predicate.equals("left")&&!i.atom.positive).findFirst().get();require(negative.proofs.size()==2,"distinct exact Source alternatives retained");
        List<Item> removed=new ArrayList<>(t.output.items);removed.remove(negative);no(scoped(r,corrupt(t,packet(removed)),65536,WORK),"missing negative product");
        List<Item> changed=new ArrayList<>(t.output.items);changed.set(changed.indexOf(negative),item(negative,Collections.singletonList(negative.proofs.get(0))));no(scoped(r,corrupt(t,packet(changed)),65536,WORK),"missing Source superset");
        Support original=negative.proofs.get(0);changed=new ArrayList<>(t.output.items);List<Support> grades=new ArrayList<>(negative.proofs);grades.set(0,new Support(original.sources,original.depth+1));changed.set(changed.indexOf(negative),item(negative,grades));no(scoped(r,corrupt(t,packet(changed)),65536,WORK),"wrong exact Source grade");
        changed=new ArrayList<>(t.output.items);changed.add(negative);no(scoped(r,corrupt(t,packet(changed)),65536,WORK),"duplicate product proof");
        Snapshot extra=bind(false,"!@w anchor(w)->extra(w);");Result er=close(x,extra,common,null);ConstraintTransfer et=ContextUniversalSignedProductsRunner.transfer(er,common,null,16);Item extraItem=et.output.items.stream().filter(i->i.atom.predicate.equals("extra")).findFirst().get();changed=new ArrayList<>(t.output.items);changed.add(extraItem);no(scoped(r,corrupt(t,packet(changed)),65536,WORK),"extra product caught by reverse audit");
        Result same=close(x,group,common,null);no(scoped(same,t,65536,WORK),"equal repeated closure is not actual owning ledger");
        no(scoped(r,t,1,WORK),"obligation cap atomic");no(scoped(r,t,65536,1),"work cap atomic");no(scoped(null,t,65536,WORK),"missing closure");no(scoped(r,null,65536,WORK),"missing transfer");
        try{a.products.clear();throw new AssertionError("mutable products");}catch(UnsupportedOperationException expected){++checks;}
        require(t.output.items.contains(negative)&&negative.proofs.get(0)==original,"mutations never change real sealed products");
    }
    static void privateAndRounds()throws Exception{
        Snapshot x=bind(true,"!anchor(1); !@w seed(w,1)->~flag(w,1);"),a=bind(false,"!$w seed(w,1); !@w seed(w,1)->local(w,1);");Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);Result r=close(x,a,common,pool);ConstraintTransfer t=ContextUniversalSignedProductsRunner.transfer(r,common,pool,16);ContextUniversalSignedProductAudit.Result audit=audit(r,t);
        require(audit.products.stream().anyMatch(p->p.fact.literals.get(0).predicate.equals("seed")&&p.shared==null),"raw private witness retained only locally");require(audit.products.stream().anyMatch(p->p.fact.literals.get(0).predicate.equals("flag")&&!p.fact.literals.get(0).positive&&p.shared!=null&&!p.fact.origins.isEmpty()),"X-dependent negative physical Origin shared with exact proof");
        Snapshot generic=bind(true,"!anchor(1); !@w ~p(w,1);");Packet gp=ContextPrivateWitnessPoolRunner.common(generic);Result gr=close(generic,null,gp,null);ContextUniversalSignedProductAudit.Result ga=audit(gr,ContextUniversalSignedProductsRunner.transfer(gr,gp,null,16));require(ga.symbolic>0&&ga.products.stream().noneMatch(p->p.fact.literals.get(0).predicate.equals("p")),"universal negative not finite sampled");
        ContextUniversalFrozenBranch.Limits limits=new ContextUniversalFrozenBranch.Limits(16,16,16,512,256,WORK,512,512);
        ContextUniversalSignedRounds.Result rounds=ContextUniversalSignedRounds.run(x,Collections.singletonList(a),common,Collections.singletonMap(Cut.of(a),pool),limits,8,WORK);require(rounds.status==ContextUniversalSignedRounds.Status.STABLE_SIGNED_POOLS,rounds.reason);
        for(ContextUniversalSignedRounds.Round round:rounds.history){require(round.products.keySet().equals(round.checks.keySet()),"every supplied cut audited before barrier");for(Cut cut:round.products.keySet())require(round.products.get(cut).coverage==round.checks.get(cut)&&round.products.get(cut).transfer==round.barrier.branches.get(cut),"actual branch receipt in round history");try{round.products.clear();throw new AssertionError("mutable audits");}catch(UnsupportedOperationException expected){++checks;}}
        Snapshot bad=bind(false,"!~anchor(1);");Result refuted=close(x,bad,common,null);no(scoped(refuted,t,65536,WORK),"refutation cannot become product audit");
        Result legacy=ContextUniversalFrozenConstraints.close(ContextUniversalSignedProductsRunner.rows(generic,null),gp,null,16,16,512,256,WORK);no(scoped(legacy,ContextUniversalSignedProductsRunner.transfer(gr,gp,null,16),65536,WORK),"positive-only closure cannot cover signed products");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-audit-").toString());exactAndMutations();privateAndRounds();certified();System.out.println("CONTEXT_UNIVERSAL_PRODUCT_SESSION_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
