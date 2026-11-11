/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCacheLedgerInspection.*;
import org.kanger.interfaces.*;
import org.kanger.units.*;
import org.kanger.primitives.Argument;

public final class ContextDependentCacheLedgerInspectionRunner {
    private static int checks;
    private static void need(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,622),1,true,Collections.emptyList(),1000);}
    private static void matrix(boolean full,int mode,boolean reverse)throws Exception{
        Mind m=memory();String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";
        String a="!@y rel(1,y,1)->out(1,y);",b="!@y rel("+(mode==0?2:1)+",y,"+(mode==1?2:1)+")->out(2,y);";
        need(m.compile(reverse?b+a+root:root+a+b,null,false),"joint ledger fixture");ContextProductionInventory inventory=inventory(m);String before=DmzObservationStateFingerprint.capture(m);Result result=ContextDependentCacheLedgerInspection.inspect(m,inventory);
        if(mode==2){need(result.status==Status.INCOMPLETE&&result.reason.contains("Projection ancestry incomplete"),"unresolved projection alternatives cannot authorize a joint receipt");need(result.occurrences.isEmpty(),"incomplete joint inspection returns no successful occurrence ledger");}
        else{
            need(result.status==Status.INSPECTED,"joint ledger: "+result.reason);need(result.rootRows==2&&result.productRows==2&&result.rootRows+result.productRows==inventory.generated.size(),"all generated rows jointly partitioned");
            ContextDependentCacheAliasInspection.Status expected=full?ContextDependentCacheAliasInspection.Status.CONFLATED_AND_SPLIT:(mode==0?ContextDependentCacheAliasInspection.Status.CONFLATED:ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED);
            need(result.aliases==expected,"cross-scope dependency identity full="+full+" mode="+mode+" reverse="+reverse+" expected="+expected+" actual="+result.aliases);
            need(result.occurrences.size()==4,"root instances and projected symbolic slots both present");
            boolean immutable=false;try{result.occurrences.clear();}catch(UnsupportedOperationException expectedFailure){immutable=true;}need(immutable,"joint occurrence ledger immutable");
        }
        need(before.equals(DmzObservationStateFingerprint.capture(m))&&!inventory.dependentPrimaryView(),"joint inspection is read-only and cannot open cache admission");
    }
    private static void crossBoundary(boolean reverse)throws Exception{
        Mind m=memory();String root="!$u rel(1,u,1);",rule="!@u rel(1,u,1)->out(u);",other="!$v other(v);";
        need(m.compile(reverse?other+rule+root:root+rule+other,null,false),"primary/product boundary fixture");Result good=ContextDependentCacheLedgerInspection.inspect(m,inventory(m));need(good.status==Status.INSPECTED&&good.aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED&&good.productRows==1&&good.rootRows==0,"closed root primary anchor and product jointly match");
        Rule product=null;ITerm foreign=null;for(IRule item:m.getRules()){Rule row=(Rule)item;String predicate=row.getDomain().getPredicate(m).getName(m);if(item.isGenerated()&&predicate.equals("out"))product=row;else if(!item.isGenerated()&&predicate.equals("other"))foreign=row.getDomain().getArguments().get(0).getValue(m);}
        need(product!=null&&foreign!=null,"real isolated runtime rows available");ContextProductionInventory old=inventory(m);product.getDomain().getArguments().set(0,new Argument(foreign));boolean stale=false;try{ContextDependentCacheLedgerInspection.inspect(m,old);}catch(IllegalStateException expectedFailure){stale=true;}need(stale,"changed cross-scope native symbol rejects old fingerprint");
        ContextProductionInventory changed=inventory(m);String before=DmzObservationStateFingerprint.capture(m);
        need(ContextDependentRootCacheInspection.inspect(m,changed).aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"root-only ledger alone still matches");
        need(ContextDependentCacheAncestryInspection.inspect(m,changed).aliases.status==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"product-only ledger alone still matches");
        Result joint=ContextDependentCacheLedgerInspection.inspect(m,changed);need(joint.status==Status.INSPECTED&&joint.aliases==ContextDependentCacheAliasInspection.Status.CONFLATED_AND_SPLIT,"joint ledger catches lost anchor invisible to both separate matching ledgers");
        need(joint.occurrences.size()==3&&before.equals(DmzObservationStateFingerprint.capture(m)),"both primary anchors and product retained without runtime change");
    }
    private static void chain(boolean reverse)throws Exception{
        Mind m=memory();String root="!$u rel(1,u,1);",rules="!@u rel(1,u,1)->middle(1,u); !@u middle(1,u)->tail(1,u);";
        need(m.compile(reverse?rules+root:root+rules,null,false),"joint two-hop fixture");Result result=ContextDependentCacheLedgerInspection.inspect(m,inventory(m));need(result.status==Status.INSPECTED&&result.productRows==2&&result.aliases==ContextDependentCacheAliasInspection.Status.RENAMING_MATCHED,"one primary witness remains shared through two projected steps");
        int anchors=0,products=0;for(Occurrence o:result.occurrences){if(o.origin==Origin.PRIMARY_ANCHOR)anchors++;if(o.origin==Origin.PRODUCT)products++;}need(anchors==1&&products==2,"explicit origins preserve anchor/product boundary");
        Rule generated=null;for(IRule item:m.getRules())if(item.isGenerated()){generated=(Rule)item;break;}need(generated!=null,"actual chain cache");generated.getCauses().clear();ContextProductionInventory broken=inventory(m);String before=DmzObservationStateFingerprint.capture(m);need(ContextDependentCacheLedgerInspection.inspect(m,broken).status==Status.INCOMPLETE,"missing cause does not silently drop a generated row from the joint ledger");need(before.equals(DmzObservationStateFingerprint.capture(m)),"joint incomplete refusal preserves native state");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-cache-ledger-").toString());for(boolean full:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean reverse:new boolean[]{false,true})matrix(full,mode,reverse);crossBoundary(false);crossBoundary(true);chain(false);chain(true);
        System.out.println("CONTEXT_DEPENDENT_CACHE_LEDGER_INSPECTION_PASS checks="+checks);
    }
}
