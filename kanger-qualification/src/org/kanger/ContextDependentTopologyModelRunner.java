/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.*;
import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.ContextDependentModelInspection.*;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.DB;
import org.kanger.units.Rule;

/** Exact all-value consistency evidence for the compatible dependent attachment example. */
public final class ContextDependentTopologyModelRunner {
    private static int checks;
    private static final String UNIT="!@a $u @b rel(a,u,b);";
    private static final String SAFE="!@u ~(rel(1,u,1)&&rel(2,u,1));";
    private static final String BAD="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static void raw(Mind m,String text)throws Exception{m.compileLine(text,false,new LinkedList<ITerm>());}
    private static SemanticTermSnapshot value(String text)throws Exception{Mind m=memory();Rule r=(Rule)m.compileLine("!value("+text+");",false,new LinkedList<ITerm>());return SemanticTermSnapshot.capture(r.getDomain().get(0).getValue(m));}
    private static Member member(Mind m,UUID id,long revision,boolean local)throws Exception{return new Member(m,ContextProductionInventory.inspect(m,id,revision,local,Collections.emptyList(),1000));}
    private static ContextDependentIterationInspection.Request iteration(){return new ContextDependentIterationInspection.Request(new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000),10000,8);}
    private static Model model(Member producer,boolean dependent)throws Exception{
        // Classes 0 and 1 name 1 and 2; class 2 contains EVERY other value.
        Map<List<Integer>,Boolean> relation=new LinkedHashMap<>();
        for(int a=0;a<3;a++)for(int b=0;b<3;b++)relation.put(Arrays.asList(a,dependent?a:0,b),true);
        Map<List<Integer>,Integer> choices=new LinkedHashMap<>();
        if(dependent){choices.put(Collections.singletonList(1),1);choices.put(Collections.singletonList(2),2);}
        ContextProductionInventory.Row root=producer.inventory.primary.get(0);
        Source source=new Source(producer.inventory.context,producer.inventory.revision,root.nativeRule,
                producer.inventory.localX,"inventory:"+producer.inventory.fingerprint);
        return new Model(Arrays.asList(value("1"),value("2")),value("9"),
                Collections.singletonMap(new Signature("rel",3),new Relation(false,relation)),
                Collections.singletonMap(new Function(source,1),new Choice(1,0,choices)));
    }
    private static Result inspect(Member x,Member foreign,Member producer,boolean dependent)throws Exception{
        Cut cut=new Cut(null,Collections.singletonList(foreign));Map<String,Model> models=new LinkedHashMap<>();
        // The reverse local cut receives X-supported residuals containing the foreign function.
        Model full=model(producer,dependent);
        models.put(ContextDependentCutInspection.local(x.inventory.context),full);models.put(cut.key,full);
        return ContextDependentModelInspection.inspect(x,Collections.singletonList(cut),new Request(iteration(),models,10,10000,8000000,8000000));
    }
    private static ContextDependentModelSearchInspection.Request search(Member x,Member foreign,Member producer,boolean dependent,int truthWork)throws Exception{
        Map<String,Model> proposal=new LinkedHashMap<>();Model m=model(producer,dependent);
        proposal.put(ContextDependentCutInspection.local(x.inventory.context),m);
        proposal.put(new Cut(null,Collections.singletonList(foreign)).key,m);
        List<Map<String,Model>> proposals=new ArrayList<>();proposals.add(proposal);
        ContextDependentModelSearchInspection.Request request=new ContextDependentModelSearchInspection.Request(iteration(),Collections.emptyList(),value("9"),
                new ContextDependentModelSearchInspection.Limits(10,10000,10000,1,8000000,8000000,20000000,8000000,8000000),
                new ContextDependentRuntimeContinuationInspection.Projection(10000,100000,8000000,8000000,8000000,truthWork),proposals);
        proposal.clear();proposals.clear();require(request.proposals.size()==1&&request.proposals.get(0).size()==2,"proposal container copied before caller mutation");
        return request;
    }
    private static void noPrefix(ContextDependentModelSearchInspection.Result result,String why){
        require(result.status==ContextDependentModelSearchInspection.Status.INCOMPLETE&&result.models.isEmpty()&&result.family==null&&result.frame==null
                &&result.originalRows==0&&result.finalObligations==0&&result.runtimeObligations==0&&result.tried==1,why+": "+result.phase+"/"+result.reason);
    }
    private static void check(Mind producer,Mind receiver,UUID p,long pr,UUID r,long rr,boolean reverse)throws Exception{
        Member pm=member(producer,p,pr,!reverse),rm=member(receiver,r,rr,reverse);
        Member x=reverse?rm:pm,foreign=reverse?pm:rm;
        String ps=DmzObservationStateFingerprint.capture(producer),rs=DmzObservationStateFingerprint.capture(receiver);
        Result good=inspect(x,foreign,pm,true);
        require(good.status==Status.AUDITED_SUPPLIED,"dependent family model required (reverse="+reverse+", rows="+pm.inventory.rows.size()+"/"+rm.inventory.rows.size()+"): "+good.status+"/"+good.phase+"/"+good.reason);
        require(good.originalRows==3&&good.iteration!=null,"local and X/member originals audited separately");
        Result bad=inspect(x,foreign,pm,false);
        require(bad.status==Status.INCOMPLETE&&"original-audit".equals(bad.phase)&&bad.originalRows==0&&bad.iteration==null,
                "a false constant witness guess is not a theory refutation: "+bad.phase+"/"+bad.reason);
        List<Cut> cuts=Collections.singletonList(new Cut(null,Collections.singletonList(foreign)));
        ContextDependentModelSearchInspection.Request request=search(x,foreign,pm,true,8000000);
        ContextDependentModelSearchInspection.Result found=ContextDependentModelSearchInspection.inspect(x,cuts,request);
        require(found.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&found.tried==1&&found.originalRows==3
                &&found.family!=null&&found.frame!=null&&found.finalObligations==found.runtimeObligations,
                "one proposed model must pass original, final and independent indexed truth: "+found.phase+"/"+found.reason);
        require(!reverse||found.finalObligations>0,"reverse attachment includes actual transported symbolic consequences");
        ContextDependentModelSearchInspection.Limits cap=request.limits;
        ContextDependentModelSearchInspection.Request automatic=new ContextDependentModelSearchInspection.Request(request.iteration,request.extraPoints,request.otherRepresentative,
                new ContextDependentModelSearchInspection.Limits(cap.points,cap.cells,cap.choiceCells,128,cap.schemaWork,cap.searchWork,cap.auditWork,cap.originalWork,cap.finalWork),request.projection);
        ContextDependentModelSearchInspection.Result inferred=ContextDependentModelSearchInspection.inspect(x,cuts,automatic);
        require(inferred.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&inferred.originalRows==3&&inferred.finalObligations==inferred.runtimeObligations,
                "automatic dependent family model within 128 candidates, reverse="+reverse+": "+inferred.phase+"/"+inferred.reason);

        noPrefix(ContextDependentModelSearchInspection.inspect(x,cuts,search(x,foreign,pm,false,8000000)),"failed constant choice consumes budget without a false refutation or partial prefix");
        ContextDependentModelSearchInspection.Result bounded=ContextDependentModelSearchInspection.inspect(x,cuts,search(x,foreign,pm,true,1));
        noPrefix(bounded,"indexed truth limit preserves no model receipt");
        require("runtime-truth".equals(bounded.phase),"good native model must still reach independently bounded indexed truth");
        boolean immutable=false;try{request.proposals.get(0).clear();}catch(UnsupportedOperationException expected){immutable=true;}
        require(immutable,"saved proposal maps immutable");
        require(ps.equals(DmzObservationStateFingerprint.capture(producer))&&rs.equals(DmzObservationStateFingerprint.capture(receiver)),"model audits never write chosen values or change actual source state");
    }
    private static Mind open(Path directory,String name)throws Exception{User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);DB db=new DB();db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("dependent-topology-model-");System.setProperty("user.home",directory.toString());
        for(boolean reverse:new boolean[]{false,true}){Mind p=memory(),r=memory();raw(p,UNIT);raw(r,SAFE);check(p,r,new UUID(0,6111),1,new UUID(0,6112),1,reverse);}
        for(int pass=0;pass<2;pass++){
            Mind p=open(directory,"Producer"),r=open(directory,"Receiver");
            try{
                if(pass==0){require(p.compile(UNIT,null,false),"persist producer");require(r.compile(SAFE,null,false),"persist receiver");}
                DB pd=(DB)((User)p.getUser()).getData(),rd=(DB)((User)r.getUser()).getData();
                for(boolean reverse:new boolean[]{false,true})check(p,r,pd.federationSnapshot().getSourceContextId(),pd.getRevision(),rd.federationSnapshot().getSourceContextId(),rd.getRevision(),reverse);
            }finally{close(r);close(p);}
        }
        // A proposed model is pinned to native occurrence identity, not just declaration text.
        Mind sp=memory(),sr=memory();raw(sp,UNIT);raw(sr,SAFE);
        Member current=member(sp,new UUID(0,6115),1,true),foreign=member(sr,new UUID(0,6116),1,false);
        Member wrongRevision=member(sp,current.inventory.context,2,true);
        noPrefix(ContextDependentModelSearchInspection.inspect(current,Collections.singletonList(new Cut(null,Collections.singletonList(foreign))),search(current,foreign,wrongRevision,true,8000000)),
                "same text at another revision cannot supply witness provenance");
        ContextDependentModelSearchInspection.Request stale=search(current,foreign,current,true,8000000);
        ContextDependentModelSearchInspection.Request falseGuess=search(current,foreign,current,false,8000000);
        ContextDependentModelSearchInspection.Limits cap=stale.limits;
        ContextDependentModelSearchInspection.Request second=new ContextDependentModelSearchInspection.Request(stale.iteration,stale.extraPoints,stale.otherRepresentative,
                new ContextDependentModelSearchInspection.Limits(cap.points,cap.cells,cap.choiceCells,2,cap.schemaWork,cap.searchWork,cap.auditWork,cap.originalWork,cap.finalWork),
                stale.projection,Arrays.asList(falseGuess.proposals.get(0),stale.proposals.get(0)));
        ContextDependentModelSearchInspection.Result recovered=ContextDependentModelSearchInspection.inspect(current,Collections.singletonList(new Cut(null,Collections.singletonList(foreign))),second);
        require(recovered.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING&&recovered.tried==2,
                "false proposal can be followed by an independently audited correct proposal within the shared allowance");
        raw(sp,"!rel(3,3,3);");boolean changed=false;
        try{ContextDependentModelSearchInspection.inspect(current,Collections.singletonList(new Cut(null,Collections.singletonList(foreign))),stale);}
        catch(IllegalStateException expected){changed=true;}
        require(changed,"actual changed inventory invalidates proposal before search");
        Mind p=memory(),r=memory();raw(p,UNIT);raw(r,BAD);
        Member pm=member(p,new UUID(0,6113),1,true),rm=member(r,new UUID(0,6114),1,false);
        Result impossible=inspect(pm,rm,pm,true);
        require(impossible.status!=Status.AUDITED_SUPPLIED,"same-a across-b prohibition cannot receive compatible model verdict");
        System.out.println("CONTEXT_DEPENDENT_TOPOLOGY_MODEL_PASS checks="+checks);
    }
}
