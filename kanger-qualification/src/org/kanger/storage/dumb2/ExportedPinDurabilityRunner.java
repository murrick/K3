/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.kanger.*;

/** Two real JVMs, distinct metadata directories, producer paused before its final barrier. */
public final class ExportedPinDurabilityRunner {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory,String name)throws Exception{
        User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);
        Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;
    }
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[1]).toAbsolutePath();
        System.setProperty("user.home",root.resolve(args[0]+"-home").toString());
        Path producer=root.resolve("producer-db/Producer");
        if("producer".equals(args[0])){
            DB db=new DB();Mind m=open(db,producer.getParent(),"Producer");
            need(m.compile("!seed(1);",null,false),"producer seed");db.flush();need(db.getRevision()==1,"producer predecessor R1");
            Files.write(root.resolve("previous-marker.bin"),Files.readAllBytes(ContextStore.revisionPath(producer)));
            ExportedPinPause.arm(ContextStore.revisionPath(producer),root.resolve("producer-visible"));
            need(m.compile("!fresh(2);",null,false),"producer delta");db.flush();
            throw new AssertionError("Paused producer must not acknowledge before the consumer");
        }
        if(!"coordinate".equals(args[0])||args.length!=3)throw new IllegalArgumentException("coordinate, fresh root, producer classpath");
        Files.createDirectory(root);
        Path log=root.resolve("producer.log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin/java").toString(),"-XX:-UsePerfData","-cp",args[2],
                ExportedPinDurabilityRunner.class.getName(),"producer",root.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            long deadline=System.nanoTime()+90_000_000_000L;
            while(!Files.exists(root.resolve("producer-visible"))&&child.isAlive()&&System.nanoTime()<deadline)Thread.sleep(20);
            need(Files.exists(root.resolve("producer-visible")),"producer reached real replacement: "+new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8));
            need(RevisionStore.read(ContextStore.revisionPath(producer))==2,"producer R2 is visible without its ACK");
            DB db=new DB();Mind m=open(db,root.resolve("consumer-db"),"Consumer");
            try{
                need(m.compile("!local(1);",null,false),"consumer original");db.flush();
                db.connectContext(producer.toString());
                long accepted=m.saveCheckedSymbolicContextConnections();
                need(accepted==2&&db.dmzTopology().size()==1,"consumer acknowledged a real topology publication");
                need(db.dmzTopology().get(0).getTarget().getRevision()==2,"consumer pins visible producer R2");
                System.out.println("EXPORTED_PIN_CONSUMER_ACK revision="+accepted+" target=2");
            }finally{close(m);}
        }finally{
            child.destroyForcibly();need(child.waitFor(10,TimeUnit.SECONDS),"paused producer terminated");
        }
        need(RevisionStore.read(ContextStore.revisionPath(producer))==2,"process death alone does not roll back living-FS marker");
        System.out.println("EXPORTED_PIN_FIXTURE_PASS checks="+checks);
    }
}
