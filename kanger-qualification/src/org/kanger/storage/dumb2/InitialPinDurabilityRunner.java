/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.kanger.*;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.Step;

/** A live-filesystem regression: initial creator must never retract a pin already exported. */
public final class InitialPinDurabilityRunner {
    private static Mind open(DB db,Path directory,String name)throws Exception{
        User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);
        Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;
    }
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[1]).toAbsolutePath(),producer=root.resolve("producer-db/Producer"),consumer=root.resolve("consumer-db/Consumer");
        System.setProperty("user.home",root.resolve(args[0]+"-home").toString());
        boolean fork=args.length>3&&"fork".equals(args[3]);long pin=fork?1:0;
        if("producer".equals(args[0])){
            Path source=root.resolve("fork-source/Source");
            if(fork)try(ContextStore original=ContextStore.create(source)){
                Step row=new Step();row.setId(0);row.setHash(19);row.setData(1L);original.getBase("index").add(row);original.flush();
            }
            InitialPinPause.marker=ContextStore.revisionPath(producer);InitialPinPause.root=root;
            boolean refused=false;
            try{
                if(fork)try(ContextSnapshot original=ContextSnapshot.open(source);ContextStore ignored=ContextStore.fork(original,producer)){}
                else try(ContextStore ignored=ContextStore.create(producer)){}
            }catch(java.io.IOException expected){refused=true;}
            if(!refused)throw new AssertionError("Initial failure did not reach creation");
            System.out.println("INITIAL_CREATOR_REFUSED");return;
        }
        Files.createDirectory(root);Path log=root.resolve("producer.log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin/java").toString(),"-XX:-UsePerfData","-cp",args[2],
                InitialPinDurabilityRunner.class.getName(),"producer",root.toString(),args[2],fork?"fork":"create").redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long acknowledged;
        try{
            long deadline=System.nanoTime()+90_000_000_000L;
            while(!Files.exists(root.resolve("visible"))&&child.isAlive()&&System.nanoTime()<deadline)Thread.sleep(20);
            if(!Files.exists(root.resolve("visible")))throw new AssertionError("Producer did not expose its initial marker: "+new String(Files.readAllBytes(log),"UTF-8"));
            DB db=new DB();Mind m=open(db,consumer.getParent(),"Consumer");
            try{
                if(!m.compile("!local(1);",null,false))throw new AssertionError("Consumer seed");db.flush();db.connectContext(producer.toString());
                acknowledged=m.saveCheckedSymbolicContextConnections();
                if(acknowledged!=2||db.dmzTopology().get(0).getTarget().getRevision()!=pin)throw new AssertionError("Real consumer exact pin required");
            }finally{close(m);}
            Files.write(root.resolve("release"),new byte[]{1});
            if(!child.waitFor(20,TimeUnit.SECONDS)||child.exitValue()!=0)throw new AssertionError("Producer did not return its injected failure: "+new String(Files.readAllBytes(log),"UTF-8"));
        }finally{if(child.isAlive()){child.destroyForcibly();child.waitFor(10,TimeUnit.SECONDS);}}
        boolean readable=true;
        try(ContextSnapshot ignored=ContextSnapshot.open(producer,pin)){}catch(StorageLifecycleException failure){
            if(!"STORAGE_NOT_FOUND".equals(failure.getCode()))throw failure;readable=false;
        }
        if(readable)try(ContextStore owner=ContextStore.open(producer)){
            if(owner.getRevision()!=pin)throw new AssertionError("Mutable reopen must confirm the same initial revision");
        }
        try(ContextSnapshot own=ContextSnapshot.open(consumer)){
            if(own.getRevision()!=acknowledged||ConnectionStore.read(consumer,new RevisionRef(own.getContextId(),acknowledged)).getConnections().get(0).getTarget().getRevision()!=pin)
                throw new AssertionError("Consumer acknowledgement changed");
        }
        System.out.println("INITIAL_EXPORTED_PIN consumer="+acknowledged+" pinned="+pin+" readable="+readable);
        if(!readable)System.exit(1);
    }
}
