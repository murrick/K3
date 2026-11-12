/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextDependentSymbolicFamilyInspection.Member;

public final class ContextDependentSymbolicFamilyInspectionRunner {
    private static int checks;
    private static final String CACHED="!$u rel(1,u,1); !@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);";
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory(String text)throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);if(!text.isEmpty())need(m.compile(text,null,false),"family fixture declarations");return m;}
    private static ContextDependentSymbolicCacheSnapshot.Snapshot snap(Mind m,long id,boolean local)throws Exception{ContextDependentSymbolicCacheSnapshot.Result r=ContextDependentSymbolicCacheSnapshot.capture(m,ContextProductionInventory.inspect(m,new UUID(0,id),1,local,Collections.emptyList(),1000));need(r.status==ContextDependentSymbolicCacheSnapshot.Status.BOUND,"fresh family snapshot: "+r.reason);return r.snapshot;}
    private static void pair(boolean localCache,boolean conflict)throws Exception{
        Mind x=memory(localCache?CACHED:(conflict?"!@u ~tail(u);":"!marker(9);")),a=memory(localCache?(conflict?"!@u ~tail(u);":"!marker(9);"):CACHED);
        ContextDependentSymbolicCacheSnapshot.Snapshot xs=snap(x,6271,true),as=snap(a,6272,false);String beforeX=DmzObservationStateFingerprint.capture(x),beforeA=DmzObservationStateFingerprint.capture(a);
        ContextDependentModelSearchInspection.Result result=ContextDependentSymbolicFamilyInspection.inspect(x,xs,Collections.singletonList(new Member(a,as)));
        need(result.status==(conflict?ContextDependentModelSearchInspection.Status.REFUTED:ContextDependentModelSearchInspection.Status.MODELLED_PENDING),"joint cached family localCache="+localCache+" conflict="+conflict+": "+result.status+"/"+result.phase+"/"+result.reason);
        if(conflict){need(result.family!=null&&result.family.status==ContextDependentIterationInspection.Status.COLLISION&&result.models.isEmpty(),"real sourced receiving refutation, not failed model guessing");boolean sourced=false;
            for(ContextDependentRoundInspection.Barrier barrier:result.family.checked.barriers)for(ContextDependentProductionInspection.Result cut:barrier.cuts.values())for(ContextDependentProductionInspection.Refutation ref:cut.finalUnitRefutations){boolean left=false,right=false;for(Source source:ref.sources){ContextProductionInventory inventory=source.context.equals(xs.inventory.context)?xs.inventory:as.inventory;need(inventory.primary.stream().anyMatch(row->row.nativeRule==source.rule),"refutation uses actual original IDs, never cached row axioms");need(source.localX==inventory.localX&&source.view.contains(inventory.fingerprint),"actual role and full physical fingerprint retained");left|=source.context.equals(xs.inventory.context);right|=source.context.equals(as.inventory.context);}sourced|=left&&right;}need(sourced,"conflict support spans X and the receiving independent member");
        }else{need(!result.models.isEmpty()&&result.family!=null&&result.originalRows==2*xs.inventory.primary.size()+as.inventory.primary.size(),"complete all-value original/final/indexed model checks exclude the two covered cache rows from original axioms");need(result.finalObligations==result.runtimeObligations,"final and indexed/native obligation counts agree");}
        need(beforeX.equals(DmzObservationStateFingerprint.capture(x))&&beforeA.equals(DmzObservationStateFingerprint.capture(a)),"family inspection leaves actual contexts unchanged");ContextProductionInventory cached=localCache?xs.inventory:as.inventory;need(cached.generated.size()==2&&!cached.dependentPrimaryView()&&cached.groundCacheProof()==null,"raw symbolic inventory never promoted or recast as ground proof");
    }
    private static void independent(boolean reverse)throws Exception{
        Mind x=memory(""),a=memory("!p(1);"),b=memory("!~p(1);");ContextDependentSymbolicCacheSnapshot.Snapshot xs=snap(x,6273,true),as=snap(a,6274,false),bs=snap(b,6275,false);List<Member> members=Arrays.asList(new Member(a,as),new Member(b,bs));if(reverse)Collections.reverse(members);
        ContextDependentModelSearchInspection.Result result=ContextDependentSymbolicFamilyInspection.inspect(x,xs,members);need(result.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&result.models.size()==3,"independent A/B disagreement is retained in distinct X/A and X/B models in both orders: "+result.reason);need(result.originalRows==2,"empty X and the two independent originals remain separate");
    }
    private static void guards()throws Exception{
        Mind x=memory("!marker(1);"),a=memory(CACHED);ContextDependentSymbolicCacheSnapshot.Snapshot xs=snap(x,6276,true),as=snap(a,6277,false);ContextProductionInventory scoped=as.diagnosticPrimaryView(a);need(scoped.dependentPrimaryView()&&!as.inventory.dependentPrimaryView(),"checked primary projection is a distinct internal diagnostic inventory");
        ContextDependentForeignCacheSnapshotRunner.withNoGood(a,()->{boolean rejected=false;try{ContextDependentReplayInspection.currentMember(a,scoped,new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000));}catch(IllegalStateException expected){rejected=true;}need(rejected,"old scoped projection cannot be reused in a restricted branch despite unchanged rows");});
        need(a.compile("!extra(9);",null,false),"actual member mutation");boolean stale=false;try{ContextDependentSymbolicFamilyInspection.inspect(x,xs,Collections.singletonList(new Member(a,as)));}catch(IllegalStateException expected){stale=true;}need(stale,"stale actual member snapshot cannot enter family inspection");
    }
    private static void nullaryPalette()throws Exception{
        Mind x=memory("!~tail(9);"),a=memory(CACHED);ContextDependentSymbolicCacheSnapshot.Snapshot xs=snap(x,62781,true),as=snap(a,62782,false);String before=DmzObservationStateFingerprint.capture(a);
        ContextDependentModelSearchInspection.Result result=ContextDependentSymbolicFamilyInspection.inspect(x,xs,Collections.singletonList(new Member(a,as)));
        need(result.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&result.tried<20000,"known compatible witness-avoidance example gets an independently checked palette candidate before the former table-search bound: "+result.status+" "+result.reason);
        need(!result.models.isEmpty()&&result.finalObligations==result.runtimeObligations&&before.equals(DmzObservationStateFingerprint.capture(a)),"palette proposal passes full independent truth checks without mutating actual member state");
        for(org.kanger.interfaces.IRule item:a.getRules())if(!item.isGenerated()&&item.getId()==0)need(((org.kanger.units.Term)((org.kanger.units.Rule)item).getDomain().getArguments().get(1).getValue(a)).getParent(a)==null,"new model candidate never assigns the actual native witness");
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("symbolic-family-core-").toString());for(boolean local:new boolean[]{false,true})for(boolean conflict:new boolean[]{false,true})pair(local,conflict);independent(false);independent(true);guards();nullaryPalette();System.out.println("CONTEXT_DEPENDENT_SYMBOLIC_FAMILY_INSPECTION_PASS checks="+checks);}
}
