/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.primitives.Argument;
import org.kanger.ContextDependentRootCacheInspection.*;

public final class ContextDependentRootCacheInspectionRunner {
    private static int checks;
    private static void need(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,621),1,true,Collections.emptyList(),1000);}
    private static void matrix(boolean full,int mode,boolean reverse)throws Exception{
        Mind m=memory();String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";
        String a="!@y rel(1,y,1)->out(1,y);",b="!@y rel("+(mode==0?2:1)+",y,"+(mode==1?2:1)+")->out(2,y);";
        need(m.compile(reverse?b+a+root:root+a+b,null,false),"root-instance fixture");ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);Result result=ContextDependentRootCacheInspection.inspect(m,inventory);
        need(result.status==Status.INSPECTED,"root-instance inspection: "+result.reason);
        need(result.instances.size()==(mode==2?1:2)&&result.remaining.size()==2&&result.instances.size()+result.remaining.size()==inventory.generated.size(),"exact root-instance/projected occurrence partition");
        ContextDependentCacheAliasInspection.Status expected=mode!=2&&(full||mode==0)?ContextDependentCacheAliasInspection.Status.CONFLATED:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED;
        need(result.aliases==expected,"dependency-indexed root alias relation full="+full+" mode="+mode+" reverse="+reverse+" expected="+expected+" actual="+result.aliases);
        for(Occurrence occurrence:result.occurrences){need(!occurrence.primaryAnchor&&occurrence.call.parameters.size()==(full?2:1),"only preceding universals parameterize a root witness");need(result.instances.containsKey(occurrence.row),"actual root instance occurrence retained");}
        need(before.equals(DmzObservationStateFingerprint.capture(m))&&!inventory.dependentPrimaryView(),"root audit is read-only and grants no symbolic-cache authority");
        boolean immutable=false;try{result.instances.clear();}catch(UnsupportedOperationException expectedFailure){immutable=true;}need(immutable,"instance ledger immutable");
        if(mode==2)need(ContextDependentCacheAncestryInspection.inspect(m,inventory).status==ContextDependentCacheAncestryInspection.Status.INCOMPLETE,"root-scope inspection does not pretend to resolve ambiguous projection history");
    }
    private static void anchors()throws Exception{
        Mind m=memory();need(m.compile("!$u @a rel(a,u); !@u rel(1,u)->out(u); !$v other(v);",null,false),"nullary primary witness fixture");ContextProductionInventory inventory=inventory(m);Result result=ContextDependentRootCacheInspection.inspect(m,inventory);
        need(result.status==Status.INSPECTED&&result.instances.size()==1&&result.aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"nullary instance retains original primary witness identity");int anchors=0;for(Occurrence o:result.occurrences)if(o.primaryAnchor)anchors++;need(anchors==2,"both independent nullary primary witnesses anchored");
        Rule instance=(Rule)m.getRules().get(result.instances.keySet().iterator().next());ITerm foreign=null;for(IRule item:m.getRules())if(!item.isGenerated()&&((Rule)item).getDomain().getPredicate(m).getName(m).equals("other"))foreign=((Rule)item).getDomain().getArguments().get(0).getValue(m);
        need(foreign!=null,"distinct actual primary witness available");instance.getDomain().getArguments().set(1,new Argument(foreign));boolean stale=false;try{ContextDependentRootCacheInspection.inspect(m,inventory);}catch(IllegalStateException expectedFailure){stale=true;}need(stale,"changed symbolic binding invalidates old physical inventory");
        ContextProductionInventory changed=inventory(m);String before=DmzObservationStateFingerprint.capture(m);Result bad=ContextDependentRootCacheInspection.inspect(m,changed);
        need(bad.status==Status.INSPECTED&&bad.aliases==ContextDependentCacheAliasInspection.Status.CONFLATED_AND_SPLIT,"root anchors expose both sharing with another root and separation from the original witness");
        need(before.equals(DmzObservationStateFingerprint.capture(m)),"anchored mismatch is diagnostic and read-only");
        instance.getCauses().clear();need(ContextDependentRootCacheInspection.inspect(m,inventory(m)).status==Status.INCOMPLETE,"missing root provenance cannot invent a primary source");
    }
    private static void wide()throws Exception{
        Mind m=memory();need(m.compile("!@a $u @b rel(a,u,b,a,u,b,7); !@y rel(1,y,2,1,y,2,7)->out(y);",null,false),"seven-argument repeated-binder fixture");Result result=ContextDependentRootCacheInspection.inspect(m,inventory(m));need(result.status==Status.INSPECTED&&result.instances.size()==1&&result.occurrences.size()==2&&result.aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"wide root binds repeated universals/existentials and fixed slot");
        Rule instance=(Rule)m.getRules().get(result.instances.keySet().iterator().next());IArgument one=instance.getDomain().getArguments().get(0);IArgument two=instance.getDomain().getArguments().get(2);IArgument seven=instance.getDomain().getArguments().get(6);
        instance.getDomain().getArguments().set(6,one);need(ContextDependentRootCacheInspection.inspect(m,inventory(m)).status==Status.INCOMPLETE,"changed fixed ordinary root slot refuses");instance.getDomain().getArguments().set(6,seven);
        instance.getDomain().getArguments().set(3,two);need(ContextDependentRootCacheInspection.inspect(m,inventory(m)).status==Status.INCOMPLETE,"different values for one repeated universal refuse");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-root-cache-").toString());for(boolean full:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean reverse:new boolean[]{false,true})matrix(full,mode,reverse);anchors();wide();
        System.out.println("CONTEXT_DEPENDENT_ROOT_CACHE_INSPECTION_PASS checks="+checks);
    }
}
