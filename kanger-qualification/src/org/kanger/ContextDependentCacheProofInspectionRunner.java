/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.primitives.*;
import org.kanger.ContextDependentCacheProofInspection.*;

public final class ContextDependentCacheProofInspectionRunner {
    private static int checks;
    private static final ContextDependentReplayInspection.Policy P=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    private static void need(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,623),1,true,Collections.emptyList(),1000);}
    private static void matrix(boolean full,int mode,boolean reverse)throws Exception{
        Mind m=memory();String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);",a="!@y rel(1,y,1)->out(1,y);",b="!@y rel("+(mode==0?2:1)+",y,"+(mode==1?2:1)+")->out(2,y);";
        need(m.compile(reverse?b+a+root:root+a+b,null,false),"primary proof fixture");ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);
        if(mode==2)need(ContextDependentCacheLedgerInspection.inspect(m,inventory).status==ContextDependentCacheLedgerInspection.Status.INCOMPLETE,"old current-cause path retains its unresolved-alternative refusal");
        Result result=ContextDependentCacheProofInspection.inspect(m,inventory);need(result.status==Status.INSPECTED,"independent primary-only proofs full="+full+" mode="+mode+" reverse="+reverse+": "+result.reason);
        need(result.bindings.size()==2&&result.ledger.productRows==2&&result.ledger.rootRows==(mode==2?1:2)&&result.rounds==1,"all demanded rows proved after one fresh discovery pass");
        ContextDependentCacheAliasInspection.Status expected=full?(mode==2?ContextDependentCacheAliasInspection.Status.SPLIT:ContextDependentCacheAliasInspection.Status.CONFLATED_AND_SPLIT):(mode==0?ContextDependentCacheAliasInspection.Status.CONFLATED:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED);
        need(result.ledger.aliases==expected,"independent proof route preserves joint native witness diagnostics");need(before.equals(DmzObservationStateFingerprint.capture(m))&&!inventory.dependentPrimaryView(),"independent proof search is read-only and grants no cache admission");
        ContextDependentProductionInspection.Result reopened=ContextDependentProductionReplay.reopen(result.proof.bytes(),result.proof.pin,P,1000);need(reopened.products.size()==2&&reopened.originals.size()==3,"saved proof contains the three whole originals, never cached rows as knowledge");
        boolean immutable=false;try{result.bindings.clear();}catch(UnsupportedOperationException expectedFailure){immutable=true;}need(immutable,"proof bindings immutable");
        need(m.compile("!unrelated(9);",null,false),"actual independent mutation");boolean stale=false;try{ContextDependentCacheProofInspection.inspect(m,inventory);}catch(IllegalStateException expectedFailure){stale=true;}need(stale,"old physical proof target cannot authorize changed inventory");
    }
    private static void dangling(boolean reverse)throws Exception{
        Mind m=memory();String root="!$u rel(1,u,1);",rules="!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);";need(m.compile(reverse?rules+root:root+rules,null,false),"two-hop dangling donor fixture");Rule tail=null;for(IRule item:m.getRules())if(item.isGenerated()&&((Rule)item).getDomain().getPredicate(m).getName(m).equals("tail"))tail=(Rule)item;need(tail!=null,"physical tail cache");
        long producer=tail.getCauses().iterator().next().getRule(m).getId();Domain domain=tail.getDomain();Cause dangling=new Cause();dangling.setPersistentState(producer,new Solve(domain.getPredicate(m),false,domain.getArguments().convertBase(m)));tail.getCauses().clear();tail.getCauses().add(dangling);
        ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);need(ContextDependentCacheLedgerInspection.inspect(m,inventory).status==ContextDependentCacheLedgerInspection.Status.INCOMPLETE,"dangling current donor is not fabricated as native history");Result result=ContextDependentCacheProofInspection.inspect(m,inventory);
        need(result.status==Status.INSPECTED&&result.rounds==2&&result.ledger.aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED&&result.bindings.size()==2,"two fresh discovery barriers recover both demanded products: "+result.reason);
        need(before.equals(DmzObservationStateFingerprint.capture(m)),"dangling cause metadata stays unchanged");for(IRule item:m.getRules())if(item.isGenerated())item.getCauses().clear();ContextDependentProductionInspection.Result reopened=ContextDependentProductionReplay.reopen(result.proof.bytes(),result.proof.pin,P,1000);need(reopened.products.size()==2&&reopened.originals.size()==3,"portable original-only proof survives loss of native cause metadata");
        need(ContextDependentCacheProofInspection.inspect(m,inventory(m)).status==Status.INCOMPLETE,"loss of root/projection classification is still an explicit separate boundary");
    }
    private static void refusals()throws Exception{
        Mind m=memory();need(m.compile("!$u first(u); !$v second(v); !@u first(u)->out(u); !@v second(v)->out(v);",null,false),"ambiguous witness proof fixture");ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);Result result=ContextDependentCacheProofInspection.inspect(m,inventory);need(result.status==Status.INCOMPLETE&&result.reason.contains("Different primary witness proofs"),"distinct witness-producing proofs are not silently collapsed into one binding");need(result.proof==null&&result.ledger==null&&before.equals(DmzObservationStateFingerprint.capture(m)),"ambiguous binding returns no proof receipt and does not mutate runtime");
        m=memory();need(m.compile("!$u rel(1,u,1); !@u rel(1,u,1)->out(1,u); !marker(9);",null,false),"unproved cache target fixture");Rule out=null;ITerm nine=null;for(IRule item:m.getRules()){Rule row=(Rule)item;String predicate=row.getDomain().getPredicate(m).getName(m);if(item.isGenerated()&&predicate.equals("out"))out=row;if(!item.isGenerated()&&predicate.equals("marker"))nine=row.getDomain().getArguments().get(0).getValue(m);}need(out!=null&&nine!=null,"actual ordinary cache slot available");out.getDomain().getArguments().set(0,new Argument(nine));inventory=inventory(m);before=DmzObservationStateFingerprint.capture(m);result=ContextDependentCacheProofInspection.inspect(m,inventory);need(result.status==Status.INCOMPLETE&&result.proof==null,"unentailed ordinary cache value cannot be assumed to close coverage");need(before.equals(DmzObservationStateFingerprint.capture(m)),"missing-proof refusal is read-only");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-cache-proof-").toString());for(boolean full:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean reverse:new boolean[]{false,true})matrix(full,mode,reverse);dangling(false);dangling(true);refusals();System.out.println("CONTEXT_DEPENDENT_CACHE_PROOF_INSPECTION_PASS checks="+checks);
    }
}
