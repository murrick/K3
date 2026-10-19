/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;

/** Capacity belongs to one immutable input; no pruning or global policy change. */
public final class ContextResidualRequestLimitsRunner {
    static int checks,ids;static final int WORK=64000000;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static Snapshot snapshot(boolean x,String text)throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);require(m.compile(text,null,false),"native request fixture");
        ContextProductionNativeBinding.Result r=ContextProductionNativeBinding.bind(m,ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000),WORK,100000);require(r.bound,"whole request binding");return r.snapshot;
    }
    static String theory(int n){StringBuilder s=new StringBuilder();for(int i=1;i<n;i++)s.append("!p").append(i).append("(1);");s.append("!q(1)");for(int i=1;i<n;i++)s.append("&&p").append(i).append("(1)");return s+"->out(1);";}
    static ContextGroundClauseFamily.Evaluation family(Snapshot x,List<Snapshot> cuts,LedgerLimits limits){return ContextGroundClauseFamily.evaluate(x,cuts,16,16,16,WORK,500000,100000,1000000,limits);}
    static boolean sameProducts(List<Product> a,List<Product> b){return a.size()==b.size()&&a.stream().allMatch(p->b.stream().anyMatch(q->p.clause.equivalent(q.clause)&&p.proof.equals(q.proof)));}
    static boolean sameModels(ContextGroundClauseFamily.Evaluation a,ContextGroundClauseFamily.Evaluation b){
        if(a.certificate.models.size()!=b.certificate.models.size())return false;
        for(ContextGroundClauseFamily.Model m:a.certificate.models){
            ContextGroundClauseFamily.Model n=b.certificate.models.stream().filter(t->t.target==m.target).findFirst().orElse(null);
            if(n==null||m.positiveAtoms.size()!=n.positiveAtoms.size()||!m.positiveAtoms.stream().allMatch(f->n.positiveAtoms.stream().anyMatch(f::equivalent)))return false;
        }
        return true;
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("request-limits-").toString());
        for(int[] bad:new int[][]{{0,1},{1,0},{-1,2},{2,-1}}){boolean refused=false;try{new LedgerLimits(bad[0],bad[1]);}catch(IllegalArgumentException expected){refused=true;}require(refused,"nonpositive request capacity rejected");}
        LedgerLimits loose=new LedgerLimits(256,1024);require(LedgerLimits.DEFAULT.clauses==64&&LedgerLimits.DEFAULT.proofs==256,"legacy capacity unchanged");
        Snapshot a=snapshot(false,"!q(1);"),safe=snapshot(false,"!safe(1);");List<Snapshot> cuts=Arrays.asList(a,safe);
        for(int n:new int[]{3,4}){
            Snapshot x=snapshot(true,theory(n));ContextGroundClauseFamily.Evaluation old=family(x,cuts,LedgerLimits.DEFAULT),expanded=family(x,cuts,loose);
            require(old.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&expanded.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT,"both complete ground requests: "+old.reason+" / "+expanded.reason);
            require(expanded.certificate.frontier.ledgerLimits==loose&&old.certificate.frontier.ledgerLimits==LedgerLimits.DEFAULT,"every complete barrier preserves its own immutable capacity object");
            require(sameProducts(old.certificate.frontier.products,expanded.certificate.frontier.products),"larger capacity preserves all clauses, Source alternatives and depths");require(sameModels(old,expanded),"all per-cut models agree");
            ContextGroundClauseFamily.Evaluation small=family(x,cuts,new LedgerLimits(1,8));require(small.status==ContextGroundClauseFamily.Status.INCOMPLETE&&small.certificate==null&&small.reason.contains("Residual clause limit: clauses=1, proofs=1"),"explicit smaller clause capacity reaches complete failure cause");
            ContextGroundClauseFamily.Evaluation proofs=family(x,cuts,new LedgerLimits(256,2));require(proofs.status==ContextGroundClauseFamily.Status.INCOMPLETE&&proofs.certificate==null&&proofs.reason.contains("Residual proof limit: proofs=2, clauses=2"),"proof capacity is independent of clause capacity");
            require(family(x,cuts,LedgerLimits.DEFAULT).status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT,"small request cannot contaminate a later legacy request");
        }
        Snapshot wide=snapshot(true,theory(7));Frontier expanded=ContextOrdinaryResidualRounds.start(wide,cuts,WORK,loose);Receipt all=ContextOrdinaryResidualRounds.inspect(expanded,a,16,WORK,500000);
        require(all.verified&&all.localProofs.size()==135&&all.products.size()==127,"explicit capacity preserves complete135-row ordinary A-X pass: "+all.reason+" rows="+all.localProofs.size());
        require(all.products.stream().allMatch(p->p.proof.depth==1&&p.proof.sources.size()==9-p.clause.literals.size()),"every one-step subset retains all exact donors and producer, without source/depth reset");
        ContextGroundClauseFamily.Evaluation bounded=family(wide,cuts,new LedgerLimits(128,1024));require(bounded.status==ContextGroundClauseFamily.Status.INCOMPLETE&&bounded.certificate==null&&bounded.collisions.isEmpty()&&bounded.reason.contains("Residual clause limit: clauses=128, proofs=128"),"complete family uses the requested capacity across local phases without fallback: "+bounded.reason);
        Frontier narrow=ContextOrdinaryResidualRounds.start(wide,cuts,WORK);Receipt stopped=ContextOrdinaryResidualRounds.inspect(narrow,a,16,WORK,500000);require(!stopped.verified&&stopped.products.isEmpty()&&stopped.reason.equals("Residual clause limit: clauses=64, proofs=64"),"same fixture legacy request remains bounded");
        Snapshot smallX=snapshot(true,theory(3));Frontier f=ContextOrdinaryResidualRounds.start(smallX,cuts,WORK,loose);List<Receipt> outputs=new ArrayList<>();for(Snapshot cut:f.cuts)outputs.add(ContextOrdinaryResidualRounds.inspect(f,cut,16,WORK,500000));
        Barrier merged=ContextOrdinaryResidualRounds.merge(f,outputs,WORK);require(merged.status==Status.MERGED&&merged.frontier.ledgerLimits==loose,"ordinary barrier propagates the capacity unchanged");
        Frontier other=ContextOrdinaryResidualRounds.start(smallX,cuts,WORK,new LedgerLimits(256,1024));Barrier wrong=ContextOrdinaryResidualRounds.merge(other,outputs,WORK);require(wrong.status==Status.INCOMPLETE&&wrong.frontier==null&&wrong.reason.contains("scope/input/evidence mismatch"),"equal-valued or different-capacity request cannot reuse old receipts");
        boolean refused=false;try{ContextOrdinaryResidualRounds.start(smallX,cuts,WORK,null);}catch(IllegalArgumentException expected){refused=true;}require(refused,"null policy cannot silently pick default");
        refused=false;try{family(smallX,cuts,null);}catch(IllegalArgumentException expected){refused=true;}require(refused,"family entrypoint rejects null explicit policy");
        System.out.println("CONTEXT_RESIDUAL_REQUEST_LIMITS_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
