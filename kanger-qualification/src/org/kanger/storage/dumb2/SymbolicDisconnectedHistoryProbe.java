/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import org.kanger.*;

/** Permanent public-API regression for the stage-642 first-sidecar history gap. */
public final class SymbolicDisconnectedHistoryProbe {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Mind open(DB db,Path dir,String name)throws Exception {
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);db.init(user);
        Mind root=new Mind(user);user.setCurrentMind(root);Mind mind=(Mind)root.useStorage(name);user.setCurrentMind(mind);return mind;
    }
    private static void close(Mind mind)throws Exception{((User)mind.getUser()).setCurrentMind(mind.closeStorage());}
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("history-home-").toString());
        Path dir=Files.createTempDirectory("disconnected-history-");DB target=new DB();Mind t=open(target,dir,"Rules");
        try{need(t.compile("!target(1);",null,false),"target fixture");}finally{close(t);}
        DB db=new DB();Mind m=open(db,dir,"Source");long first,current;
        try {
            need(m.compile("!marker(9);",null,false),"first revision");first=db.getRevision();
            need(m.compile("!second(1);",null,false),"second revision");
            need(db.exists("Source@"+first),"history readable before first sidecar");
            need(db.exists("Source@0"),"initial revision readable before first sidecar");
            need(!Files.exists(ConnectionStore.path(dir.resolve("Source"))),"history starts without sidecar");
            db.connectContext("Rules");m.saveCheckedSymbolicContextConnections();current=db.getRevision();
            need(db.exists("Source@"+first),"history readable after first sidecar");
            need(db.exists("Source@0"),"initial revision readable after first sidecar");
            byte[] sidecar=Files.readAllBytes(ConnectionStore.path(dir.resolve("Source")));
            try(ContextSnapshot old=ContextSnapshot.open(dir.resolve("Source"),first)){
                need(old.getRevision()==first,"historical snapshot remains pinned");
                need(ConnectionStore.read(dir.resolve("Source"),new RevisionRef(old.getContextId(),first)).isEmpty(),"history has no connected Rules");
            }
            need(java.util.Arrays.equals(sidecar,Files.readAllBytes(ConnectionStore.path(dir.resolve("Source")))),"reading history does not rewrite sidecar");
            ((User)m.getUser()).checkpoint(m);
            need(db.exists("Source@"+first),"history readable after maintenance checkpoint");
            need(db.getRevision()==current,"checkpoint did not publish");
        } finally {close(m);}
        DB pinned=new DB();Mind old=open(pinned,dir,"Source@"+first);
        try {
            need(pinned.getRevision()==first,"public reopen is pinned");
            need(Boolean.TRUE.equals(old.query("?marker(9);")),"historical facts readable");
            need(!Boolean.TRUE.equals(old.query("?second(1);")),"later facts do not leak");
            need(!Boolean.TRUE.equals(old.query("?target(1);")),"later connections do not leak");
        } finally {close(old);}
        DB zero=new DB();Mind initial=open(zero,dir,"Source@0");
        try {
            need(zero.getRevision()==0,"initial public reopen stays pinned");
            need(!Boolean.TRUE.equals(initial.query("?marker(9);")),"initial view has no later marker");
            need(!Boolean.TRUE.equals(initial.query("?second(1);")),"initial view has no later second fact");
            need(!Boolean.TRUE.equals(initial.query("?target(1);")),"initial view has no later connection");
        } finally {close(initial);}
        DB latest=new DB();Mind now=open(latest,dir,"Source");
        try {
            need(latest.getRevision()==current,"historical read did not publish");
            need(Boolean.TRUE.equals(now.query("?second(1);")),"current facts retained");
            need(Boolean.TRUE.equals(now.query("?target(1);")),"current connection retained");
        } finally {close(now);}
        System.out.println("SYMBOLIC_DISCONNECTED_HISTORY_PASS checks="+checks);
    }
}
