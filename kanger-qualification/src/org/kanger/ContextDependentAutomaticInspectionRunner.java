/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Automatic ALL-other selection must cover every physical cut without native compiler writes. */
public final class ContextDependentAutomaticInspectionRunner {
    private static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static Member member(Mind m,int id,boolean local,List<String> init)throws Exception{return new Member(m,ContextProductionInventory.inspect(m,new UUID(0,id),1,local,init,1000));}
    private static ContextDependentModelSearchInspection.Result inspect(Member x,List<Cut> cuts,int points)throws Exception{
        return ContextDependentAutomaticInspection.inspect(x,cuts,
                new ContextDependentIterationInspection.Request(new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000),10000,8),
                new ContextDependentModelSearchInspection.Limits(points,100000,100000,128,64000000,64000000,64000000,64000000,64000000),
                new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,64000000,64000000,64000000,64000000));
    }
    private static SemanticTermSnapshot string(String value)throws Exception{Mind m=memory();Rule r=raw(m,"!value(\""+value+"\");");return SemanticTermSnapshot.capture(r.getDomain().get(0).getValue(m));}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("dependent-auto-palette-").toString());
        Mind m=memory(),a=memory(),b=memory();
        raw(m,"!tag(\"__kanger_all_other_0\");");raw(a,"!tag(\"__kanger_all_other_1\");");raw(b,"!tag(\"__kanger_all_other_2\");");
        Member x=member(m,6131,true,Collections.emptyList()),aa=member(a,6132,false,Collections.emptyList()),bb=member(b,6133,false,Collections.emptyList());
        List<Cut> cuts=Arrays.asList(new Cut(null,Collections.singletonList(aa)),new Cut(null,Collections.singletonList(bb)));
        int terms=m.getTerms().size()+a.getTerms().size()+b.getTerms().size();String state=DmzObservationStateFingerprint.capture(m);
        ContextDependentModelSearchInspection.Result r=inspect(x,cuts,2);
        require(r.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&r.models.size()==3,"whole family model despite global union exceeding per-cut point bound: "+r.phase+"/"+r.reason);
        for(ContextDependentModelInspection.Model model:r.models.values())require(model.otherRepresentative.semanticallyEquals(string("__kanger_all_other_3")),"representative fresh against all independent members");
        List<Cut> reverse=new ArrayList<>(cuts);Collections.reverse(reverse);ContextDependentModelSearchInspection.Result again=inspect(x,reverse,2);
        require(again.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&again.models.values().iterator().next().otherRepresentative.semanticallyEquals(string("__kanger_all_other_3")),"selection deterministic across physical member order");
        require(terms==m.getTerms().size()+a.getTerms().size()+b.getTerms().size()&&state.equals(DmzObservationStateFingerprint.capture(m)),"palette selection makes no compiler/factory writes");
        boolean initialized=false;try{inspect(member(m,6131,true,Collections.singletonList("!tag(3);")),cuts,2);}catch(IllegalStateException expected){initialized=expected.getMessage().contains("primary-only automatic palette");}
        require(initialized,"initialized views remain an explicit unsupported boundary");
        raw(m,"!changed(1);");boolean stale=false;try{inspect(x,cuts,2);}catch(IllegalStateException expected){stale=true;}
        require(stale,"stale actual inventory cannot seed automatic qualification");
        Mind positive=memory(),negative=memory();raw(positive,"!p(1);");raw(negative,"!~p(1);");
        Member pm=member(positive,6141,true,Collections.emptyList()),nm=member(negative,6142,false,Collections.emptyList());
        ContextDependentModelSearchInspection.Result refuted=inspect(pm,Collections.singletonList(new Cut(null,Collections.singletonList(nm))),2);
        require(refuted.status==ContextDependentModelSearchInspection.Status.REFUTED&&refuted.models.isEmpty()&&refuted.frame==null,"actual sourced collision remains distinct from no model found");
        Map<UUID,String> names=new HashMap<>();names.put(pm.inventory.context,"Parent");names.put(nm.inventory.context,"Opposite");
        Map<UUID,ContextProductionInventory> inventories=new HashMap<>();inventories.put(pm.inventory.context,pm.inventory);inventories.put(nm.inventory.context,nm.inventory);
        org.kanger.exception.ContextDependentFamilyCommitException failure=org.kanger.exception.ContextDependentFamilyCommitException.collision(refuted,names,inventories);
        require(failure.getErrorCode()==org.kanger.enums.StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&failure.getFamilyResult()==refuted&&!failure.getCollisions().isEmpty(),"typed refusal retains actual family evidence");
        StringBuilder text=new StringBuilder();for(ContextQualification.CollisionWitness witness:failure.getCollisions())text.append(witness.getLeft()).append(witness.getRight());
        require(text.toString().contains("Parent@1#")&&text.toString().contains("Opposite@1#")&&!text.toString().contains(pm.inventory.context.toString())&&!text.toString().contains(nm.inventory.context.toString()),"human evidence contains names and exact original rules, not UUIDs");
        boolean missing=false;try{org.kanger.exception.ContextDependentFamilyCommitException.collision(refuted,Collections.emptyMap(),inventories);}catch(IllegalArgumentException expected){missing=true;}
        require(missing,"unknown physical name cannot silently become a raw identity");
        boolean notRefuted=false;try{org.kanger.exception.ContextDependentFamilyCommitException.collision(r,names,inventories);}catch(IllegalArgumentException expected){notRefuted=true;}
        require(notRefuted,"a modelled result cannot be relabelled as a collision");
        System.out.println("CONTEXT_DEPENDENT_AUTOMATIC_INSPECTION_PASS checks="+checks);
    }
}
