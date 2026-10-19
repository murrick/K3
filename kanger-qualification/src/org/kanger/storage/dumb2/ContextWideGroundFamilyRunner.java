/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Signed wide-ground inference through actual isolated cuts and original publication. */
public final class ContextWideGroundFamilyRunner {
    static int checks;static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static class Fixture extends ContextWideWitnessFamilyRunner.Fixture{Fixture(Path d,String n)throws Exception{super(d,n);}}
    static String base(int n,boolean nullary){StringBuilder s=new StringBuilder();for(int i=1;i<n;i++)s.append("!p").append(i).append(nullary?";":"(1);");return s.toString();}
    static String rule(int n,boolean negative,boolean nullary){String arg=nullary?"":"(1)";StringBuilder s=new StringBuilder("!q"+arg);for(int i=1;i<n;i++)s.append("&&p").append(i).append(arg);return s+"->"+(negative?"~":"")+"out"+arg+";";}
    static void create(Path d,String n,String text)throws Exception{try(Fixture f=new Fixture(d,n)){require(f.root.compile(text,null,false),"private context "+n);}}
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("wide-ground-");System.setProperty("user.home",dir.toString());
        for(boolean nullary:new boolean[]{false,true}){String suffix=""+nullary,arg=nullary?"":"(1)";create(dir,"A"+suffix,"!q"+arg+";");create(dir,"Safe"+suffix,"!safe"+arg+";");create(dir,"Deny"+suffix,"!~out"+arg+";");create(dir,"Plus"+suffix,"!out"+arg+";");}
        for(int n:new int[]{3,4})for(boolean negative:new boolean[]{false,true})for(boolean nullary:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){
            String suffix=""+nullary,name=n+"-"+negative+nullary+reverse;
            try(Fixture f=new Fixture(dir,"Accept"+name)){
                require(f.root.compile(base(n,nullary),null,false),"signed ground parent");f.db.connectContext((reverse?"Safe":"A")+suffix);f.db.connectContext((reverse?"A":"Safe")+suffix);
                ContextPendingProductionSnapshotSession.Decision d=ContextWideWitnessFamilyRunner.inspect(f,rule(n,negative,nullary));
                require(d.engine==ContextPendingProductionSnapshotSession.Engine.GROUND&&d.ground.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&d.ground.certificate!=null&&d.ground.certificate.models.size()==3,"complete wide signed ground models: "+d.ground.reason);
                long rev=f.db.getRevision();f.guard.install();require(Boolean.TRUE.equals(f.root.query(rule(n,negative,nullary),null,false)),"original native commit after fresh complete models");require(f.db.getRevision()>rev,"ground publication advances revision");
            }
            try(Fixture f=new Fixture(dir,"Accept"+name);ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){require(lease.inventory().x.primary.size()==n,"reopen retains only X source primaries, no raw foreign facts");}
            try(Fixture f=new Fixture(dir,"Refuse"+name)){
                require(f.root.compile(base(n,nullary),null,false),"collision parent");String deny=(negative?"Plus":"Deny")+suffix;f.db.connectContext(reverse?deny:"A"+suffix);f.db.connectContext(reverse?"A"+suffix:deny);
                ContextPendingProductionSnapshotSession.Decision d=ContextWideWitnessFamilyRunner.inspect(f,rule(n,negative,nullary));
                require(d.ground.status==ContextGroundClauseFamily.Status.COLLISION&&!d.ground.collisions.isEmpty(),"wide signed cross-cut collision: "+d.ground.reason);
                require(d.ground.collisions.stream().anyMatch(c->{java.util.Set<org.kanger.ContextProductionCapture.Source> all=new java.util.HashSet<>(c.leftProof.sources);all.addAll(c.rightProof.sources);return all.size()==n+2&&all.stream().filter(s->s.localX).count()==n&&all.stream().filter(s->!s.localX).map(s->s.context).distinct().count()==2&&Math.max(c.leftProof.depth,c.rightProof.depth)>=1;}),"collision keeps every X premise/candidate and both private cuts with nonzero cumulative depth");
                long rev=f.db.getRevision();f.guard.install();StorageLifecycleException failure=null;try{f.root.query(rule(n,negative,nullary),null,false);}catch(StorageLifecycleException expected){failure=expected;}
                require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"actual signed wide refusal typed conflict");require(f.db.getRevision()==rev,"wide refutation leaves physical revision unchanged");
            }
        }
        System.out.println("CONTEXT_WIDE_GROUND_FAMILY_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
