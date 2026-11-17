/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.kanger.*;
import org.kanger.units.Rule;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.*;

/** Real child JVM termination: no Java finally/close/shutdown recovery before cold reopening. */
public final class SymbolicProcessDeathRunner {
    private static int checks;
    private static boolean beforeMarker(String point){return !point.equals("AFTER_MARKER")&&!point.equals("ACTIVATION");}
    private static boolean reclamation(String point){return point.startsWith("RECLAIM_");}
    private static boolean preparation(String point){return !point.equals("BEFORE_MARKER")&&beforeMarker(point)&&!reclamation(point);}
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void halt(String point){System.err.println("KANGER_PROCESS_CUT point="+point);System.err.flush();Runtime.getRuntime().halt(83);}
    private static Mind open(DB db,Path dir,String name)throws Exception{User u=new User();u.setDatabaseDir(dir.toString()+File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static void seed(Path d,String name,String text)throws Exception{DB db=new DB();Mind m=open(db,d,name);try{if(!m.compile(text,null,false))throw new AssertionError("seed");}finally{close(m);}}
    private static ContextStore store(DB db)throws Exception{java.lang.reflect.Field f=DB.class.getDeclaredField("context");f.setAccessible(true);return (ContextStore)f.get(db);}
    private static Set<String> originals(Mind m,DB db)throws Exception{Set<String> out=new HashSet<>();for(ContextProductionInventory.Row row:ContextProductionInventory.inspect(m,db.getContextId(),db.getRevision(),true,Collections.emptyList(),1000).primary){if(!out.add(row.origin))throw new AssertionError("duplicate original");}return out;}
    private static String digest(Path generation)throws Exception{
        java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
        List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.walk(generation)){stream.filter(Files::isRegularFile).forEach(files::add);}Collections.sort(files);
        for(Path file:files){byte[] name=generation.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),bytes=Files.readAllBytes(file);hash.update(java.nio.ByteBuffer.allocate(4).putInt(name.length).array());hash.update(name);hash.update(java.nio.ByteBuffer.allocate(8).putLong(bytes.length).array());hash.update(bytes);}
        return Base64.getEncoder().encodeToString(hash.digest());
    }
    private static void connect(DB db,Mind m)throws Exception{db.connectContext("Rules");db.applyConnectionCommand(m,"Rules","!$u rel(1,u,1);");}
    private static void child(Path dir,String operation,String point)throws Exception{
        System.setProperty("user.home",dir.resolve("home").toString());
        seed(dir,"Source","!marker(9);");seed(dir,"Rules","!@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);");
        DB db=new DB();Mind m=open(db,dir,"Source");long original=-1;
        if(operation.equals("reindex")){
            ContextStore s=store(db);ContextBase b=(ContextBase)db.getBase("rules");Rule rule=null;for(org.kanger.interfaces.IRule r:m.getRules())if(!r.isGenerated())rule=(Rule)r;
            original=rule.getId();PersistentRecord row=((ContextStep)b.get(original)).getPersistentRecord();TypeDefinition type=s.registerType(RuleV1Adapter.TYPE_NAME,RuleV1Adapter.INSTANCE.getDescriptor());
            b.update(new ContextStep(b,new PersistentRecord(original,row.getHash(),row.getNextId(),type.getTypeCode(),StructuralValueCodec.encode(type.getDescriptor(),RuleV1Adapter.INSTANCE.project(rule,m)))));db.flush();
        }
        connect(db,m);if(!operation.equals("save"))m.saveCheckedSymbolicContextConnections();
        Properties plan=new Properties();plan.setProperty("revision",Long.toString(db.getRevision()));plan.setProperty("target",Long.toString(db.versionHistory("Rules").getCurrentRevision()));plan.setProperty("identity",db.getContextId().toString());plan.setProperty("original",Long.toString(original));
        plan.setProperty("sourceDigest",digest(ContextStore.generationPath(dir.resolve("Source"),db.getRevision())));plan.setProperty("targetDigest",digest(ContextStore.generationPath(dir.resolve("Rules"),Long.parseLong(plan.getProperty("target")))));
        try(OutputStream out=Files.newOutputStream(dir.resolve("plan.properties"))){plan.store(out,"pre-cut observations");}
        try(SymbolicPreparationFaults prepared=preparation(point)?new SymbolicPreparationFaults(dir.resolve("Source"),dir.resolve("artifact.properties"),point):null;
            SymbolicPublicationFaults publication=point.equals("AFTER_MARKER")||preparation(point)?null:new SymbolicPublicationFaults(db,dir.resolve("Source"),point.equals("BEFORE_MARKER")?SymbolicPublicationFaults.Point.HALT_BEFORE_MARKER:SymbolicPublicationFaults.Point.HALT_ACTIVATION);
            SymbolicMarkerFaults marker=point.equals("AFTER_MARKER")?new SymbolicMarkerFaults(ContextStore.revisionPath(dir.resolve("Source")),SymbolicMarkerFaults.Point.HALT_AFTER_MOVE):null){
            if(operation.equals("native"))m.writeCheckedSymbolicContextAssertion("!safe(8);",false);
            else if(operation.equals("save"))m.saveCheckedSymbolicContextConnections();
            else ((User)m.getUser()).reindex(null,m,"Source");
        }
        throw new AssertionError("requested cut was not reached");
    }
    private static int executeChild(Path log,String... arguments)throws Exception{
        String executable=Paths.get(System.getProperty("java.home"),"bin","java").toString();List<String> command=new ArrayList<>(Arrays.asList(executable,"-XX:-UsePerfData","-cp",System.getProperty("java.class.path"),SymbolicProcessDeathRunner.class.getName()));command.addAll(Arrays.asList(arguments));
        Process child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();throw new AssertionError("child cut timed out: "+log);}return child.exitValue();
    }
    private static void run(String operation,String point)throws Exception{
        Path dir=Files.createTempDirectory("process-death-"+operation+"-");Path log=dir.resolve("child.log");
        String seedPoint=reclamation(point)?"BEFORE_MARKER":point;
        int exit=executeChild(log,"child",dir.toString(),operation,seedPoint);
        String trace=new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8);
        need(exit==83&&trace.contains("KANGER_PROCESS_CUT point="+seedPoint),"exact child halt boundary "+operation+" "+seedPoint+": "+log);
        if(reclamation(point)){
            Path cleanupLog=dir.resolve("reclaimer.log");exit=executeChild(cleanupLog,"reclaim",dir.toString(),point);
            trace=new String(Files.readAllBytes(cleanupLog),java.nio.charset.StandardCharsets.UTF_8);
            need(exit==83&&trace.contains("KANGER_PROCESS_CUT point="+point),"second process really dies inside reclamation: "+cleanupLog);
        }
        Properties plan=new Properties();try(InputStream in=Files.newInputStream(dir.resolve("plan.properties"))){plan.load(in);}
        long before=Long.parseLong(plan.getProperty("revision")),expected=before+(beforeMarker(point)?0:1);Path location=dir.resolve("Source");
        need(RevisionStore.read(ContextStore.revisionPath(location))==expected,"actual marker names the exact durable side of cut");
        Path interruptedArtifact=null;
        if(preparation(point)){
            Properties artifact=new Properties();try(InputStream in=Files.newInputStream(dir.resolve("artifact.properties"))){artifact.load(in);}Path path=Paths.get(artifact.getProperty("artifact"));interruptedArtifact=path;long full=Long.parseLong(artifact.getProperty("fullLength"));
            need(path.startsWith(dir)&&Files.exists(path),"exact preparation artifact remains inside this fixture");
            if(point.equals("STAGING_COPY"))need(Files.isDirectory(path),"copied hidden staging directory really exists");
            else if(point.endsWith("PARTIAL"))need(Files.size(path)>0&&Files.size(path)<full,"actual file is incomplete, not a synthetic post-write exception");
            else {
                need(Files.size(path)==full&&full>0,"complete forced temporary file exists before atomic replacement");
                byte[] bytes=Files.readAllBytes(path);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(bytes,0,bytes.length-4);
                need((int)crc.getValue()==java.nio.ByteBuffer.wrap(bytes,bytes.length-4,4).getInt(),"complete temporary file has a valid on-disk CRC");
                if(point.equals("MARKER_TEMP"))need(RevisionStore.read(path)==before+1,"valid future temporary marker cannot authorize the unpublished revision");
            }
            need(Files.isDirectory(ContextStore.generationPath(location,before+1))==point.startsWith("MARKER_"),"generation installation agrees with exact preparation boundary");
        }
        if(reclamation(point)){
            Properties evidence=new Properties();try(InputStream input=Files.newInputStream(dir.resolve("reclaim.properties"))){evidence.load(input);}
            interruptedArtifact=Paths.get(evidence.getProperty("artifact"));long count=Long.parseLong(evidence.getProperty("before")),remaining=Long.parseLong(evidence.getProperty("remaining"));
            need(interruptedArtifact.startsWith(ContextStore.stateRoot(location))&&interruptedArtifact.getFileName().toString().startsWith(".reclaim-")&&Files.isDirectory(interruptedArtifact),"retired generation is in the reserved cleanup namespace");
            need(!Files.exists(ContextStore.generationPath(location,before+1)),"retirement removes only the unpublished canonical name");
            need(count>1&&(point.equals("RECLAIM_RETIRED")?remaining==count:remaining>0&&remaining<count),"real unlink occurred only at the selected cleanup boundary");
            if(point.equals("RECLAIM_PARTIAL")){boolean broken=false;try{RevisionManifestStore.validate(interruptedArtifact,UUID.fromString(plan.getProperty("identity")),before+1,before);}catch(IOException|org.kanger.exception.StorageLifecycleException e){broken=true;}need(broken,"partial deletion destroys the original seal, requiring resumable retirement ownership");}
        }
        if(beforeMarker(point)){boolean hidden=false;try(ContextSnapshot future=ContextSnapshot.open(location,before+1)){}catch(org.kanger.exception.StorageLifecycleException e){hidden=true;}need(hidden,"unpublished next revision remains hidden at every pre-marker cut");}
        if(point.equals("BEFORE_MARKER")){need(Files.isDirectory(ContextStore.generationPath(location,before+1)),"unpublished generation really installed before death");boolean hidden=false;try(ContextSnapshot future=ContextSnapshot.open(location,before+1)){}catch(org.kanger.exception.StorageLifecycleException e){hidden=true;}need(hidden,"orphan cannot be read as published pinned revision");}
        DB db=new DB();Mind m=open(db,dir,"Source");
        try{
            need(db.getContextId().toString().equals(plan.getProperty("identity"))&&db.getRevision()==expected,"cold open reacquires OS lock and exact published identity/cursor");
            boolean saved=!operation.equals("save")||!beforeMarker(point);need(db.dmzTopology().size()==(saved?1:0),"only published topology survives process death");
            Set<String> wanted=new HashSet<>(Collections.singletonList("!marker(9);"));if(operation.equals("native")&&!beforeMarker(point))wanted.add("!safe(8);");need(originals(m,db).equals(wanted),"exact durable originals; unpersisted accepted memory is not fabricated");
            if(saved){ContextConnection c=db.dmzTopology().get(0);need(c.getInitialization().equals(Collections.singletonList("!$u rel(1,u,1);")),"full private recipe replays after restart");need(c.getCertificate().matches(new RevisionRef(db.getContextId(),expected),c.getTarget(),c.getCertificate().getSemanticVersion()),"cold certificate binds actual source");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"fresh whole-family evidence qualifies after restart");}
            if(operation.equals("reindex")){ContextStore s=store(db);ContextBase b=(ContextBase)db.getBase("rules");String layout=s.resolveType(((ContextStep)b.get(Long.parseLong(plan.getProperty("original")))).getPersistentRecord().getTypeCode()).getDescriptor().getName();need(layout.equals(beforeMarker(point)?"Rule-v1":"Rule-v2"),"cold physical layout matches selected marker");}
            ((User)m.getUser()).checkpoint(m);need(db.getRevision()==expected,"plain checkpoint never publishes abandoned memory or orphan generation");
            if(interruptedArtifact!=null)need(!Files.exists(interruptedArtifact,LinkOption.NOFOLLOW_LINKS),"validated quiescent checkpoint reclaims this interrupted artifact before retry");
            if(point.equals("BEFORE_MARKER")||point.startsWith("MARKER_"))need(!Files.exists(ContextStore.generationPath(location,before+1)),"checkpoint reclaims the sealed owned immediate-successor generation without publishing it");
            if(beforeMarker(point)){
                if(operation.equals("native"))m.writeCheckedSymbolicContextAssertion("!safe(8);",false);
                else if(operation.equals("save")){connect(db,m);m.saveCheckedSymbolicContextConnections();}
                else db.reindex(null,m);
            }
            need(db.getRevision()==before+1,"fresh explicit retry or adopted publication has exactly one next revision");need(m.saveCheckedSymbolicContextConnections()==before+1,"recovered no-op cannot duplicate publication");
            need(db.versionHistory("Rules").getCurrentRevision()==Long.parseLong(plan.getProperty("target")),"private recovery leaves physical target unchanged");
        }finally{close(m);}
        db=new DB();m=open(db,dir,"Source");try{need(db.getRevision()==before+1,"second cold open retains recovered revision");need(originals(m,db).equals(operation.equals("native")?new HashSet<>(Arrays.asList("!marker(9);","!safe(8);")):Collections.singleton("!marker(9);")),"second cold open has exact original set");db.inspectSymbolicCacheFamily(m).requireCurrentModelled(db,m);need(true,"recovered family remains usable after another reopen");}finally{close(m);}
        need(digest(ContextStore.generationPath(location,before)).equals(plan.getProperty("sourceDigest")),"previous published source generation is byte-identical after death and retry");
        need(digest(ContextStore.generationPath(dir.resolve("Rules"),Long.parseLong(plan.getProperty("target")))).equals(plan.getProperty("targetDigest")),"physical target generation is byte-identical after death and replay");
        System.out.println("PROCESS_DEATH_CASE operation="+operation+" point="+point+" passed");
    }
    static void preparationMatrix()throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("preparation-death-home-").toString());
        for(String operation:new String[]{"native","save","reindex"})for(String point:new String[]{"STAGING_COPY","SCHEMA_PARTIAL","SIDECAR_PARTIAL","SIDECAR_TEMP","MARKER_PARTIAL","MARKER_TEMP"}){
            if(operation.equals("save")&&point.equals("SCHEMA_PARTIAL"))continue;
            run(operation,point);
        }
        System.out.println("SYMBOLIC_PREPARATION_DEATH_PASS checks="+checks);
    }
    static void reclamationMatrix()throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("reclamation-death-home-").toString());
        for(String point:new String[]{"RECLAIM_RETIRED","RECLAIM_PARTIAL"})run("native",point);
        System.out.println("SYMBOLIC_ORPHAN_RECLAMATION_DEATH_PASS checks="+checks);
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("child")){child(Paths.get(args[1]),args[2],args[3]);return;}
        if(args.length>0&&args[0].equals("reclaim")){
            Path dir=Paths.get(args[1]);System.setProperty("user.home",dir.resolve("home").toString());DB db=new DB();Mind m=open(db,dir,"Source");
            try(SymbolicReclamationFaults cut=new SymbolicReclamationFaults(dir.resolve("Source"),dir.resolve("reclaim.properties"),args[2])){((User)m.getUser()).checkpoint(m);}throw new AssertionError("reclamation cut not reached");
        }
        System.setProperty("user.home",Files.createTempDirectory("process-death-home-").toString());
        for(String operation:new String[]{"native","save","reindex"})for(String point:new String[]{"BEFORE_MARKER","AFTER_MARKER","ACTIVATION"})run(operation,point);
        System.out.println("SYMBOLIC_PROCESS_DEATH_PASS checks="+checks);
    }
}
