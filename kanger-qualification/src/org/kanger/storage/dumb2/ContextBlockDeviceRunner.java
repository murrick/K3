/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.storage.Step;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.units.Rule;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.exception.StorageLifecycleException;

/** Executed inside the guest against the unmodified packaged provider. */
public final class ContextBlockDeviceRunner {
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home","/root");Path location=Paths.get("/store/Source");
        if(args[0].endsWith("-native-topology")||args[0].endsWith("-native-reindex")){
            String operation=args[0].endsWith("topology")?"topology":"reindex";
            nativeOperation(args[0].startsWith("write-"),operation,location,args[0].startsWith("write-resume-"),args[0].contains("-checked-"));
            for(;;)Thread.sleep(1000);
        }else if("write-interrupt".equals(args[0])){
            ContextStore store=ContextStore.create(location);add(store,0,42L);
            if(store.flush()!=1)throw new AssertionError("R1 ACK required");
            System.out.println("KANGER_BLOCK_PREVIOUS_ACK revision=1 value=42");
            if(System.in.read()<0)throw new AssertionError("Host release required");
            add(store,1,84L);if(store.flush()!=2)throw new AssertionError("R2 ACK");
            System.out.println("KANGER_BLOCK_NEXT_ACK revision=2 value=84");
            for(;;)Thread.sleep(1000);
        }else if("read-interrupt".equals(args[0])){
            // Snapshot reads must precede mutable open or any explicit persistence repair.
            try(ContextSnapshot previous=ContextSnapshot.open(location,1)){check(previous,0,42L);}
            long selected;
            try(ContextSnapshot current=ContextSnapshot.open(location)){
                selected=current.getRevision();
                if(selected!=1&&selected!=2)throw new AssertionError("Invalid recovered CURRENT "+selected);
                check(current,0,42L);if(selected==2)check(current,1,84L);
            }
            System.out.println("KANGER_BLOCK_PREVIOUS_RECOVERED revision=1 selected="+selected);
            for(;;)Thread.sleep(1000);
        }else if(args[0].contains("-")){
            String[] parts=args[0].split("-",2);String operation=parts[1];
            if(!java.util.Arrays.asList("create","append2","fork").contains(operation))
                throw new IllegalArgumentException(operation);
            boolean write="write".equals(parts[0]);
            long revision="create".equals(operation)?0:("append2".equals(operation)?2:1);
            if(write){
                ContextStore store=ContextStore.create(location);
                if(!"create".equals(operation)){
                    add(store,0,42L);if(store.flush()!=1)throw new AssertionError("R1");
                    if("append2".equals(operation)){
                        add(store,1,84L);if(store.flush()!=2)throw new AssertionError("R2");
                    }else{
                        try(ContextSnapshot source=ContextSnapshot.open(location,1)){
                            ContextStore fork=ContextStore.fork(source,Paths.get("/store/Fork"));
                            if(fork.getRevision()!=1)throw new AssertionError("Fork R1");
                        }
                    }
                }
            }else{
                try(ContextSnapshot recovered=ContextSnapshot.open(location,revision)){
                    if(recovered.getRevision()!=revision)throw new AssertionError("Revision");
                    if(revision>0)check(recovered,0,42L);
                    if(revision==2)check(recovered,1,84L);
                }
                if("append2".equals(operation)){
                    try(ContextSnapshot previous=ContextSnapshot.open(location,1)){check(previous,0,42L);}
                }
                if("fork".equals(operation)){
                    try(ContextSnapshot fork=ContextSnapshot.open(Paths.get("/store/Fork"),1)){check(fork,0,42L);}
                }
            }
            System.out.println("KANGER_BLOCK_"+(write?"ACK":"RECOVERED")+" operation="+operation+" revision="+revision);
            for(;;)Thread.sleep(1000);
        }else if("write".equals(args[0])){
            ContextStore store=ContextStore.create(location);
            Step row=new Step();row.setId(0);row.setHash(19);row.setData(42L);store.getBase("index").add(row);
            if(store.flush()!=1)throw new AssertionError("Expected actual R1 acknowledgement");
            System.out.println("KANGER_BLOCK_ACK revision=1 value=42");
            // No orderly shutdown, close or extra sync after the ACK.
            for(;;)Thread.sleep(1000);
        }else{
            DurabilityProfile.requireContext(location);
            try(ContextSnapshot recovered=ContextSnapshot.open(location,1)){
                if(recovered.getRevision()!=1||!Long.valueOf(42).equals(recovered.getBase("index").get(0).getData()))
                    throw new AssertionError("Acknowledged value lost after cold recovery");
            }catch(StorageLifecycleException missing){
                if(!"STORAGE_NOT_FOUND".equals(missing.getCode()))throw missing;
                System.out.println("KANGER_BLOCK_MISSING revision=1 code=STORAGE_NOT_FOUND");
                for(;;)Thread.sleep(1000);
            }
            System.out.println("KANGER_BLOCK_RECOVERED revision=1 value=42");
            for(;;)Thread.sleep(1000);
        }
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Mind open(DB db,String name)throws Exception{
        User user=new User();user.setDatabaseDir("/store/");db.init(user);
        Mind root=new Mind(user);user.setCurrentMind(root);
        Mind current=(Mind)root.useStorage(name);user.setCurrentMind(current);return current;
    }
    private static ContextStore store(DB db)throws Exception{
        java.lang.reflect.Field field=DB.class.getDeclaredField("context");field.setAccessible(true);
        return (ContextStore)field.get(db);
    }
    private static PersistentRecord original(ContextSnapshot snapshot,String expectedLayout)throws Exception{
        ContextBase base=(ContextBase)snapshot.getBase("rules");PersistentRecord found=null;
        for(byte[] bytes:base.publicationImage().values()){
            PersistentRecord record=PersistentRecordCodec.decode(bytes);
            TypeDefinition type=snapshot.resolveType(record.getTypeCode());
            StructuralValue value=StructuralValueCodec.decode(type.getDescriptor(),record.getPayload());
            if(!value.asStruct().get("generated").asBool()){
                need(found==null,"Exactly one original rule expected");found=record;
                need(expectedLayout.equals(type.getDescriptor().getName()),"Wrong selected physical rule layout");
                need(!value.asStruct().get("deleted").asBool(),"Original must remain live");
            }
        }
        need(found!=null,"Original rule missing");return found;
    }
    private static void vector(ContextSnapshot snapshot,int count)throws Exception{
        ConnectionVector dependencies=ConnectionStore.read(snapshot.getLocation(),
                new RevisionRef(snapshot.getContextId(),snapshot.getRevision()));
        need(dependencies.size()==count,"Historical dependency vector changed");
        if(count==1){
            ContextConnection pin=dependencies.getConnections().get(0);
            need(pin.getTargetLocation().equals(Paths.get("/store/Rules")),"Pin locator changed");
            need(pin.getTarget().getRevision()==1,"Exact target revision changed");
            try(ContextSnapshot target=ContextSnapshot.open(pin.getTargetLocation(),1)){
                need(target.getContextId().equals(pin.getTarget().getContextId()),"Target identity changed");
                original(target,"Rule-v2");
            }
        }
    }
    private static void sameContent(ContextSnapshot previous,ContextSnapshot selected,boolean reindex)throws Exception{
        try(DirectoryStream<Path> images=Files.newDirectoryStream(previous.getGeneration(),"*.base")){
        for(Path base:images){
            Path next=selected.getGeneration().resolve(base.getFileName());
            if(reindex&&base.equals(ContextStore.schemaPathForGeneration(previous.getGeneration(),"rules")))continue;
            need(java.util.Arrays.equals(Files.readAllBytes(base),Files.readAllBytes(next)),"Nonmigrated base changed");
        }
        }
        PersistentRecord before=original(previous,reindex?"Rule-v1":"Rule-v2");
        PersistentRecord after=original(selected,reindex&&selected.getRevision()>previous.getRevision()?"Rule-v2":(reindex?"Rule-v1":"Rule-v2"));
        need(before.getId()==after.getId()&&before.getHash()==after.getHash()&&before.getNextId()==after.getNextId(),"Original address changed");
        StructuralValue oldValue=StructuralValueCodec.decode(previous.resolveType(before.getTypeCode()).getDescriptor(),before.getPayload());
        StructuralValue newValue=StructuralValueCodec.decode(selected.resolveType(after.getTypeCode()).getDescriptor(),after.getPayload());
        for(java.util.Map.Entry<String,StructuralValue> entry:oldValue.asStruct().entrySet())
            need(entry.getValue().equals(newValue.asStruct().get(entry.getKey())),"Rule meaning changed: "+entry.getKey());
    }
    private static void nativeOperation(boolean write,String operation,Path location,boolean resume,boolean checked)throws Exception{
        boolean reindex="reindex".equals(operation);long previous=reindex?3:1,next=previous+1;
        if(write){
            DB db;Mind mind;
            if(resume){
                db=new DB();mind=open(db,"Source");
                if(!reindex)db.connectContext("Rules");
            }else{
            DB targetDB=new DB();Mind target=open(targetDB,"Rules");
            need(target.compile("!remote(7);",null,false),"Target original");targetDB.flush();
            need(targetDB.getRevision()==1,"Target R1");
            ((User)target.getUser()).setCurrentMind(target.closeStorage());
            db=new DB();mind=open(db,"Source");
            need(mind.compile("!anchor(1);",null,false),"Source original");db.flush();need(db.getRevision()==1,"Source R1");
            if(reindex){
                ContextStore owner=store(db);ContextBase base=(ContextBase)db.getBase("rules");Rule rule=null;
                for(org.kanger.interfaces.IRule candidate:mind.getRules())if(!candidate.isGenerated())rule=(Rule)candidate;
                need(rule!=null,"Native original available");PersistentRecord record=((ContextStep)base.get(rule.getId())).getPersistentRecord();
                TypeDefinition type=owner.registerType(RuleV1Adapter.TYPE_NAME,RuleV1Adapter.INSTANCE.getDescriptor());
                base.update(new ContextStep(base,new PersistentRecord(record.getId(),record.getHash(),record.getNextId(),type.getTypeCode(),
                        StructuralValueCodec.encode(type.getDescriptor(),RuleV1Adapter.INSTANCE.project(rule,mind)))));
                db.flush();need(db.getRevision()==2,"Legacy Rule-v1 publication R2");
            }
            db.connectContext("Rules");
            if(reindex)need(db.saveConnections(mind)==previous,"Persisted legacy dependency R3");
            }
            need(db.getRevision()==previous,"Actual previous ACK cursor");
            if(resume)try(ContextSnapshot snapshot=ContextSnapshot.open(location,previous)){
                original(snapshot,reindex?"Rule-v1":"Rule-v2");vector(snapshot,reindex?1:0);
            }
            System.out.println("KANGER_BLOCK_PREVIOUS_ACK operation="+operation+" previous="+previous);
            if(System.in.read()<0)throw new AssertionError("Host release required");
            if(reindex)db.reindex(null,mind);
            else need((checked?mind.saveCheckedSymbolicContextConnections():db.saveConnections(mind))==next,"Topology must publish");
            need(db.getRevision()==next,"Operation must advance exactly once");
            // No filesystem work between the acknowledged method return and this marker.
            System.out.println("KANGER_BLOCK_NEXT_ACK operation="+operation+" revision="+next);
        }else{
            long selectedRevision;
            try(ContextSnapshot old=ContextSnapshot.open(location,previous);ContextSnapshot selected=ContextSnapshot.open(location)){
                selectedRevision=selected.getRevision();
                need(selectedRevision==previous||selectedRevision==next,"CURRENT outside attempted transition");
                vector(old,reindex?1:0);vector(selected,reindex||selectedRevision==next?1:0);
                sameContent(old,selected,reindex);
            }
            if(reindex){
                for(int revision=1;revision<=2;revision++)try(ContextSnapshot history=ContextSnapshot.open(location,revision)){
                    vector(history,0);original(history,revision==1?"Rule-v2":"Rule-v1");
                }
            }
            System.out.println("KANGER_BLOCK_NATIVE_RECOVERED operation="+operation+" previous="+previous+" selected="+selectedRevision);
        }
    }
    private static void add(ContextStore store,int id,long value)throws Exception{
        Step row=new Step();row.setId(id);row.setHash(19+id);row.setData(value);if(id>0)row.setNext(store.getBase("index").get(id-1));store.getBase("index").add(row);
    }
    private static void check(ContextSnapshot snapshot,int id,long value)throws Exception{
        if(!Long.valueOf(value).equals(snapshot.getBase("index").get(id).getData()))throw new AssertionError("Lost row "+id);
    }
}
