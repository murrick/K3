/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.exception.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Actual native wide-rule family qualification, publication and cross-cut refutation. */
public final class ContextWideWitnessFamilyRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static class Fixture extends ContextAutomaticPendingCommitRunner.Fixture {Fixture(Path dir,String name)throws Exception{super(dir,name);}}
    static void create(Path dir,String name,String text)throws Exception{try(Fixture f=new Fixture(dir,name)){require(f.root.compile(text,null,false),"fixture "+name);}}
    static String base(int n){StringBuilder s=new StringBuilder("!$w seed(w,1)");for(int i=1;i<n;i++)s.append("&&flag").append(i).append("(w,1)");return s+"; !@w seed(w,1)->warm(w,1);";}
    static String rule(int n,boolean ground){StringBuilder s=new StringBuilder("!@w q(w,1)");for(int i=1;i<n;i++)s.append("&&flag").append(i).append("(w,1)");return s+"->out"+(ground?"":"(w,1)")+";";}
    static ContextPendingProductionSnapshotSession.Decision inspect(Fixture f,String text)throws Exception{
        Mind child=new Mind(f.root);require(child.compile(text,null,false),"pending native rule");
        try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){
            ContextPendingProductionSnapshotSession.Decision d=lease.inspectAutomatic(16,16,16,20000,1000000,WORK,500000,WORK,WORK,100000);lease.requireCurrent();return d;
        }finally{f.root.release(child);}
    }
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("wide-witness-family-");System.setProperty("user.home",dir.toString());
        create(dir,"A","!@w seed(w,1)->q(w,1);");create(dir,"Safe","!@w seed(w,1)->safe(w,1);");
        create(dir,"DenyGround","!~out;");create(dir,"DenyWitness","!@w out(w,1)->~flag1(w,1);");
        for(int n:new int[]{3})for(boolean ground:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){
            String name="Accept"+n+ground+reverse;
            try(Fixture f=new Fixture(dir,name)){
                require(f.root.compile(base(n),null,false),"native witness source");f.db.connectContext(reverse?"Safe":"A");f.db.connectContext(reverse?"A":"Safe");
                ContextPendingProductionSnapshotSession.Decision d=inspect(f,rule(n,ground));
                require(d.engine==ContextPendingProductionSnapshotSession.Engine.WITNESS&&d.witness.status==ContextProductionFamilyAssembly.Status.FIXED_POINT,"whole wide family fixed point: "+d.witness.reason);
                long revision=f.db.getRevision();f.guard.install();require(Boolean.TRUE.equals(f.root.query(rule(n,ground),null,false)),"original guarded native publication");require(f.db.getRevision()>revision,"successful write advances revision");
            }
            try(Fixture f=new Fixture(dir,name);ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){
                require(lease.inventory().x.primary.size()==3,"durable wide rule reopens as primary, no imported foreign fact rows");
            }
            try(Fixture f=new Fixture(dir,"Refuse"+n+ground+reverse)){
                require(f.root.compile(base(n),null,false),"refutation source");f.db.connectContext(reverse?(ground?"DenyGround":"DenyWitness"):"A");f.db.connectContext(reverse?"A":(ground?"DenyGround":"DenyWitness"));
                ContextPendingProductionSnapshotSession.Decision d=inspect(f,rule(n,ground));
                require(d.engine==ContextPendingProductionSnapshotSession.Engine.WITNESS&&d.witness.status==ContextProductionFamilyAssembly.Status.COLLISION,"whole wide cross-cut collision: "+d.witness.reason);
                require(d.witness.refutations.stream().flatMap(e->e.proofs.stream()).anyMatch(p->p.depth>=2&&p.sources.stream().anyMatch(s->s.localX)&&p.sources.stream().filter(s->!s.localX).map(s->s.context).distinct().count()>=2),"refutation retains full cross-cut dependencies and depth");
                long revision=f.db.getRevision();f.guard.install();StorageLifecycleException failure=null;try{f.root.query(rule(n,ground),null,false);}catch(StorageLifecycleException expected){failure=expected;}
                require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"actual native refusal typed conflict");require(f.db.getRevision()==revision,"collision does not publish");
            }
        }
        try(Fixture f=new Fixture(dir,"Outside")){
            require(f.root.compile(base(3),null,false),"boundary source");f.db.connectContext("A");String tooWide=rule(3,true).replace("->out","&&seed(w,1)->out");
            boolean incomplete=false;try{inspect(f,tooWide);}catch(IllegalStateException expected){incomplete=expected.getMessage().contains("Native clause width limit");}require(incomplete,"four premises retain native binding INCOMPLETE");
            long revision=f.db.getRevision();f.guard.install();StorageLifecycleException failure=null;try{f.root.query(tooWide,null,false);}catch(StorageLifecycleException expected){failure=expected;}
            require(failure!=null&&failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"outside bound cannot fall through to ordinary publication");require(f.db.getRevision()==revision,"unsupported write leaves revision unchanged");
        }
        System.out.println("CONTEXT_WIDE_WITNESS_FAMILY_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
