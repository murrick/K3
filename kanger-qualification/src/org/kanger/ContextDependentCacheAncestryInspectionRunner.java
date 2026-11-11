/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.units.*;
import org.kanger.interfaces.*;
import org.kanger.ContextDependentCacheAncestryInspection.*;

public final class ContextDependentCacheAncestryInspectionRunner {
    private static int checks;
    private static void need(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,620),1,true,Collections.emptyList(),1000);}
    private static void matrix(boolean full,int mode,boolean reverse)throws Exception{
        Mind m=memory();String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";
        String a="!@y rel(1,y,1)->out(1,y);",b="!@y rel("+(mode==0?2:1)+",y,"+(mode==1?2:1)+")->out(2,y);";
        need(m.compile(reverse?b+a+root:root+a+b,null,false),"automatic ancestry fixture");
        ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);Result result=ContextDependentCacheAncestryInspection.inspect(m,inventory);
        if(mode==2){
            need(result.status==Status.INCOMPLETE&&result.reason.contains("Different resolved cause alternatives"),"shared-instance native cause has unresolved and resolved alternatives");
            need(result.recipe==null&&result.bindings.isEmpty()&&result.aliases==null,"ambiguous native causes cannot issue a partial success receipt");
            need(before.equals(DmzObservationStateFingerprint.capture(m)),"unresolved shared-instance refusal is read-only");return;
        }
        need(result.status==Status.PARTIAL,"root instantiations are explicitly uncovered full="+full+" mode="+mode+" reverse="+reverse+": "+result.status+" "+result.reason);
        need(result.bindings.size()==2&&!result.unplanned.isEmpty()&&result.bindings.size()+result.unplanned.size()==inventory.generated.size(),"every actual occurrence classified, no silent root-cache omission");
        ContextDependentCacheAliasInspection.Status expected=full?(mode==2?ContextDependentCacheAliasInspection.Status.SPLIT:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED):(mode==0?ContextDependentCacheAliasInspection.Status.CONFLATED:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED);
        need(result.aliases!=null&&result.aliases.status==expected,"automatically resolved recipe exposes the correct alias relation: "+result.reason);
        need(before.equals(DmzObservationStateFingerprint.capture(m)),"automatic planning and independent recipe audit are read-only");
        Set<Long> targets=new HashSet<>();for(ContextDependentCacheAliasInspection.Binding binding:result.bindings)need(targets.add(binding.nativeRule),"unique demanded cache occurrence");
        boolean immutable=false;try{result.unplanned.clear();}catch(UnsupportedOperationException expectedFailure){immutable=true;}need(immutable,"unplanned occurrence ledger immutable");
        need(!inventory.dependentPrimaryView(),"automatic plan cannot grant symbolic cache primary-view authority");
    }
    private static void chain(boolean reverse)throws Exception{
        Mind m=memory();String root="!$u rel(1,u,1);",rules="!@u rel(1,u,1)->middle(1,u); !@u middle(1,u)->tail(1,u);";
        need(m.compile(reverse?rules+root:root+rules,null,false),"closed root two-hop fixture");ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);
        Result result=ContextDependentCacheAncestryInspection.inspect(m,inventory);
        need(result.status==Status.PLANNED&&result.bindings.size()==inventory.generated.size()&&result.bindings.size()==2&&result.unplanned.isEmpty(),"all generated two-hop occurrences planned: "+result.reason);
        need(result.recipe.steps.size()==2&&!result.recipe.steps.get(1).donors.get(0).original&&result.recipe.steps.get(1).donors.get(0).reference==0,"resolved generated donor becomes an earlier product, not original knowledge");
        need(result.aliases.status==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"closed chain keeps selected witness identity");
        need(before.equals(DmzObservationStateFingerprint.capture(m))&&!inventory.dependentPrimaryView(),"planned ledger is immutable observation, not cache admission");
        // Cause metadata is included in the physical fingerprint.
        Rule generated=(Rule)m.getRules().get(result.bindings.get(0).nativeRule);IRule producer=generated.getCauses().iterator().next().getRule(m);generated.getCauses().clear();boolean stale=false;
        try{ContextDependentCacheAncestryInspection.inspect(m,inventory);}catch(IllegalStateException expectedFailure){stale=true;}need(stale,"changed actual cause invalidates old inventory");
        need(ContextDependentCacheAncestryInspection.inspect(m,inventory(m)).status==Status.INCOMPLETE,"fresh inventory cannot reconstruct a cache with missing causes");
        org.kanger.primitives.Cause cycle=new org.kanger.primitives.Cause();Domain domain=generated.getDomain();cycle.setPersistentState(producer.getId(),new org.kanger.primitives.Solve(domain.getPredicate(m),true,domain.getArguments().convertBase(m)));generated.getCauses().add(cycle);
        ContextProductionInventory cyclic=inventory(m);String cyclicState=DmzObservationStateFingerprint.capture(m);Result refused=ContextDependentCacheAncestryInspection.inspect(m,cyclic);
        need(refused.status==Status.INCOMPLETE&&refused.reason.contains("Cyclic"),"cyclic actual cause graph refuses instead of recursing indefinitely");
        need(cyclicState.equals(DmzObservationStateFingerprint.capture(m)),"cyclic refusal preserves actual native state");
    }
    private static void alternatives()throws Exception{
        Mind m=memory();need(m.compile("!$u rel(1,u,1); !@u rel(1,u,1)->out(1,u); !@u rel(1,u,1)->other(1,u);",null,false),"alternative endpoint fixture");
        Rule out=null,other=null;for(IRule item:m.getRules())if(item.isGenerated()){Rule r=(Rule)item;String predicate=r.getDomain().getPredicate(m).getName(m);if(predicate.equals("out"))out=r;else if(predicate.equals("other"))other=r;}
        need(out!=null&&other!=null,"physical endpoint caches");out.getCauses().addAll(other.getCauses());ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);
        Result result=ContextDependentCacheAncestryInspection.inspect(m,inventory);need(result.status==Status.INCOMPLETE&&result.recipe==null&&result.bindings.isEmpty(),"different current cause endpoints never silently select one proof");
        need(before.equals(DmzObservationStateFingerprint.capture(m)),"ambiguous ancestry refusal is read-only");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-cache-ancestry-").toString());
        for(boolean full:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean reverse:new boolean[]{false,true})matrix(full,mode,reverse);
        chain(false);chain(true);alternatives();
        System.out.println("CONTEXT_DEPENDENT_CACHE_ANCESTRY_INSPECTION_PASS checks="+checks);
    }
}
