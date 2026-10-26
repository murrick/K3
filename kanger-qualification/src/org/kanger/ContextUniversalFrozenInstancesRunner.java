/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalFrozenInstances.*;
import org.kanger.ContextUniversalFrozenInstances.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** Concrete demands are questions, not donors or finite-domain completeness evidence. */
public final class ContextUniversalFrozenInstancesRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static ContextUniversalFrozenUnits.Result units(Snapshot x,Snapshot a,PrivatePool pool) {
        ContextUniversalFrozenBranch.Result r=pool==null?ContextUniversalFrozenBranch.run(x,a,ContextPrivateWitnessPoolRunner.common(x),ContextUniversalFrozenBranchRunner.full()):ContextUniversalFrozenBranch.runPrivate(pool,ContextUniversalFrozenBranchRunner.full());
        require(r.produced,"owning prerequisite "+r.phase+" "+r.reason);return r.products.transfer.proof.input;
    }
    static Residual request(String s)throws Exception{return ContextUniversalInputResiduals.schema(bind(false,s).primary.get(0).clauses.get(0),()->{});}
    static Result prove(ContextUniversalFrozenUnits.Result u,int depth,Residual... requests){return ContextUniversalFrozenInstances.prove(u,Arrays.asList(requests),16,depth,65536,WORK);}
    static Result ok(ContextUniversalFrozenUnits.Result u,int depth,Residual... requests){Result r=prove(u,depth,requests);require(r.proved,"concrete instance "+r.reason);require(r.input==u,"actual sealed unit frame");return r;}
    static void no(Result r,String why){require(!r.proved&&r.instances.isEmpty(),why+": "+r.reason);}
    static void immutable(Result r){try{r.instances.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}try{r.instances.get(0).proofs.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}}
    static void ordinary()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@u p(u,1);");ContextUniversalFrozenUnits.Result u=units(x,a,null);
        require(u.universalUnits==1&&u.units.stream().noneMatch(v->v.conclusion.literals.get(0).predicate.equals("p")),"baseline universal remains symbolic before explicit demand");
        Residual p7=request("!p(7,1);"),p8=request("!p(8,1);");Result r=ok(u,1,p7,p8,p7);require(r.instances.size()==2,"duplicates normalized without enumerating admissible domain");
        for(Instance i:r.instances){require(i.proofs.size()==1,"one whole producer Source alternative");ContextUniversalFrozenEvents.Donor d=i.proofs.get(0);require(d.support.depth==1&&d.support.sources.equals(Collections.singleton(a.primary.get(0).source)),"ordinary instance adds one step, no unrelated X or demand Source");require(d.anchors.isEmpty()&&d.assignment.size()==1,"ordinary value needs no invented witness binding");}
        immutable(r);no(prove(u,0,p7),"instantiation is not a depth-zero theorem copy");
        no(prove(u,16,p7,request("!unknown(7);")),"later unproved request exposes no successful prefix");
        no(prove(u,16,request("!@v p(v,1);")),"request must be concrete, not finite sampling of universal");
        no(prove(u,16,request("!~p(7,1);")),"negative request not a positive theorem");
        no(prove(u,16,request("!p(7,1)->p(8,1);")),"conditional is not a unit");
        no(ContextUniversalFrozenInstances.prove(u,Arrays.asList(p7,p8),16,16,2,WORK),"shared receipt budget no prefix");
        no(ContextUniversalFrozenInstances.prove(u,Arrays.asList(p7),16,16,65536,1),"work budget no prefix");
        no(ContextUniversalFrozenInstances.prove(null,Arrays.asList(p7),16,16,65536,WORK),"no caller-created theorem authority");
        no(ContextUniversalFrozenInstances.prove(u,null,16,16,65536,WORK),"null requests fail");
        no(prove(u,16,(Residual)null),"null later request fails");
        require(ok(u,0).instances.isEmpty(),"empty request set has no completeness meaning");
        require(u.universalUnits==1,"immutable source unit projection unchanged");
    }
    static void origins()throws Exception {
        String seed="!$w seed(w,1);",p="!@v p(v,1);",rule="!@v seed(v,1)->p(v,1);";
        Snapshot x=bind(true,seed),a=bind(false,p),helper=bind(false,rule);ContextUniversalFrozenUnits.Result u=units(x,a,null),made=units(x,helper,null);
        Residual demand=made.units.stream().filter(v->v.conclusion.literals.get(0).predicate.equals("p")).findFirst().get().conclusion;
        Result r=ok(u,1,demand);require(r.instances.size()==1&&r.instances.get(0).proofs.size()==1,"carried root demand has exact proof");
        ContextUniversalFrozenEvents.Donor d=r.instances.get(0).proofs.get(0);require(d.anchors.size()==1&&d.anchors.get(0).input==u.input.theory.frame.local.items.get(0),"actual original input item authenticates free-variable binding");
        require(d.support.sources.equals(new HashSet<>(Arrays.asList(x.primary.get(0).source,a.primary.get(0).source)))&&d.support.depth==1,"full producer and carried root Source history, demand producer excluded");
        Snapshot otherX=bind(true,seed);ContextUniversalFrozenUnits.Result other=units(otherX,helper,null);Residual alien=other.units.stream().filter(v->v.conclusion.literals.get(0).predicate.equals("p")).findFirst().get().conclusion;
        no(prove(u,16,alien),"equal binder index/formula cannot borrow another physical Origin");
        Snapshot fixed=bind(false,rule);ContextUniversalFrozenUnits.Result fixedUnits=units(x,fixed,null);Result copy=ok(fixedUnits,1,demand);require(copy.instances.get(0).proofs.stream().allMatch(v->v.support.depth==1&&v.assignment.isEmpty()&&v.anchors.isEmpty()),"fixed theorem copy no second instantiation or artificial anchor");
        no(ContextUniversalFrozenInstances.prove(fixedUnits,Arrays.asList(demand),0,16,65536,WORK),"proof exploration bound distinct from requested fact bound");
        Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,fixed),WORK);Result alternatives=ok(units(x,group,null),1,demand);
        require(alternatives.instances.get(0).proofs.stream().anyMatch(v->v.support.sources.equals(d.support.sources)),"universal Source alternative retained alongside fixed theorem");
        require(alternatives.instances.get(0).proofs.stream().anyMatch(v->v.support.sources.equals(new HashSet<>(Arrays.asList(x.primary.get(0).source,fixed.primary.get(0).source)))),"independent fixed alternative retained");
        Snapshot px=bind(true,"!$u $v pair(u,v);"),pa=bind(false,"!@u @v pair(u,v);");ContextUniversalFrozenUnits.Result pair=units(px,pa,null);Residual both=ContextUniversalInputResiduals.fact(pair.input.theory.frame.local.items.get(0),()->{});Result pairs=ok(pair,1,both);
        require(both.origins.size()==2,"two physical original binders remain distinct");require(pairs.instances.get(0).proofs.stream().anyMatch(v->v.assignment.size()==2&&v.support.sources.contains(pa.primary.get(0).source)),"both rigid binding obligations proved using actual carried pair");
    }
    static void substitutionsAndAlternatives()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@u same(u,u);");
        ContextUniversalFrozenUnits.Result same=units(x,a,null);
        ok(same,1,request("!same(7,7);"));no(prove(same,16,request("!same(7,8);")),"repeated variable has one consistent substitution");
        Snapshot pair=bind(false,"!@u @v pair(u,v);");Result coalesced=ok(units(x,pair,null),1,request("!pair(7,7);"));
        require(coalesced.instances.get(0).proofs.get(0).assignment.size()==2,"distinct universal variables may coincide at one ordinary value");
        String p="!@u p(u,1);",loop="!@u p(u,1)->p(u,1);";Snapshot pa=bind(false,p),pb=bind(false,loop),group=ContextProductionNativeBinding.trusted("own",Arrays.asList(pa,pb),WORK);
        ContextUniversalFrozenUnits.Result all=units(x,group,null);Residual demand=request("!p(17,1);");Result r=ok(all,2,demand);
        Set<Source> base=Collections.singleton(pa.primary.get(0).source),superset=new HashSet<>(Arrays.asList(pa.primary.get(0).source,pb.primary.get(0).source));
        require(r.instances.get(0).proofs.stream().anyMatch(d->d.support.sources.equals(base)&&d.support.depth==1),"minimum base Source alternative");
        require(r.instances.get(0).proofs.stream().anyMatch(d->d.support.sources.equals(superset)&&d.support.depth==2),"Source supersets are not pruned after instantiation");
        no(prove(all,1,demand),"deeper exact Source obligation cannot vanish behind shallow alternative");
        Result reordered=ok(all,2,request("!p(18,1);"),demand);require(reordered.instances.get(1).proofs.size()==r.instances.get(0).proofs.size(),"request order preserves full proof alternatives");
    }
    static void privateInput()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!$w p(w,1); !@v p(v,1);");Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);ContextUniversalFrozenUnits.Result u=units(x,a,pool);
        Residual demand=ContextUniversalInputResiduals.fact(pool.local.items.stream().filter(i->i.atom.predicate.equals("p")).findFirst().get(),()->{});Result r=ok(u,1,demand);
        require(r.instances.get(0).proofs.stream().anyMatch(d->d.assignment.size()==1&&!d.anchors.isEmpty()),"private original binding participates locally");
        for(ContextUniversalFrozenEvents.Donor d:r.instances.get(0).proofs)require(d.support.sources.stream().noneMatch(s->s.localX),"private question cannot invent unrelated X dependence");
        require(common.items.stream().noneMatch(i->i.atom.predicate.equals("p")),"instances are receipts, do not publish private facts");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-instances-").toString());ordinary();origins();substitutionsAndAlternatives();privateInput();System.out.println("CONTEXT_UNIVERSAL_FROZEN_INSTANCES_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
