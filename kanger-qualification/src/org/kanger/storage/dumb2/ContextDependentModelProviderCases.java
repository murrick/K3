/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextDependentModelInspection.*;
import org.kanger.ContextDependentModelInspection.Request;
import org.kanger.ContextDependentModelInspection.Result;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Actual provider ownership cases for the independent model runner; not a second registered driver. */
public final class ContextDependentModelProviderCases {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}require(no,why);}
    private static final class Fixture implements AutoCloseable{
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path d,String name)throws Exception{user.setDatabaseDir(d+File.separator);user.setUserDir(Files.createDirectories(d.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static Request request(Map<String,Model> models,int rounds,int finalWork){return new Request(new ContextDependentIterationInspection.Request(new Policy(2000000,1000,1000,64000000,200000),10000,rounds),models,20,100000,4000000,finalWork);}
    public static int run()throws Exception{
        checks=0;Path dir=Files.createTempDirectory("dependent-model-db-");try(Fixture a=new Fixture(dir,"A")){require(a.root.compile("!p(1);",null,false),"A fixture");}try(Fixture b=new Fixture(dir,"B")){require(b.root.compile("!~p(1);",null,false),"B fixture");}
        for(boolean reverse:new boolean[]{false,true})try(Fixture f=new Fixture(dir,"X"+reverse)){
            require(f.root.compile("!seed(1);",null,false),"actual parent");f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");long revision=f.db.getRevision();String parent=ContextProductionInventory.inspect(f.root,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;Mind child=new Mind(f.root);Rule h=(Rule)child.compileLine("!@x seed(x)->out(x);",false,new LinkedList<ITerm>());Rule fixed=(Rule)f.root.getRules().get(0);SemanticTermSnapshot one=SemanticTermSnapshot.capture(fixed.getDomain().get(0).getValue(f.root));User temp=new User();Mind tm=new Mind(temp);temp.setCurrentMind(tm);Rule tr=(Rule)tm.compileLine("!fresh(2);",false,new LinkedList<ITerm>());SemanticTermSnapshot other=SemanticTermSnapshot.capture(tr.getDomain().get(0).getValue(tm));Map<String,Model> models=new LinkedHashMap<>();
            Map<Signature,Relation> local=new LinkedHashMap<>();local.put(new Signature("seed",1),new Relation(true,Collections.emptyMap()));local.put(new Signature("out",1),new Relation(true,Collections.emptyMap()));models.put(ContextDependentCutInspection.local(f.db.getContextId()),new Model(Collections.singletonList(one),other,local,Collections.emptyMap()));
            for(ContextConnection connection:f.db.dmzTopology()){boolean positive=false;for(Object o:connection.layer().getRules())positive|=((Rule)o).getOrigin().equals("!p(1);");Map<Signature,Relation> table=new LinkedHashMap<>(local);table.put(new Signature("p",1),new Relation(positive,Collections.emptyMap()));models.put(ContextDependentCutInspection.independent(connection.getTarget().getContextId()),new Model(Collections.singletonList(one),other,table,Collections.emptyMap()));}
            String before=ContextProductionInventory.inspect(child,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){
                Result result=lease.inspectDependentModels(request(models,5,4000000));require(result.status==Status.AUDITED_SUPPLIED&&result.originalRows==8&&result.finalObligations>0,"actual native originals and every frozen common/private carrier audited under independent cut models");require(f.db.getRevision()==revision&&before.equals(ContextProductionInventory.inspect(child,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"chosen model values never enter actual child or revision");Result noFinal=lease.inspectDependentModels(request(models,5,1));require(noFinal.status==Status.INCOMPLETE&&noFinal.phase.equals("actual-final-audit")&&noFinal.iteration==null,"provider final-only exhaustion cannot promote an original-model prefix");Result shortRun=lease.inspectDependentModels(request(models,1,4000000));require(shortRun.status==Status.INCOMPLETE&&shortRun.phase.equals("iteration"),"provider requires completed cycle before model success");child.compileLine("!changed(1);",false,new LinkedList<ITerm>());reject(()->lease.inspectDependentModels(request(models,5,4000000)),"actual owning whole-child guard remains mandatory");
            }finally{f.root.release(child);}require(parent.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"actual parent unchanged after model audit/release");require(h!=null,"pending original preserved");
        }
        return checks;
    }
}
