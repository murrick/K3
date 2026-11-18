/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.io.File;
import org.kanger.*;
import org.kanger.units.Rule;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.*;

/** Distinct guest data devices; producer-only pause, unmodified consumer provider. */
public final class ContextDevicePinRunner {
    private static final Path PRODUCER=Paths.get("/producer/Producer"),CONSUMER=Paths.get("/consumer/Consumer");
    private static void need(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    private static Mind open(DB db,Path directory,String name)throws Exception{
        User user=new User();user.setDatabaseDir(directory+File.separator);db.init(user);
        Mind root=new Mind(user);user.setCurrentMind(root);Mind mind=(Mind)root.useStorage(name);user.setCurrentMind(mind);return mind;
    }
    private static void close(Mind mind)throws Exception{((User)mind.getUser()).setCurrentMind(mind.closeStorage());}
    private static long pin(String mode){return "create".equals(mode)?0:("fork".equals(mode)?1:("reindex".equals(mode)?4:2));}
    private static void producer(String mode)throws Exception{
        if("create".equals(mode)){
            DevicePinPause.arm(ContextStore.revisionPath(PRODUCER));ContextStore.create(PRODUCER);
        }else if("fork".equals(mode)){
            DB source=new DB();Mind m=open(source,PRODUCER.getParent(),"Template");need(m.compile("!anchor(1);",null,false),"Fork template");source.flush();close(m);
            DevicePinPause.arm(ContextStore.revisionPath(PRODUCER));
            try(ContextSnapshot snapshot=ContextSnapshot.open(Paths.get("/producer/Template"),1)){ContextStore.fork(snapshot,PRODUCER);}
        }else{
            if("reindex".equals(mode)){
                DB support=new DB();Mind s=open(support,PRODUCER.getParent(),"Support");need(s.compile("!support(9);",null,false),"Support original");support.flush();close(s);
            }
            DB db=new DB();Mind m=open(db,PRODUCER.getParent(),"Producer");need(m.compile("!anchor(1);",null,false),"Producer R1");db.flush();need(db.getRevision()==1,"Producer predecessor");
            if("reindex".equals(mode)){
                java.lang.reflect.Field field=DB.class.getDeclaredField("context");field.setAccessible(true);ContextStore owner=(ContextStore)field.get(db);
                ContextBase base=(ContextBase)db.getBase("rules");Rule rule=null;for(org.kanger.interfaces.IRule one:m.getRules())if(!one.isGenerated())rule=(Rule)one;
                need(rule!=null,"Legacy source");PersistentRecord old=((ContextStep)base.get(rule.getId())).getPersistentRecord();
                TypeDefinition type=owner.registerType(RuleV1Adapter.TYPE_NAME,RuleV1Adapter.INSTANCE.getDescriptor());
                base.update(new ContextStep(base,new PersistentRecord(old.getId(),old.getHash(),old.getNextId(),type.getTypeCode(),StructuralValueCodec.encode(type.getDescriptor(),RuleV1Adapter.INSTANCE.project(rule,m)))));
                db.flush();need(db.getRevision()==2,"Legacy R2");db.connectContext("Support");need(db.saveConnections(m)==3,"Saved legacy R3");
                DevicePinPause.arm(ContextStore.revisionPath(PRODUCER));db.reindex(null,m);
            }else{
                DevicePinPause.arm(ContextStore.revisionPath(PRODUCER));need(m.compile("!fresh(2);",null,false),"Producer delta");db.flush();
            }
        }
        System.out.println("PIN_PRODUCER_ACK_UNEXPECTED");throw new AssertionError("Producer must remain paused before its ACK");
    }
    private static void originals(ContextSnapshot snapshot,int expected,String layout)throws Exception{
        if(snapshot.getRevision()==0){need(expected==0,"Initial empty state");return;}
        int count=0;ContextBase base=(ContextBase)snapshot.getBase("rules");
        for(byte[] bytes:base.publicationImage().values()){
            PersistentRecord row=PersistentRecordCodec.decode(bytes);TypeDefinition type=snapshot.resolveType(row.getTypeCode());
            StructuralValue value=StructuralValueCodec.decode(type.getDescriptor(),row.getPayload());
            if(!value.asStruct().get("generated").asBool()){
                need(layout.equals(type.getDescriptor().getName()),"Rule layout");need(!value.asStruct().get("deleted").asBool(),"Original remains live");count++;
            }
        }
        need(count==expected,"Original count changed");
    }
    private static void read(String mode,boolean missingExpected)throws Exception{
        long selected=pin(mode);java.util.UUID targetId;
        try(ContextSnapshot consumer=ContextSnapshot.open(CONSUMER,2)){
            need(consumer.getRevision()==2,"Consumer ACK survives");originals(consumer,1,"Rule-v2");
            ConnectionVector saved=ConnectionStore.read(CONSUMER,new RevisionRef(consumer.getContextId(),2));need(saved.size()==1,"One direct persisted pin");
            ContextConnection target=saved.getConnections().get(0);need(target.getTargetLocation().equals(PRODUCER)&&target.getTarget().getRevision()==selected,"Exact exported target");targetId=target.getTarget().getContextId();
        }
        try(ContextSnapshot producer=ContextSnapshot.open(PRODUCER,selected)){
            need(!missingExpected,"Lying-device control unexpectedly retained its pin");need(producer.getContextId().equals(targetId),"Target identity");
            originals(producer,"append".equals(mode)?2:("create".equals(mode)?0:1),"Rule-v2");
            if("fork".equals(mode))try(ContextSnapshot template=ContextSnapshot.open(Paths.get("/producer/Template"),1)){need(!template.getContextId().equals(producer.getContextId()),"Fork identity is fresh");originals(template,1,"Rule-v2");}
            if("append".equals(mode))try(ContextSnapshot previous=ContextSnapshot.open(PRODUCER,1)){originals(previous,1,"Rule-v2");}
            if("reindex".equals(mode)){
                try(ContextSnapshot previous=ContextSnapshot.open(PRODUCER,3)){originals(previous,1,"Rule-v1");need(ConnectionStore.read(PRODUCER,new RevisionRef(previous.getContextId(),3)).size()==1,"Old producer vector");}
                need(ConnectionStore.read(PRODUCER,new RevisionRef(producer.getContextId(),4)).size()==1,"Producer advisory vector");
            }
        }catch(StorageLifecycleException failure){
            if(!missingExpected||!"STORAGE_NOT_FOUND".equals(failure.getCode()))throw failure;
            System.out.println("PIN_COLD_MISSING mode="+mode+" consumer=2 target="+selected);return;
        }
        System.out.println("PIN_COLD_RECOVERED mode="+mode+" consumer=2 target="+selected);
    }
    private static void producerOnly(String mode)throws Exception{
        if("append".equals(mode))try(ContextSnapshot old=ContextSnapshot.open(PRODUCER,1)){originals(old,1,"Rule-v2");}
        if("reindex".equals(mode))try(ContextSnapshot old=ContextSnapshot.open(PRODUCER,3)){originals(old,1,"Rule-v1");need(ConnectionStore.read(PRODUCER,new RevisionRef(old.getContextId(),3)).size()==1,"Old advisory vector");}
        if("fork".equals(mode))try(ContextSnapshot original=ContextSnapshot.open(Paths.get("/producer/Template"),1)){originals(original,1,"Rule-v2");}
        long selected=-1;
        try(ContextSnapshot current=ContextSnapshot.open(PRODUCER)){
            selected=current.getRevision();
            if("append".equals(mode)){need(selected==1||selected==2,"Append transition");originals(current,(int)selected,"Rule-v2");}
            else if("reindex".equals(mode)){need(selected==3||selected==4,"Reindex transition");originals(current,1,selected==3?"Rule-v1":"Rule-v2");}
            else{need(selected==pin(mode),"Initial transition");originals(current,"fork".equals(mode)?1:0,"Rule-v2");}
        }catch(StorageLifecycleException missing){if(!("create".equals(mode)||"fork".equals(mode))||!"STORAGE_NOT_FOUND".equals(missing.getCode()))throw missing;}
        System.out.println("PIN_PRODUCER_ONLY_RECOVERED mode="+mode+" selected="+selected);
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home","/root");String action=args[0],mode=args[1];
        need(java.util.Arrays.asList("append","reindex","create","fork").contains(mode),"Known scenario");
        if("producer".equals(action)){producer(mode);return;}
        need(!Files.getAttribute(PRODUCER.getParent(),"unix:dev").equals(Files.getAttribute(CONSUMER.getParent(),"unix:dev")),"Producer and consumer must be distinct devices");
        if("read-producer".equals(action)){producerOnly(mode);for(;;)Thread.sleep(1000);}
        if(action.startsWith("write")){
            Process child=new ProcessBuilder("/opt/java/bin/java","-XX:-UsePerfData","-XX:ActiveProcessorCount=1","-Xmx128m","-cp","/app/producer-overlay:/app/classes:/app/kanger.jar",ContextDevicePinRunner.class.getName(),"producer",mode).redirectErrorStream(true).redirectOutput(new File("/tmp/pin-producer.log")).start();
            long end=System.nanoTime()+600_000_000_000L;
            while(!Files.exists(Paths.get("/tmp/pin-visible"))&&child.isAlive()&&System.nanoTime()<end)Thread.sleep(20);
            need(Files.exists(Paths.get("/tmp/pin-visible")),"Producer pause: "+new String(Files.readAllBytes(Paths.get("/tmp/pin-producer.log")),"UTF-8"));
            need(child.isAlive()&&RevisionStore.read(ContextStore.revisionPath(PRODUCER))==pin(mode),"Exact marker visible without producer ACK");
            System.out.println("PIN_VISIBLE mode="+mode+" target="+pin(mode));if(System.in.read()<0)throw new AssertionError("Host release");
            DB db=new DB();Mind m=open(db,CONSUMER.getParent(),"Consumer");need(m.compile("!local(1);",null,false),"Consumer original");db.flush();db.connectContext(PRODUCER.toString());
            need(child.isAlive()&&!new String(Files.readAllBytes(Paths.get("/tmp/pin-producer.log")),"UTF-8").contains("PIN_PRODUCER_ACK_UNEXPECTED"),"Producer still has no ACK");
            need(db.saveConnections(m)==2,"Consumer saved pin ACK");
            System.out.println("PIN_CONSUMER_ACK mode="+mode+" consumer=2 target="+pin(mode));
        }else read(mode,"read-missing".equals(action));
        for(;;)Thread.sleep(1000);
    }
}
