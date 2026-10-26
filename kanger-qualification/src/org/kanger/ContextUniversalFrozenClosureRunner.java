/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalFrozenClosure.Result;
import org.kanger.ContextUniversalInputResiduals.Literal;

/** Typed unit saturation preserves arbitrary variables, rigid origins, exact Sources and round boundaries. */
public final class ContextUniversalFrozenClosureRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String text)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,text);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... sources){return new HashSet<>(Arrays.asList(sources));}
    static ContextUniversalUnitClosure.Result theory(Snapshot x,Snapshot a){ContextUniversalUnitClosure.Result t=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(t.unitClosed,"schema frame: "+t.reason);return t;}
    static Result close(ContextUniversalUnitClosure.Result theory,Packet input){Result r=ContextUniversalFrozenClosure.close(theory,input,16,16,512,256,WORK);require(r.closed,"typed closure: "+r.reason);ContextUniversalFrozenClosure.check(r,16,ContextGroundProductionRounds.budget(WORK));return r;}
    static boolean unit(Proof proof,String name){return proof.clause.literals.size()==1&&proof.clause.literals.get(0).predicate.equals(name);}
    static void denied(Result r,String why){require(!r.closed&&r.proofs.isEmpty()&&r.rounds==0,why+": "+r.reason);}
    static void missingNative()throws Exception {
        String bundle="!$w seed(w,1)&&q(7,1);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);",p="!@u p(u,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,bundle+consumer),a=bind(false,p+rel);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalUnitClosure.Result t=theory(x,a);Result result=close(t,input);
        require(result.basis.equals(input.identity)&&result.localBasis.equals(input.identity)&&!result.privateInput,"exact immutable common basis");
        require(result.rounds>=3&&result.obligations>0,"new residues participate only in later immutable rounds; stable barrier required");
        boolean universal=false,out=false;
        Origin origin=input.items.stream().filter(i->i.atom.predicate.equals("seed")).findFirst().get().atom.arguments.get(0).origin;
        for(Proof proof:result.proofs) {
            if(unit(proof,"rel")&&proof.clause.variables.size()==1) {
                universal=true;require(proof.clause.origins.isEmpty()&&proof.support.depth==2&&proof.support.sources.equals(set(source(x,bundle),source(a,p),source(a,rel))),"required forall u rel(u,7), exact source/depth2");
            }
            if(unit(proof,"out")) {
                out=true;require(proof.clause.variables.isEmpty()&&proof.clause.origins.equals(Collections.singleton(origin)),"out belongs to actual witness, never all values");
                require(proof.support.depth==3&&proof.support.sources.equals(set(source(x,bundle),source(x,consumer),source(a,p),source(a,rel))),"binary symbolic proof out3 keeps both newly generated parents and four Sources");
                require(proof.unit!=null&&proof.horn!=null&&proof.unit.unit!=null&&proof.horn.unit!=null,"conclusion has checked multi-round proof DAG parents");
            }
        }
        require(universal&&out,"unobserved universal residual and frozen out both proved");
        require(input.items.size()==2&&input.items.stream().noneMatch(i->i.atom.predicate.equals("out")||i.atom.predicate.equals("rel")),"no implicit ordinary Packet export");
        denied(ContextUniversalFrozenClosure.close(t,input,1,16,512,256,WORK),"productive first pass cannot certify closure");
        denied(ContextUniversalFrozenClosure.close(t,input,16,2,512,256,WORK),"missing depth3 theorem returns no proof prefix");
        denied(ContextUniversalFrozenClosure.close(t,input,16,16,1,256,WORK),"typed clause limit yields no prefix");
        denied(ContextUniversalFrozenClosure.close(t,input,16,16,512,256,1),"work limit yields no prefix");
        Packet wrong=ContextPrivateWitnessPoolRunner.common(bind(true,bundle+consumer));
        denied(ContextUniversalFrozenClosure.close(t,wrong,16,16,512,256,WORK),"another X view cannot reuse owning schema roots");
        boolean checked=false;try{ContextUniversalFrozenClosure.check(result,2,()->{});}catch(IllegalStateException expected){checked=true;}
        require(checked,"proof DAG recheck enforces retained conclusion depth");
    }
    static void standardizedApart()throws Exception {
        String bundle="!$w seed(w,1);",unit="!@w gate(w,1);",horn="!@w seed(w,1)&&gate(7,1)->mid(w,1);";
        Snapshot x=bind(true,bundle),a=bind(false,unit+horn);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result r=close(theory(x,a),input);boolean mid=false;
        for(Proof proof:r.proofs)if(unit(proof,"mid")) {
            mid=true;require(proof.clause.origins.size()==1&&proof.clause.variables.isEmpty(),"unit w=7 cannot ground independent Horn w or its frozen donor");
            require(proof.clause.literals.get(0).arguments.get(0).origin.equals(input.items.get(0).atom.arguments.get(0).origin),"canonical original witness retained across resolution");
        }
        require(mid,"ground pivot not observed by native still yields frozen mid");
        String constants="!@u p(u,1); !q(7,1); !@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot gx=bind(true,"!anchor(1);"),ga=bind(false,constants);Result g=close(theory(gx,ga),ContextPrivateWitnessPoolRunner.common(gx));
        require(g.proofs.stream().anyMatch(p->unit(p,"rel")&&p.clause.variables.size()==1&&p.clause.origins.isEmpty()),"head-free universal remains universal over unmentioned values");
    }
    static void duplicatesAndOrigins()throws Exception {
        String bundle="!$u pair(u,u)&&p(u,1);",rule="!@x @y pair(x,y)&&p(x,1)&&p(y,1)->same(x,y);";
        Snapshot x=bind(true,bundle),a=bind(false,rule);Result r=close(theory(x,a),ContextPrivateWitnessPoolRunner.common(x));
        require(r.proofs.stream().anyMatch(p->p.clause.literals.size()==2&&p.clause.origins.size()==1&&p.clause.literals.stream().anyMatch(l->l.predicate.equals("same"))),"duplicate body literals collapse only after identical rigid substitution");
        require(r.proofs.stream().anyMatch(p->unit(p,"same")&&p.clause.origins.size()==1),"normalized residual participates in next unit application");
        String two="!$u $v pair(u,v);",diag="!@x pair(x,x)->diagonal(x);",pair="!@x @y pair(x,y)->rel(x,y);",next="!@x @y rel(x,y)->out(x,y);";
        Snapshot tx=bind(true,two),ta=bind(false,diag+pair+next);Result t=close(theory(tx,ta),ContextPrivateWitnessPoolRunner.common(tx));
        require(t.proofs.stream().noneMatch(p->unit(p,"diagonal")),"different frozen origins supply no equality proof");
        require(t.proofs.stream().anyMatch(p->unit(p,"out")&&p.clause.origins.size()==2&&p.support.depth==2),"two rigid origins survive full typed proof chain, without native replay permission");
    }
    static void privateAndOrder()throws Exception {
        String seed="!$w seed(w,1);",gate="!@v gate(v,1);",rel="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);",end="!@u rel(u,7)->out(u,1);";
        Snapshot x=bind(true,"!anchor(1);"+end),a=bind(false,seed+gate),b=bind(false,rel);Packet common=ContextPrivateWitnessPoolRunner.common(x);Result before=null;
        for(boolean reverse:new boolean[]{false,true}) {
            Snapshot group=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
            PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,group,WORK);
            Result r=ContextUniversalFrozenClosure.closePrivate(theory(x,group),pool,16,16,512,256,WORK);
            require(r.closed&&r.privateInput&&r.basis.equals(common.identity)&&r.localBasis.equals(pool.local.identity),"owning trusted private/common frame: "+r.reason);
            ContextUniversalFrozenClosure.check(r,16,ContextGroundProductionRounds.budget(WORK));
            boolean frozen=false,out=false;
            for(Proof proof:r.proofs) {
                if(unit(proof,"rel")&&proof.clause.origins.size()==1&&proof.clause.variables.size()==1) {
                    frozen=true;require(proof.support.sources.equals(set(source(a,seed),source(a,gate),source(b,rel)))&&proof.support.depth==2,"private universal residual has no invented X anchor");
                }
                if(unit(proof,"out")){out=true;require(proof.support.sources.contains(source(x,end))&&!proof.support.sources.contains(source(x,"!anchor(1);")),"only actual X consumer introduces X dependence");}
            }
            require(frozen&&out,"frozen universal clause and later fixed-value consumer proved");
            if(before!=null) {
                require(before.proofs.size()==r.proofs.size(),"member order preserves complete clause/source alternative count");
                for(Proof proof:before.proofs)require(r.proofs.stream().anyMatch(other->proof.support.equals(other.support)&&proof.clause.equivalent(other.clause,()->{})),"every exact source/depth theorem survives reversed member order");
            }
            before=r;require(common.items.size()==1&&pool.local.items.stream().noneMatch(i->i.atom.predicate.equals("out")),"proof closure does not mutate input pools");
            PrivatePool wrong=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
            denied(ContextUniversalFrozenClosure.closePrivate(theory(x,group),wrong,16,16,512,256,WORK),"independent cut cannot borrow trusted producer authority");
        }
        PrivatePool independent=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
        Result alone=ContextUniversalFrozenClosure.closePrivate(theory(x,a),independent,16,16,512,256,WORK);
        require(alone.closed&&alone.proofs.stream().noneMatch(p->unit(p,"rel")||unit(p,"out")),"independent A cannot acquire B's raw rule");
    }
    static void derivedDiagonal()throws Exception {
        String bundle="!$u $v mark(u,v)&&mark(u,u)&&q(7);",p="!@u p(u);",make="!@u @v p(u)&&q(v)->pair(u,u);",consume="!@u @v pair(u,v)&&mark(u,v)->rel(u,v);";
        Snapshot x=bind(true,bundle),a=bind(false,p+make+consume);Result result=close(theory(x,a),ContextPrivateWitnessPoolRunner.common(x));
        boolean diagonal=false,rel=false;
        for(Proof proof:result.proofs) {
            if(unit(proof,"pair")&&proof.clause.variables.size()==1) {
                diagonal=true;Literal h=proof.clause.literals.get(0);
                require(proof.clause.origins.isEmpty()&&h.arguments.get(0).variable==h.arguments.get(1).variable,"newly derived universal unit retains its repeated variable");
            }
            if(unit(proof,"rel")) {
                rel=true;Literal h=proof.clause.literals.get(0);
                require(proof.clause.origins.size()==1&&h.arguments.get(0).origin.equals(h.arguments.get(1).origin),"new universal diagonal cannot prove rel for two independent frozen binders");
                require(proof.support.depth==3&&proof.support.sources.equals(set(source(x,bundle),source(a,p),source(a,make),source(a,consume))),"principal typed unifier preserves all whole Sources and binary depth3");
            }
        }
        require(diagonal&&rel,"derived universal unit participates in later typed/frozen matching");
    }
    static void alternatives()throws Exception {
        String seed=ContextUniversalDerivedReplayRunner.SEED,rule="!@w seed(w,1)->q(w,1);",complex="!@w seed(w,1)&&flag(w,1)->q(w,1);";
        Snapshot x=bind(true,seed),a=bind(false,rule),b=bind(false,complex);
        Snapshot target=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK);ContextUniversalUnitClosure.Result t=theory(x,target);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        Result result=close(t,input);Set<Support> actual=new HashSet<>();for(Proof proof:result.proofs)if(unit(proof,"q"))actual.add(proof.support);
        require(actual.equals(new HashSet<>(Arrays.asList(new Support(set(source(x,seed),source(a,rule)),1),new Support(set(source(x,seed),source(b,complex)),2)))),"exact alternative Sources are not union-flattened or subset-elided");
        denied(ContextUniversalFrozenClosure.close(t,input,16,16,512,1,WORK),"derived Source alternative limit yields no prefix");
        String loop="!@w q(w,1)->q(w,1);";Snapshot lx=bind(true,seed),la=bind(false,rule+loop);
        Result cycle=close(theory(lx,la),ContextPrivateWitnessPoolRunner.common(lx));
        require(cycle.proofs.stream().anyMatch(p->unit(p,"q")&&p.support.sources.contains(source(la,loop))),"known head with an additional whole Source still needs its alternative");
    }
    static void delayedCheaperProof()throws Exception {
        String seed="!$w seed(w,1);",make="!@w flag(w,1) && ((flag(w,1)&&seed(w,1))->q(w,1)) && (seed(w,1)->r(w,1));",
            end="!@w (q(w,1)->out(w,1)) && ((r(w,1)&&seed(w,1))->out(w,1));";
        Snapshot x=bind(true,seed),a=bind(false,make),b=bind(false,end);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalDerivedReplay.Result replay=ContextUniversalDerivedReplay.replay(theory(x,a),input,16,WORK,500000);
        ContextUniversalDerivedRoutes.Result nativeProof=ContextUniversalDerivedRoutes.reconstruct(replay,16,16384,WORK);
        DerivedTransfer carried=ContextWitnessResultTransport.transferDerived(nativeProof,input,16,WORK);
        require(carried.transferred,"real graded input precursor: "+carried.reason);
        require(carried.output.items.stream().anyMatch(i->i.atom.predicate.equals("q")&&i.proofs.stream().anyMatch(s->s.depth==2)),"actual imported q is depth2");
        require(carried.output.items.stream().anyMatch(i->i.atom.predicate.equals("r")&&i.proofs.stream().anyMatch(s->s.depth==1)),"actual imported r is depth1");
        ContextUniversalUnitClosure.Result t=theory(x,b);
        Result bounded=ContextUniversalFrozenClosure.close(t,carried.output,16,2,512,256,WORK),full=close(t,carried.output);
        require(bounded.closed,"later shorter proof must discharge an earlier over-depth candidate: "+bounded.reason);
        ContextUniversalFrozenClosure.check(bounded,2,ContextGroundProductionRounds.budget(WORK));
        boolean out=false;for(Proof proof:bounded.proofs) {
            require(proof.support.depth<=2,"no over-limit proof is retained");
            if(unit(proof,"out")){out=true;require(proof.support.depth==2&&proof.support.sources.equals(set(source(x,seed),source(a,make),source(b,end))),"short out2 proof preserves the exact same whole Source group as long out3");}
        }
        require(out&&bounded.rounds>=3,"bounded saturation waits for a newly generated conditional before final depth check");
        require(bounded.proofs.size()==full.proofs.size(),"depth2 and depth16 return the same complete minimum ledger");
        for(Proof proof:full.proofs)require(bounded.proofs.stream().anyMatch(other->proof.support.equals(other.support)&&proof.clause.equivalent(other.clause,()->{})),"each minimum theorem survives the strict bound");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-closure-").toString());
        missingNative();standardizedApart();duplicatesAndOrigins();privateAndOrder();derivedDiagonal();alternatives();delayedCheaperProof();
        denied(ContextUniversalFrozenClosure.close(null,null,16,16,512,256,WORK),"missing sealed roots");
        System.out.println("CONTEXT_UNIVERSAL_FROZEN_CLOSURE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
