/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.units.*;
import org.kanger.interfaces.*;
import org.kanger.ContextDependentCacheAliasInspection.*;
import org.kanger.ContextDependentProductionInspection.*;

public final class ContextDependentCacheAliasInspectionRunner {
    private static int checks;
    private static void need(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
    private static long original(Mind m,String text)throws Exception{for(IRule item:m.getRules())if(!item.isGenerated()&&text.equals(((Rule)item).getOrigin()))return item.getId();throw new AssertionError("original");}
    private static final ContextDependentReplayInspection.Policy P=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    private static void run(boolean full,int mode,boolean reverse)throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);
        String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";
        String a="!@y rel(1,y,1)->out(1,y);",b="!@y rel("+(mode==0?2:1)+",y,"+(mode==1?2:1)+")->out(2,y);";
        need(m.compile(reverse?b+a+root:root+a+b,null,false),"native cache fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,619),1,true,Collections.emptyList(),1000);
        long r=original(m,root),h1=original(m,a),h2=original(m,b);
        Request recipe=new Request(P,1000,Arrays.asList(new Step(h1,Collections.singletonList(Donor.original(r))),new Step(h2,Collections.singletonList(Donor.original(r)))));
        List<Binding> bindings=new ArrayList<>();
        for(ContextProductionInventory.Row row:inventory.generated){Rule rule=(Rule)m.getRules().get(row.nativeRule);if(rule.getDomain().getPredicate(m).getName(m).equals("out")){String value=rule.getDomain().getArguments().get(0).getValue(m).toString();bindings.add(new Binding(row.nativeRule,value.startsWith("1")?0:1));}}
        need(bindings.size()==2,"two actual projected caches");String before=DmzObservationStateFingerprint.capture(m);
        ContextDependentCacheAliasInspection.Result result=ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,bindings);
        Status expected=full?(mode==2?Status.SPLIT:Status.RENAMING_MATCHED):(mode==0?Status.CONFLATED:Status.RENAMING_MATCHED);
        need(result.status==expected,"alias full="+full+" mode="+mode+" reverse="+reverse+" expected="+expected+" actual="+result.status+" reason="+result.reason);
        need(result.rows==2&&before.equals(DmzObservationStateFingerprint.capture(m)),"read-only exact selected occurrence audit");
        Collections.reverse(bindings);need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,bindings).status==expected,"selection order does not alter structural alias result");
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,Collections.singletonList(bindings.get(0))).status==Status.RENAMING_MATCHED,"single selected occurrence is not whole-cache coverage");
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,Arrays.asList(bindings.get(0),bindings.get(0))).status==Status.INCOMPLETE,"duplicate cache occurrence refused");
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,Collections.singletonList(new Binding(r,0))).status==Status.INCOMPLETE,"primary row cannot masquerade as generated cache");
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,Collections.singletonList(new Binding(bindings.get(0).nativeRule,99))).status==Status.INCOMPLETE,"unproved product reference refused");
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,Collections.singletonList(new Binding(bindings.get(0).nativeRule,1-bindings.get(0).product))).status==Status.INCOMPLETE,"predicate match alone cannot ignore ordinary argument mismatch");
        Request cacheAsOriginal=new Request(P,1000,Collections.singletonList(new Step(h1,Collections.singletonList(Donor.original(bindings.get(0).nativeRule)))));
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,cacheAsOriginal,bindings).status==Status.INCOMPLETE,"cache is never replayed as an original premise");
        boolean unmodifiable=false;try{inventory.generated.clear();}catch(UnsupportedOperationException expectedFailure){unmodifiable=true;}need(unmodifiable,"inventory selection remains immutable");
        need(!inventory.dependentPrimaryView()&&ContextDependentGroundCacheInspection.inspect(m,inventory,new ContextDependentGroundCacheInspection.Limits(1000,1000,8,1000000,1000000)).status==ContextDependentGroundCacheInspection.Status.INCOMPLETE,"diagnostic cannot open symbolic cache admission");
        need(before.equals(DmzObservationStateFingerprint.capture(m)),"all incomplete checks preserve actual runtime");
        need(m.compile("!unrelated(9);",null,false),"independent actual mutation");boolean stale=false;try{ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,bindings);}catch(IllegalStateException expectedFailure){stale=true;}need(stale,"stale physical fingerprint cannot reuse an alias result");
    }
    private static void mixed()throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);
        String[] declarations={"!@a $u @b partial(a,u,b);","!@y partial(1,y,1)->pout(1,y);","!@y partial(2,y,1)->pout(2,y);","!@a @b $u full(a,u,b);","!@y full(1,y,1)->fout(1,y);","!@y full(1,y,1)->fout(2,y);"};
        need(m.compile(String.join("",declarations),null,false),"mixed alias fixture declarations");
        List<Step> steps=new ArrayList<>();for(int i:new int[]{1,2,4,5})steps.add(new Step(original(m,declarations[i]),Collections.singletonList(Donor.original(original(m,declarations[i<3?0:3])))));
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,6192),1,true,Collections.emptyList(),1000);List<Binding> bindings=new ArrayList<>();
        for(ContextProductionInventory.Row row:inventory.generated){Domain domain=((Rule)m.getRules().get(row.nativeRule)).getDomain();String predicate=domain.getPredicate(m).getName(m);if(predicate.equals("pout")||predicate.equals("fout")){int index=domain.getArguments().get(0).getValue(m).toString().startsWith("1")?0:1;bindings.add(new Binding(row.nativeRule,index+(predicate.equals("fout")?2:0)));}}
        need(bindings.size()==4,"both independent alias mechanisms have physical projected rows");String before=DmzObservationStateFingerprint.capture(m);Request recipe=new Request(P,1000,steps);
        need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,bindings).status==Status.CONFLATED_AND_SPLIT,"retain both alias findings instead of first encountered mismatch");
        Collections.reverse(bindings);need(ContextDependentCacheAliasInspection.inspect(m,inventory,recipe,bindings).status==Status.CONFLATED_AND_SPLIT,"mixed finding is independent of selection traversal order");
        need(before.equals(DmzObservationStateFingerprint.capture(m)),"mixed alias audit is read-only");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-cache-alias-").toString());
        for(boolean full:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean reverse:new boolean[]{false,true})run(full,mode,reverse);
        mixed();
        System.out.println("CONTEXT_DEPENDENT_CACHE_ALIAS_INSPECTION_PASS checks="+checks);
    }
}
