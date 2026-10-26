/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalFrozenCoverage.*;
import org.kanger.ContextUniversalFrozenCoverage.Result;
import org.kanger.ContextWitnessResultTransport.*;

/** A closed positive projection cannot silently cover omitted original private witness rows. */
public final class ContextUniversalFrozenCoverageRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String s){return ContextPrivateWitnessPoolRunner.source(x,s);}
    static ContextUniversalFrozenClosure.Result close(Snapshot x,Snapshot a,Packet common,PrivatePool pool) {
        ContextUniversalUnitClosure.Result schema=ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);require(schema.unitClosed,"schema precursor "+schema.reason);
        ContextUniversalFrozenClosure.Result r=pool==null?ContextUniversalFrozenClosure.close(schema,common,16,16,512,256,WORK):ContextUniversalFrozenClosure.closePrivate(schema,pool,16,16,512,256,WORK);require(r.closed,"typed precursor "+r.reason);return r;
    }
    static Result audit(ContextUniversalFrozenClosure.Result input,Snapshot x,Snapshot a,int depth) {
        Result r=ContextUniversalFrozenCoverage.inspect(input,x,a,depth,65536,WORK);require(r.covered,"whole typed projection audit "+r.reason);
        require(r.input==input&&r.x==x&&r.foreign==a,"actual ledger and native snapshot tokens retained");
        require(r.rows==x.primary.size()+(a==null?0:a.primary.size()),"every whole native row accounted exactly once");
        int inputs=0;for(Item item:input.frame.local.items)inputs+=item.proofs.size();require(r.inputAlternatives==inputs,"every actual input proof alternative covered");
        require(r.obligations.size()==input.obligations&&r.attempts>=r.obligations.size(),"all final stable unit/Horn/pivot obligations independently enumerated");
        for(Obligation o:r.obligations) {
            require(input.proofs.contains(o.unit)&&input.proofs.contains(o.horn)&&input.proofs.contains(o.cover),"actual sealed proof parents/cover, never alleged labels");
            require(o.expected.equivalent(o.cover.clause,()->{})&&o.cover.support.sources.equals(o.support.sources)&&o.cover.support.depth<=o.support.depth,"exact whole Source alternative with equal/shorter proved grade");
            Set<Source> sources=new HashSet<>(o.unit.support.sources);sources.addAll(o.horn.support.sources);require(o.support.sources.equals(sources)&&o.support.depth==Math.max(o.unit.support.depth,o.horn.support.depth)+1,"expected obligation grade/source union recomputed");
        }
        return r;
    }
    static void denied(Result r,String why){require(!r.covered&&r.rows==0&&r.clauses==0&&r.witnessAtoms==0&&r.inputAlternatives==0&&r.attempts==0&&r.obligations.isEmpty(),why+": "+r.reason);}
    static boolean unit(Proof p,String predicate){return p.clause.literals.size()==1&&p.clause.literals.get(0).predicate.equals(predicate);}
    static void inventoryHole()throws Exception {
        Snapshot x=bind(true,"!anchor(1); !@w seed(w,1)->out(w,1);"),a=bind(false,"!$w seed(w,1);");Packet input=ContextPrivateWitnessPoolRunner.common(x);
        ContextUniversalFrozenClosure.Result common=close(x,a,input,null);require(common.proofs.stream().noneMatch(p->unit(p,"out")),"closed common-only projection has no private seed/out");
        denied(ContextUniversalFrozenCoverage.inspect(common,x,a,16,65536,WORK),"a separate witness Source cannot count as a covered original row");
        PrivatePool pool=ContextWitnessResultTransport.privatePool(input,x,a,WORK);ContextUniversalFrozenClosure.Result local=close(x,a,input,pool);Result r=audit(local,x,a,16);
        require(r.rows==3&&r.clauses==2&&r.witnessAtoms==1&&r.inputAlternatives==2,"full original inventory includes private witness atom plus X anchor/consumer");
        require(local.proofs.stream().anyMatch(p->unit(p,"out")&&p.support.depth==1),"proper local input enables the genuinely missing out1");
        require(input.items.size()==1&&pool.local.items.stream().noneMatch(i->i.atom.predicate.equals("out")),"coverage audit mutates no Packet or private input");
        Snapshot wrong=bind(false,"!$w seed(w,1);");denied(ContextUniversalFrozenCoverage.inspect(local,x,wrong,16,65536,WORK),"same formula in another physical context cannot replace native owner");
    }
    static void completePairsAndBounds()throws Exception {
        String seed="!$w seed(w,1)&&q(7,1);",consumer="!@w seed(w,1)&&rel(w,7)->out(w,1);",p="!@u p(u,1);",rel="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot x=bind(true,seed+consumer),a=bind(false,p+rel);Packet common=ContextPrivateWitnessPoolRunner.common(x);ContextUniversalFrozenClosure.Result input=close(x,a,common,null);Result r=audit(input,x,a,3);
        require(r.rows==4&&r.clauses==3&&r.witnessAtoms==2,"whole mixed ordinary/universal/bundle inventory");
        require(r.obligations.size()>1&&r.obligations.stream().anyMatch(o->o.support.depth>3&&o.cover.support.depth<=3),"over-bound candidate may be covered by a shorter same-Source theorem");
        require(r.obligations.stream().anyMatch(o->o.unit.schema==null&&o.unit.input==null||o.horn.schema==null&&o.horn.input==null),"new derived parents participate, not only original schema/input pairs");
        require(input.proofs.stream().anyMatch(t->t.clause.variables.size()>0&&t.clause.literals.size()==1),"universals remain symbolic over all admissible values");
        denied(ContextUniversalFrozenCoverage.inspect(input,x,a,2,65536,WORK),"owning DAG depth bound no prefix");
        denied(ContextUniversalFrozenCoverage.inspect(input,x,a,16,1,WORK),"later obligation limit leaves no earlier covered prefix");
        denied(ContextUniversalFrozenCoverage.inspect(input,x,a,16,65536,1),"audit work limit leaves no prefix");
        boolean immutable=false;try{r.obligations.clear();}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"immutable coverage obligations");
    }
    static void trustedAndWholeRows()throws Exception {
        String seed="!$w seed(w,1);",gate="!@v gate(v,1);",rel="!@u @v seed(u,1)&&gate(v,1)->rel(u,v);",out="!@w rel(w,7)->out(w,1);";
        Snapshot x=bind(true,"!anchor(1);"+out),a=bind(false,seed+gate+"!privateValue(7);"),b=bind(false,rel);Packet input=ContextPrivateWitnessPoolRunner.common(x);
        int obligations=-1;
        for(boolean reverse:new boolean[]{false,true}) {
            Snapshot target=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);PrivatePool pool=ContextWitnessResultTransport.privatePool(input,x,target,WORK);
            ContextUniversalFrozenClosure.Result local=close(x,target,input,pool);Result r=audit(local,x,target,16);
            require(r.rows==6&&r.witnessAtoms==1,"trusted group accounts physical member rows, no synthetic group Source");
            if(obligations>=0)require(obligations==r.obligations.size(),"trusted member order preserves full pair obligation count");obligations=r.obligations.size();
            Snapshot copy=ContextProductionNativeBinding.trusted("own",target.members,WORK);require(Cut.of(copy).equals(Cut.of(target)),"equal-cut replacement fixture");
            denied(ContextUniversalFrozenCoverage.inspect(local,x,copy,16,65536,WORK),"equal cut metadata cannot replace exact owning private snapshots");
        }
        PrivatePool own=ContextWitnessResultTransport.privatePool(input,x,a,WORK);ContextUniversalFrozenClosure.Result independent=close(x,a,input,own);audit(independent,x,a,16);
        require(independent.proofs.stream().noneMatch(t->unit(t,"out")),"independent A cannot borrow B's rel rule");
        String combined="!@w (seed(w,1)->q(w,1)) && (seed(w,1)->r(w,1));";Snapshot sx=bind(true,seed),sa=bind(false,combined);
        ContextUniversalFrozenClosure.Result rows=close(sx,sa,ContextPrivateWitnessPoolRunner.common(sx),null);Result r=audit(rows,sx,sa,16);
        require(sa.primary.size()==1&&r.rows==2&&r.clauses==2,"two ordinary clauses remain one whole original Source row");
        for(Proof t:rows.proofs)if(unit(t,"q")||unit(t,"r"))require(t.support.sources.equals(new HashSet<>(Arrays.asList(source(sx,seed),source(sa,combined)))),"no synthetic per-clause Source in covered products");
    }
    static void carriedIsNotRaw()throws Exception {
        String seed="!$w seed(w,1);",rule="!@w seed(w,1)->seed(w,1);";Snapshot x=bind(true,"!anchor(1);"+rule),a=bind(false,seed);Packet input=ContextPrivateWitnessPoolRunner.common(x),carried;
        PrivatePool pool=ContextWitnessResultTransport.privatePool(input,x,a,WORK);
        try(ContextWitnessSeedReplay pass=ContextWitnessSeedReplay.openPrivate(pool,WORK,500000)) {
            pass.rule(source(x,rule),rule);ContextWitnessResultTransport.Result next=ContextWitnessResultTransport.exportNext(pass,16,WORK);require(next.exported,"actual carried same-atom precursor "+next.reason);carried=next.packet;
        }
        require(carried.items.stream().anyMatch(i->i.atom.predicate.equals("seed")&&i.proofs.stream().allMatch(p->p.depth>0&&p.sources.size()==2)),"same original witness/atom present only via real X-dependent proof");
        ContextUniversalFrozenClosure.Result common=close(x,a,carried,null);denied(ContextUniversalFrozenCoverage.inspect(common,x,a,16,65536,WORK),"carried atom/origin alone cannot cover raw depth0 private row");
        PrivatePool actual=ContextWitnessResultTransport.privatePool(carried,x,a,WORK);audit(close(x,a,carried,actual),x,a,16);
    }
    static void cyclesAndScope()throws Exception {
        String seed="!$w seed(w,1);",q="!@w seed(w,1)->q(w,1);",loop="!@w q(w,1)->q(w,1);";
        Snapshot x=bind(true,seed),a=bind(false,q+loop);ContextUniversalFrozenClosure.Result input=close(x,a,ContextPrivateWitnessPoolRunner.common(x),null);Result r=audit(input,x,a,2);
        require(r.obligations.stream().anyMatch(o->o.support.depth>2&&o.cover.support.depth==2),"cyclic same-Source obligation is discharged by existing minimum, not rejected on candidate grade");
        require(input.proofs.stream().anyMatch(t->unit(t,"q")&&t.support.sources.contains(source(a,loop))),"additional cyclic whole Source alternative not subset-pruned");
        Snapshot ux=bind(true,"!anchor(1);"),ua=bind(false,"!@v p(v,1);");Result symbolic=audit(close(ux,ua,ContextPrivateWitnessPoolRunner.common(ux),null),ux,ua,16);
        require(symbolic.obligations.isEmpty()&&symbolic.input.proofs.stream().anyMatch(t->unit(t,"p")&&!t.clause.variables.isEmpty()),"zero pair obligations do not instantiate a universal or issue family authority");
        Snapshot bad=bind(false,"!@v p(v,1); !~bad(7);");ContextUniversalUnitClosure.Result invalid=ContextUniversalUnitClosure.close(ux,bad,16,16,512,256,WORK);
        ContextUniversalFrozenClosure.Result failed=ContextUniversalFrozenClosure.close(invalid,ContextPrivateWitnessPoolRunner.common(ux),16,16,512,256,WORK);
        denied(ContextUniversalFrozenCoverage.inspect(failed,ux,bad,16,65536,WORK),"later negative/unsupported row cannot leave positive covered prefix");
    }
    static void twoOriginsAndIncompatible()throws Exception {
        Snapshot x=bind(true,"!$u $v pair(u,v);"),a=bind(false,"!@u @v pair(u,v)->good(u,v); !@u pair(u,u)->bad(u);");
        ContextUniversalFrozenClosure.Result input=close(x,a,ContextPrivateWitnessPoolRunner.common(x),null);Result r=audit(input,x,a,16);
        require(r.witnessAtoms==1&&r.attempts>r.obligations.size(),"distinct-origin diagonal is considered and rejected by unification, not omitted");
        require(input.proofs.stream().anyMatch(t->unit(t,"good")&&t.clause.origins.size()==2),"two original binders remain distinct in covered products");
        require(input.proofs.stream().noneMatch(t->unit(t,"bad")),"projection coverage never supplies an unproved equality between origins");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("frozen-coverage-").toString());inventoryHole();completePairsAndBounds();trustedAndWholeRows();carriedIsNotRaw();cyclesAndScope();twoOriginsAndIncompatible();
        denied(ContextUniversalFrozenCoverage.inspect(null,null,null,16,65536,WORK),"missing sealed/native frame");
        System.out.println("CONTEXT_UNIVERSAL_FROZEN_COVERAGE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
