/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;
import org.kanger.ContextDependentReplayInspection.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Actual owning pending leases, whole physical topology, independent historical replay; no publication. */
public final class ContextDependentPendingReplayRunner {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}require(no,why);}
    private static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path dir,String name)throws Exception{user.setDatabaseDir(dir.toString()+File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static Policy policy(){return new Policy(2000000,1000,100,10000000,50000);}
    private static Request request(Rule p,Rule n,boolean partial){return new Request(policy(),n.getId(),0,partial?Collections.singletonList(p.getId()):Arrays.asList(p.getId(),p.getId()),partial?Collections.singletonList(0):Arrays.asList(0,0));}
    private static String fingerprint(Mind m,Fixture f)throws Exception{return ContextProductionInventory.inspect(m,f.db.getContextId(),f.db.getRevision(),true,Collections.emptyList(),1000).fingerprint;}
    private static String foreign(Fixture f)throws Exception {
        StringBuilder out=new StringBuilder();for(ContextConnection c:f.db.dmzTopology())out.append(ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),1000).fingerprint);return out.toString();
    }
    private static Rule[] prepare(Mind child,boolean reverse)throws Exception {
        String positive="!@a $u @b rel(a,u,b);",negative="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";Rule p,n;
        if(reverse){n=raw(child,negative);p=raw(child,positive);}else{p=raw(child,positive);n=raw(child,negative);}return new Rule[]{p,n};
    }
    private static void routes(Path dir)throws Exception {
        for(boolean trusted:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
            String name="Dependent"+trusted+reverse;long revision;String original;
            try(Fixture f=new Fixture(dir,name)) {
                require(f.root.compile("!anchor(1);",null,false),"physical parent fixture");
                f.db.connectContext(reverse?"Q":"P",trusted?"joint":null);f.db.connectContext(reverse?"P":"Q",trusted?"joint":null);
                require(f.db.dmzTopology().size()==2&&f.db.productionExecutionTopology().size()==(trusted?1:2)&&(!trusted||f.db.productionExecutionTopology().get(0).commune().members().size()==2),"two physical members retain independent or whole TRUST routing");
                revision=f.db.getRevision();original=fingerprint(f.root,f);String foreign=foreign(f);Mind child=new Mind(f.root);for(int gap=0;gap<7;gap++)f.user.nextId("rules");Rule[] rules=prepare(child,reverse);String pending=fingerprint(child,f);Request request=request(rules[0],rules[1],false);Result result;
                ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000);
                try {
                    result=lease.inspectDependentLocal(request);
                    require(result.evidence.closedBranch&&result.evidence.depth==2&&result.evidence.remaining==0,"actual owning mixed pending child yields checked local refutation chain");
                    require(result.evidence.originals.size()==3&&result.evidence.proofSources.size()==2,"whole parent/child originals retained, anchor excluded from proof");
                    Set<Long> actualRules=new HashSet<>();for(ContextProductionCapture.Source source:result.evidence.proofSources)actualRules.add(source.rule);
                    require(actualRules.equals(new HashSet<>(Arrays.asList(rules[0].getId(),rules[1].getId())))&&rules[0].getId()>=8,"Source occurrence IDs remain provider-owned across noncontiguous allocation and private replay IDs");
                    for(ContextProductionCapture.Source source:result.evidence.originals)require(source.context.equals(f.db.getContextId())&&source.revision==revision&&source.localX&&source.view.equals("inventory:"+pending),"Sources originate from actual provider pending inventory, no borrowed foreign identity");
                    Historical replay=ContextDependentReplayInspection.replay(result.replayPacket(),result.pin,policy());require(replay.closedBranch&&replay.proofSources.equals(result.evidence.proofSources),"saved evidence independently restores exact original pending Sources");
                    require(f.db.getRevision()==revision&&original.equals(fingerprint(f.root,f))&&pending.equals(fingerprint(child,f))&&foreign.equals(foreign(f)),"diagnostic changes neither parent, child, pins nor either physical member");
                    require(f.user.getCurrentMind()==f.root&&f.root.requirePendingValidationChild(child)==null,"active caller and exact pending reservation preserved");
                    AtomicBoolean off=new AtomicBoolean();Thread t=new Thread(()->{try{lease.inspectDependentLocal(request);}catch(Exception expected){off.set(true);}});t.start();t.join(5000);require(!t.isAlive()&&off.get(),"off-thread provider diagnostic refuses");
                    require(lease.inspectDependentLocal(request(rules[0],rules[1],true)).evidence.remaining==1,"partial recipe stays partial, never full-family success");
                    Request low=new Request(new Policy(2000000,1000,100,1,50000),rules[1].getId(),0,request.producers,request.pivots);reject(()->lease.inspectDependentLocal(low),"bounded incomplete read cannot approve/settle child");
                    require(original.equals(fingerprint(f.root,f))&&pending.equals(fingerprint(child,f)),"failed bounded diagnostic remains read-only");
                    if(!trusted&&!reverse) {
                        try(Fixture advanced=new Fixture(dir,"P")){require(advanced.root.compile("!headadvance(2);",null,false),"physical foreign HEAD advances");}
                        require(lease.inspectDependentLocal(request).evidence.closedBranch,"immutable connected old revision remains valid after unrelated HEAD advance");
                    }
                    if(trusted){ContextConnection second=f.db.dmzTopology().get(1);raw(second.layer(),"!secondmemberchanged(9);");reject(()->lease.inspectDependentLocal(request),"second physical TRUST member remains guarded even for local-X-only proof");}
                    raw(child,"!later(3);");reject(()->lease.inspectDependentLocal(request),"changed actual pending child invalidates owning lease before replay");
                    require(ContextDependentReplayInspection.replay(result.replayPacket(),result.pin,policy()).closedBranch,"historical packet remains evidence after live lease becomes stale, not write authority");
                }finally{lease.close();}
                reject(()->lease.inspectDependentLocal(request),"closed provider lease cannot reuse detached packet authority");
                f.root.release(child);require(revision==f.db.getRevision()&&original.equals(fingerprint(f.root,f)),"release never publishes the diagnosed counterexample");
            }
            try(Fixture f=new Fixture(dir,name)){require(f.db.getRevision()==revision&&original.equals(fingerprint(f.root,f)),"cold provider reopen contains only original physical parent");}
        }
    }
    private static void boundaries(Path dir)throws Exception {
        try(Fixture f=new Fixture(dir,"Boundaries")) {
            require(f.root.compile("!anchor(1);",null,false),"boundary parent");f.db.connectContext("P");Mind child=new Mind(f.root);Rule[] rules=prepare(child,false);Request request=request(rules[0],rules[1],false);
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                Request wrong=new Request(new Policy(2000000,999,100,10000000,50000),rules[1].getId(),0,request.producers,request.pivots);reject(()->lease.inspectDependentLocal(wrong),"request cannot replace provider row scope");
                f.db.connectContext("Q");Request exact=request;reject(()->lease.inspectDependentLocal(exact),"changed physical topology invalidates diagnostic lease");
            }
            f.root.release(child);child=new Mind(f.root);rules=prepare(child,false);request=request(rules[0],rules[1],false);
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                ContextConnection member=f.db.dmzTopology().get(0);raw(member.layer(),"!changedforeign(9);");Request exact=request;reject(()->lease.inspectDependentLocal(exact),"mutation of actual initialized foreign layer invalidates whole physical baseline");
            }
            f.root.release(child);
        }
        try(Fixture f=new Fixture(dir,"Released")) {
            require(f.root.compile("!anchor(1);",null,false),"reservation parent");Mind child=new Mind(f.root);Rule[] rules=prepare(child,false);Request request=request(rules[0],rules[1],false);
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                f.root.release(child);Mind replacement=new Mind(f.root);
                try{reject(()->lease.inspectDependentLocal(request),"released child cannot borrow replacement reservation for replay");}finally{f.root.release(replacement);}
            }
        }
        for(boolean positive:new boolean[]{false,true}) {
            User user=new User();Mind m=new Mind(user);user.setCurrentMind(m);Rule p=raw(m,"!p(1);"),n=raw(m,"!~p(1);");
            (positive?p:n).setOrigin(m.getTerms().add(positive?"!p(2);":"!~p(2);"));
            ContextProductionInventory current=ContextProductionInventory.inspect(m,new UUID(0,508),0,true,Collections.emptyList(),1000);
            Request request=new Request(policy(),n.getId(),0,Collections.singletonList(p.getId()),Collections.singletonList(0));
            reject(()->ContextDependentReplayInspection.inspect(m,current,request),"fresh inventory stamp cannot hide native/origin mismatch in producer or receiver");
        }
        Mind memory=new Mind(new User());memory.getUser().setCurrentMind(memory);Rule[] rules=prepare(memory,false);ContextProductionInventory inventory=ContextProductionInventory.inspect(memory,new UUID(0,508),0,true,Collections.emptyList(),1000);Request request=request(rules[0],rules[1],false);
        Result result=ContextDependentReplayInspection.inspect(memory,inventory,request);byte[] packet=result.replayPacket();packet[0]^=1;reject(()->ContextDependentReplayInspection.replay(packet,result.pin,policy()),"public historical verifier requires exact pinned packet");
        require(result.replayPacket()[0]!=packet[0],"public result exposes detached bytes");
        try{result.evidence.proofSources.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
        raw(memory,"!extra(4);");reject(()->ContextDependentReplayInspection.inspect(memory,inventory,request),"direct facade refuses stale supplied inventory and has no provider authority");
    }
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("dependent-pending-");System.setProperty("user.home",dir.toString());
        try(Fixture f=new Fixture(dir,"P")){require(f.root.compile("!left(1);",null,false),"first physical member");}
        try(Fixture f=new Fixture(dir,"Q")){require(f.root.compile("!right(1);",null,false),"second physical member");}
        routes(dir);boundaries(dir);System.out.println("CONTEXT_DEPENDENT_PENDING_REPLAY_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
