/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;import java.util.*;
import org.kanger.*;import org.kanger.interfaces.IRule;import org.kanger.units.Rule;

/** Actual fresh-hydrated persistent predecessor must update the owning chain head. */
public final class PersistentCacheUnlinkRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory)throws Exception{User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage("Unlink");u.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static Set<String> names(Mind m)throws Exception{Set<String> out=new HashSet<>();for(IRule r:m.getRules())out.add(((Rule)r).getDomain().getPredicate(m).getName(m));return out;}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("unlink-home-").toString());Path directory=Files.createTempDirectory("persistent-unlink-");DB db=new DB();Mind m=open(db,directory);try{need(m.compile("!a(1); !b(1); !c(1);",null,false),"persist three linked originals");}finally{close(m);}
        db=new DB();m=open(db,directory);try{need(names(m).equals(new HashSet<>(Arrays.asList("a","b","c"))),"three actual persisted originals");Rule middle=null;for(IRule r:m.getRules())if(((Rule)r).getDomain().getPredicate(m).getName(m).equals("b"))middle=(Rule)r;need(middle!=null,"middle physical row");middle.setDeleted(true,m);m.getRules().pack();need(names(m).equals(new HashSet<>(Arrays.asList("a","c"))),"unlinking the immediate successor of the persistent head preserves the older tail");m.getRules().pack();need(names(m).equals(new HashSet<>(Arrays.asList("a","c"))),"repeated pack preserves the full remaining chain");}finally{close(m);}
        db=new DB();m=open(db,directory);try{need(names(m).equals(new HashSet<>(Arrays.asList("a","c"))),"both live originals survive checkpoint and cold reopening");}finally{close(m);}
        System.out.println("PERSISTENT_CACHE_UNLINK_PASS checks="+checks);
    }
}
