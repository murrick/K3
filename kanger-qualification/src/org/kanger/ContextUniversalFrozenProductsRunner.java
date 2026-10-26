/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenProducts.Product;
import org.kanger.ContextUniversalFrozenProducts.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Exact audited ledger and complete minimum Packet correspondence, without family authority. */
public final class ContextUniversalFrozenProductsRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String s){return ContextPrivateWitnessPoolRunner.source(x,s);}
    static boolean usesX(Support s){return s.sources.stream().anyMatch(t->t.localX);}
    static ContextUniversalFrozenClosure.Result close(Snapshot x,Snapshot a,Packet input,PrivatePool pool){return ContextUniversalFrozenCoverageRunner.close(x,a,input,pool);}
    static ContextUniversalFrozenCoverage.Result audit(ContextUniversalFrozenClosure.Result input,Snapshot x,Snapshot a){ContextUniversalFrozenCoverage.Result r=ContextUniversalFrozenCoverage.inspect(input,x,a,16,65536,WORK);require(r.covered,"audit precursor "+r.reason);return r;}
    static TypedTransfer transfer(ContextUniversalFrozenClosure.Result input,int depth) {
        ContextUniversalFrozenReplay.Result replay=ContextUniversalFrozenReplay.replay(input,16,WORK,500000);require(replay.bound,"native precursor "+replay.reason);
        ContextUniversalFrozenUnits.Result units=ContextUniversalFrozenUnits.project(replay,16,65536,WORK);require(units.projected,"unit precursor "+units.reason);
        ContextUniversalFrozenEvents.Result events=ContextUniversalFrozenEvents.reconstruct(units,16,65536,WORK);require(events.reconstructed,"event precursor "+events.reason);
        TypedTransfer t=input.frame.pool==null?ContextWitnessResultTransport.transferTyped(events,input.frame.common,depth,WORK):ContextWitnessResultTransport.transferTypedPrivate(events,input.frame.pool,depth,WORK);return t;
    }
    static Result products(ContextUniversalFrozenCoverage.Result coverage,TypedTransfer transfer) {
        Result r=ContextUniversalFrozenProducts.bind(coverage,transfer,65536,WORK);require(r.bound,"product correspondence "+r.reason);
        require(r.coverage==coverage&&r.transfer==transfer&&!r.products.isEmpty(),"actual audit/transfer tokens and nonvacuous products");
        int wanted=coverage.input.frame.local.items.stream().mapToInt(i->i.proofs.size()).sum();boolean privateInput=coverage.input.frame.pool!=null;
        for(ContextUniversalFrozenClosure.Proof proof:coverage.input.proofs)if(proof.clause.literals.size()==1&&proof.clause.variables.isEmpty()&&(privateInput||usesX(proof.support)))++wanted;
        for(ContextUniversalFrozenEvents.Conclusion c:transfer.proof.conclusions)if(privateInput||usesX(c.support))++wanted;
        require(r.products.size()==wanted,"all actual input/fixed/event obligations, never a selected prefix");
        for(Product p:r.products) {
            require((p.input==null?0:1)+(p.theorem==null?0:1)+(p.event==null?0:1)==1,"exactly one genuine origin token per obligation");
            require(p.localProof.sources.equals(p.expected.sources)&&p.localProof.depth<=p.expected.depth&&p.local.proofs.contains(p.localProof),"actual retained whole Source/shorter proof token");
            require(p.fact.equivalent(ContextUniversalInputResiduals.fact(p.local,()->{}),()->{}),"canonical values/Origins unchanged in actual local Packet");
            if(usesX(p.expected))require(p.shared!=null&&p.sharedProof.sources.equals(p.expected.sources)&&p.sharedProof.depth==p.localProof.depth&&transfer.output.items.contains(p.shared),"all real X-dependent proof alternatives are shared");
            else require(p.shared==null&&p.sharedProof==null,"private-only proof does not become shared by co-location");
        }
        require(transfer.output.basis==null&&!transfer.output.observedStep&&transfer.output.coveredCut==null&&transfer.output.roundCut==null,"correspondence issues no family stamps");
        return r;
    }
    static void denied(Result r,String why){require(!r.bound&&r.products.isEmpty()&&r.symbolic==0&&r.foreignOnly==0,why+": "+r.reason);}
    static void sameInputDifferentLedger()throws Exception {
        Snapshot x=bind(true,"!$w seed(w,1);"),a=bind(false,"!@w seed(w,1)->out(w,1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenClosure.Result first=close(x,a,input,null),second=close(x,a,input,null);ContextUniversalFrozenCoverage.Result audit=audit(first,x,a);TypedTransfer t=transfer(first,16);Result r=products(audit,t);
        require(r.products.stream().anyMatch(p->p.theorem!=null&&p.fact.literals.get(0).predicate.equals("out")),"actual fixed theorem product bound");
        TypedTransfer other=transfer(second,16);require(other.transferred&&other.before==input,"other valid proof chain uses identical actual common input");
        denied(ContextUniversalFrozenProducts.bind(audit,other,65536,WORK),"cannot combine successful audit and transfer from different ledgers");
        denied(ContextUniversalFrozenProducts.bind(audit,t,1,WORK),"later product count limit no prefix");
        denied(ContextUniversalFrozenProducts.bind(audit,t,65536,1),"work limit no prefix");
        TypedTransfer failed=transfer(first,0);require(!failed.transferred,"actual failed depth transport");denied(ContextUniversalFrozenProducts.bind(audit,failed,65536,WORK),"failed transfer grants no correspondence");
        boolean immutable=false;try{r.products.clear();}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"immutable product receipt list");
    }
    static void theoremAndEventMinima()throws Exception {
        Snapshot x=bind(true,"!$w seed(w,1)&&q(7,1); !@w seed(w,1)&&rel(w,7)->out(w,1);"),a=bind(false,"!@u p(u,1); !@u @v p(u,1)&&q(v,1)->rel(u,v);");Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenClosure.Result closed=close(x,a,input,null);Result r=products(audit(closed,x,a),transfer(closed,3));
        require(r.products.stream().anyMatch(p->p.event!=null&&p.fact.literals.get(0).predicate.equals("out")&&p.expected.depth==4&&p.localProof.depth==3),"native recipe4 covered by exact-Source fixed theorem3 without relabelling recipe");
        require(r.symbolic>0,"universal units/conditionals explicitly withheld from concrete products");
        Map<Cut,Packet> alleged=Collections.singletonMap(r.transfer.target,r.transfer.output);require(ContextWitnessResultTransport.family(input,Collections.singletonList(r.transfer.target),WORK).finish(alleged,WORK).status==FamilyStatus.INCOMPLETE,"bound products cannot establish Family STEP");
    }
    static void privateBoundary(boolean reverse)throws Exception {
        String seed="!$w seed(w,1);",gate="!@v gate(v,1);",rule="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);",out="!@w rel(w,7)->out(w,1);",anchor="!anchor(1);";
        Snapshot x=bind(true,anchor+out),a=bind(false,seed+gate+"!privateValue(7);"),b=bind(false,rule);Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
        Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,target,WORK);ContextUniversalFrozenClosure.Result closed=close(x,target,common,pool);Result r=products(audit(closed,x,target),transfer(closed,16));
        require(r.products.stream().anyMatch(p->p.fact.literals.get(0).predicate.equals("rel")&&p.shared==null)&&r.products.stream().anyMatch(p->p.fact.literals.get(0).predicate.equals("out")&&p.shared!=null),"private rel retained, actual X consumer exports out");
        for(Product p:r.products)if(p.fact.literals.get(0).predicate.equals("rel"))require(!p.expected.sources.contains(source(x,anchor)),"unrelated anchor cannot alter private proof");
        require(r.products.stream().anyMatch(p->p.fact.literals.get(0).predicate.equals("privateValue")&&p.localProof.depth==0&&p.shared==null),"raw private ordinary fact preserved at0, never shared");
        ContextUniversalFrozenClosure.Result partial=close(x,target,common,null);ContextUniversalFrozenCoverage.Result missing=ContextUniversalFrozenCoverage.inspect(partial,x,target,16,65536,WORK);require(!missing.covered,"actual omitted private root inventory");TypedTransfer incomplete=transfer(partial,16);require(incomplete.transferred,"partial projection may still transport sound common-only facts");denied(ContextUniversalFrozenProducts.bind(missing,incomplete,65536,WORK),"sound transfer cannot repair failed original-row coverage");
    }
    static void foreignOnlyAndTwoOrigins()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!privateValue(7); !@v p(v,1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);ContextUniversalFrozenClosure.Result closed=close(x,a,input,null);Result r=products(audit(closed,x,a),transfer(closed,16));
        require(r.foreignOnly>0&&r.symbolic>0&&r.products.stream().noneMatch(p->p.fact.literals.get(0).predicate.equals("privateValue")||p.fact.literals.get(0).predicate.equals("p")),"ordinary private-only facts and symbolic universals cannot be exported by unrelated X anchor");
        Snapshot tx=bind(true,"!$u $v pair(u,v);"),ta=bind(false,"!@u @v pair(u,v)->out(u,v);");ContextUniversalFrozenClosure.Result two=close(tx,ta,ContextPrivateWitnessPoolRunner.common(tx),null);Result pair=products(audit(two,tx,ta),transfer(two,16));
        require(pair.products.stream().anyMatch(p->p.fact.literals.get(0).predicate.equals("out")&&p.fact.origins.size()==2),"two distinct original parameters covered in actual output");
        String seed="!$w seed(w,1)&&flag(w,1);",q="!@w seed(w,1)->q(w,1);",complex="!@w seed(w,1)&&flag(w,1)->q(w,1);";
        Snapshot sx=bind(true,seed),sa=bind(false,q),sb=bind(false,complex),group=ContextProductionNativeBinding.trusted("own",Arrays.asList(sa,sb),WORK);ContextUniversalFrozenClosure.Result alternatives=close(sx,group,ContextPrivateWitnessPoolRunner.common(sx),null);Result both=products(audit(alternatives,sx,group),transfer(alternatives,16));
        Set<Support> actual=new HashSet<>();for(Product p:both.products)if(p.fact.literals.get(0).predicate.equals("q"))actual.add(p.sharedProof);
        require(both.products.stream().anyMatch(p->p.theorem!=null&&p.expected.depth==2&&p.localProof.depth==1),"native hyperapplication1 can cover symbolic theorem2 without relabelling it");
        require(actual.equals(new HashSet<>(Arrays.asList(new Support(new HashSet<>(Arrays.asList(source(sx,seed),source(sa,q))),1),new Support(new HashSet<>(Arrays.asList(source(sx,seed),source(sb,complex))),1)))),"whole Source alternatives remain separate at their actual minimum grades, no union flattening");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-products-").toString());sameInputDifferentLedger();theoremAndEventMinima();privateBoundary(false);privateBoundary(true);foreignOnlyAndTwoOrigins();
        denied(ContextUniversalFrozenProducts.bind(null,null,65536,WORK),"no sealed audit/transfer");System.out.println("CONTEXT_UNIVERSAL_FROZEN_PRODUCTS_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
