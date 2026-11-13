/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;

public final class SymbolicCacheStorageInspectionRunner {
    private static int checks;
    private static final String RULES="!$u rel(1,u,1); !@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);";
    private static final ContextDependentReplayInspection.Policy P=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind open(DB db,Path directory,String name)throws Exception{User user=new User();user.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);user.setCurrentMind(m);return m;}
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    private static boolean refuses(SymbolicCacheStorageInspection receipt,DB db,Mind m)throws Exception{try{receipt.requireCurrent(db,m);return false;}catch(Exception expected){return true;}}
    private static void physical()throws Exception{
        Path directory=Files.createTempDirectory("symbolic-cache-physical-");DB db=new DB();Mind m=open(db,directory,"Cache");try{need(m.compile(RULES,null,false),"persisted symbolic fixture");}finally{close(m);}
        db=new DB();m=open(db,directory,"Cache");long initial;UUID identity;SymbolicCacheStorageInspection receipt;
        try{
            initial=db.getRevision();identity=db.getContextId();String before=ContextProductionInventory.inspect(m,identity,initial,true,Collections.emptyList(),1000).fingerprint;receipt=db.inspectSymbolicCache(m);receipt.requireCurrent(db,m);
            need(receipt.context.equals(identity)&&receipt.revision==initial&&receipt.working.inventory.generated.isEmpty(),"provider acquires actual identity/revision and the actual published primary-only view");
            need(receipt.coldProof!=receipt.working.evidence.proof&&ContextDependentProductionReplay.reopen(receipt.coldProof.bytes(),receipt.coldProof.pin,P,1000).originals.size()==3,"cold exact-pin runtime has its own independently replayed original-only proof");
            need(db.getRevision()==initial&&before.equals(ContextProductionInventory.inspect(m,identity,initial,true,Collections.emptyList(),1000).fingerprint),"owning inspection does not publish or alter source inventory");need(receipt.working.inventory.dependentPrimaryView(),"already cache-free physical view retains its existing primary eligibility");
            DB other=new DB();Mind historical=open(other,directory,"Cache@"+initial);try{need(refuses(receipt,other,historical),"another adapter at identical physical pin cannot reuse owner authority");SymbolicCacheStorageInspection own=other.inspectSymbolicCache(historical);need(own.revision==initial&&own.context.equals(identity)&&other.isReadOnly(),"independent historical owner gets its own fresh checked view");boolean wrong=false;try{db.inspectSymbolicCache(historical);}catch(Exception expected){wrong=true;}need(wrong,"foreign runtime cannot be supplied as owning source");}finally{close(historical);}
            need(m.compile("!extra(9);",null,false),"new actual declaration");need(refuses(receipt,db,m),"old owning receipt invalidates after actual publication or inventory change");
            need(db.getRevision()>initial&&db.inspectSymbolicCache(m).working.inventory.primary.size()==4,"actually published addition qualifies after cold cache retirement");
            org.kanger.units.Rule extra=null;for(org.kanger.interfaces.IRule item:m.getRules())if("!extra(9);".equals(((org.kanger.units.Rule)item).getOrigin()))extra=(org.kanger.units.Rule)item;need(extra!=null,"actual published original for metadata mutation");
            extra=m.getRules().editInference(extra);org.kanger.interfaces.ITerm original=m.getTerms().add(extra.getOrigin());long pin=db.getRevision();extra.setOrigin(m.getTerms().add("!extra(9.0);"));
            boolean uncommitted=false;try{db.inspectSymbolicCache(m);}catch(IllegalStateException expected){uncommitted=expected.getMessage().contains("Different complete warm/cold primary declarations");}finally{extra.setOrigin(original);}need(uncommitted&&pin==db.getRevision(),"genuinely unpublished equivalent declaration text cannot borrow published cold evidence");
            db.inspectSymbolicCache(m).requireCurrent(db,m);need(true,"restoring the exact published declaration allows fresh independent evidence");
        }finally{close(m);}
        need(refuses(receipt,db,m),"closed provider cannot reuse old inspection");
        db=new DB();m=open(db,directory,"Cache");try{need(db.getRevision()>initial,"source publication advances physical revision");SymbolicCacheStorageInspection newer=db.inspectSymbolicCache(m);need(newer.revision==db.getRevision()&&newer.working.inventory.primary.size()==4,"cold reconstruction uses the new exact revision");need(refuses(receipt,db,m),"cold reopen requires new owning evidence");}finally{close(m);}
        db=new DB();m=open(db,directory,"Cache@"+initial);try{SymbolicCacheStorageInspection old=db.inspectSymbolicCache(m);need(old.revision==initial&&old.working.inventory.primary.size()==3&&old.context.equals(identity),"explicit old pin remains independently checked after CURRENT advances");need(ContextDependentProductionReplay.reopen(old.coldProof.bytes(),old.coldProof.pin,P,1000).originals.size()==3,"old pin proof never imports new CURRENT declarations");}finally{close(m);}
    }
    private static void aliases()throws Exception{
        Path directory=Files.createTempDirectory("symbolic-cache-physical-alias-");DB db=new DB();Mind m=open(db,directory,"BadCache");try{
            need(m.compile("!@a $u @b rel(a,u,b); !@u rel(1,u,1)->out(1,u); !@u rel(2,u,1)->out(2,u);",null,false),"warm physical native alias fixture");
            long revision=db.getRevision();ContextProductionInventory inventory=ContextProductionInventory.inspect(m,db.getContextId(),revision,true,Collections.emptyList(),1000);need(!inventory.generated.isEmpty(),"actual warm symbolic cache exists before physical close");boolean rejected=false;try{db.inspectSymbolicCache(m);}catch(IllegalStateException expected){rejected=true;}need(rejected,"owning provider does not bless a conflated actual warm cache");need(revision==db.getRevision()&&inventory.fingerprint.equals(ContextProductionInventory.inspect(m,db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"owning refusal leaves physical source unchanged");
        }finally{close(m);}
    }
    private static Mind memory(String text)throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);need(m.compile(text,null,false),"equivalence control declarations");return m;}
    private static ContextDependentSymbolicCacheSnapshot.Snapshot snapshot(Mind m,long revision)throws Exception{return ContextDependentSymbolicCacheSnapshot.capture(m,ContextProductionInventory.inspect(m,new UUID(0,625),revision,true,Collections.emptyList(),1000)).snapshot;}
    private static void equivalence()throws Exception{
        Mind a=memory(RULES),b=memory(RULES);ContextDependentSymbolicCacheSnapshot.Snapshot left=snapshot(a,1),right=snapshot(b,1);need(left!=null&&right!=null,"independent checked runtime controls");ContextDependentSymbolicCacheEquivalence.verify(a,left,b,right);need(!left.inventory.dependentPrimaryView(),"read-only cold comparison grants no inventory promotion");
        User coldUser=new User();Mind cacheFree=new Mind(coldUser);coldUser.setCurrentMind(cacheFree);for(String declaration:new String[]{"!$u rel(1,u,1);","!@u rel(1,u,1)->middle(u);","!@u middle(u)->tail(u);"})cacheFree.compileLine(declaration,false,new java.util.LinkedList<org.kanger.interfaces.ITerm>());
        ContextDependentSymbolicCacheSnapshot.Snapshot cacheFreeView=snapshot(cacheFree,1);need(cacheFreeView!=null&&cacheFreeView.inventory.generated.isEmpty()&&left.inventory.generated.size()==2,"independent primary-only cold control really has a different cache population");ContextDependentSymbolicCacheEquivalence.verify(a,left,cacheFree,cacheFreeView);need(!left.inventory.dependentPrimaryView()&&cacheFreeView.inventory.dependentPrimaryView(),"independently proved warm/cold equivalence does not promote either inventory");
        boolean rejected=false;try{ContextDependentSymbolicCacheEquivalence.verify(a,left,a,left);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"same-runtime comparison is not independent cold acquisition");
        Mind changed=memory(RULES+" !extra(9);");ContextDependentSymbolicCacheSnapshot.Snapshot changedView=snapshot(changed,1);need(changedView!=null,"changed cold primary theory is independently valid");rejected=false;try{ContextDependentSymbolicCacheEquivalence.verify(a,left,changed,changedView);}catch(IllegalStateException expected){rejected=true;}need(rejected,"valid caches do not hide changed cold declarations");
        ContextDependentSymbolicCacheSnapshot.Snapshot differentPin=snapshot(b,2);rejected=false;try{ContextDependentSymbolicCacheEquivalence.verify(a,left,b,differentPin);}catch(IllegalStateException expected){rejected=true;}need(rejected,"different cold pin cannot be compared as exact reconstruction");
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",Files.createTempDirectory("symbolic-cache-storage-driver-").toString());physical();aliases();equivalence();System.out.println("SYMBOLIC_CACHE_STORAGE_INSPECTION_PASS checks="+checks);}
}
