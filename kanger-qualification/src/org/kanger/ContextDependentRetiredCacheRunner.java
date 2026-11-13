/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.units.Rule;
import org.kanger.interfaces.IRule;
import org.kanger.ContextDependentSymbolicCacheSnapshot.*;

/** Deleted derived rows remain fingerprinted, inactive and unable to authorize restored knowledge. */
public final class ContextDependentRetiredCacheRunner {
    private static int checks;private static final UUID ID=new UUID(0,630);
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory(String text)throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);need(m.compile(text,null,false),"retired-cache fixture");return m;}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,ID,1,true,Collections.emptyList(),1000);}
    private static boolean stale(Snapshot s,Mind m)throws Exception{try{s.requireCurrent(m,inventory(m));return false;}catch(IllegalStateException expected){return true;}}
    private static Rule row(Mind m,String predicate)throws Exception{for(IRule r:m.getRules())if(((Rule)r).getDomain().getPredicate(m).getName(m).equals(predicate))return (Rule)r;throw new AssertionError(predicate);}
    private static void mixed(boolean all)throws Exception{
        Mind m=memory("!$u rel(1,u,1); !@u rel(1,u,1)->middle(u); !@u middle(u)->tail(u);");ContextProductionInventory before=inventory(m);need(before.generated.size()==2,"actual generated symbolic rows before retirement");Snapshot old=ContextDependentSymbolicCacheSnapshot.capture(m,before).snapshot;need(old!=null,"live cache was independently proved");
        Rule middle=row(m,"middle");for(IRule r:m.getRules())if(r.isGenerated()&&(all||r.getId()==middle.getId()))((Rule)r).setDeleted(true,m);
        ContextProductionInventory raw=inventory(m);String fingerprint=DmzObservationStateFingerprint.capture(m);Result checked=ContextDependentSymbolicCacheSnapshot.capture(m,raw);need(checked.status==Status.BOUND,"retired cache with independently proved live remainder: "+checked.reason);Snapshot snapshot=checked.snapshot;need(stale(old,m),"retirement invalidates previous snapshot");
        need(snapshot.retiredCacheRows.size()==(all?2:1)&&raw.generated.size()==(all?0:1),"exact inactive/live generated partition");need(!raw.dependentPrimaryView()&&snapshot.diagnosticPrimaryView(m).dependentPrimaryView(),"only owner-checked diagnostic view may account for retirement");
        need(snapshot.evidence.ledger.rootRows+snapshot.evidence.ledger.productRows==raw.generated.size(),"live generated rows still require complete independent proof");need(snapshot.evidence.proof!=null&&raw.primary.size()==3,"retired rows never become primary knowledge");
        ContextDependentModelSearchInspection.Result family=ContextDependentSymbolicFamilyInspection.inspect(m,snapshot,Collections.emptyList());need(family.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING,"whole family composes with the checked retired-row manifest");need(fingerprint.equals(DmzObservationStateFingerprint.capture(m)),"retirement inspection and model search are read-only");
        boolean immutable=false;try{snapshot.retiredCacheRows.clear();}catch(UnsupportedOperationException expected){immutable=true;}need(immutable,"inactive row evidence immutable");
        middle.setDeleted(false,m);need(stale(snapshot,m),"restoring a deleted cache occurrence invalidates the full snapshot");need(ContextDependentSymbolicCacheSnapshot.capture(m,inventory(m)).status==Status.BOUND,"supported restored product must receive a fresh complete proof");
    }
    private static void unsupported()throws Exception{
        Mind m=memory("!seed(1); !ghost(9);");Rule ghost=row(m,"ghost");ghost.setGenerated(true);ghost.setDeleted(true,m);ContextProductionInventory raw=inventory(m);Snapshot snapshot=ContextDependentSymbolicCacheSnapshot.capture(m,raw).snapshot;need(snapshot!=null&&snapshot.retiredCacheRows.contains(ghost.getId()),"inactive unsupported derived content supplies no knowledge");
        need(snapshot.inventory.primary.size()==1&&snapshot.evidence.ledger.productRows==0,"inactive ghost is neither primary nor demanded live product");need(ContextDependentProductionReplay.reopen(snapshot.evidence.proof.bytes(),snapshot.evidence.proof.pin,new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000),1000).originals.size()==1,"portable proof contains only the live original");
        ghost.getCauses().clear();ghost.setQuery(true);need(stale(snapshot,m),"even inactive row changes invalidate the full physical fingerprint");ghost.setQuery(false);ghost.setDeleted(false,m);need(stale(snapshot,m),"restoring unsupported ghost invalidates owner-bound evidence");need(ContextDependentSymbolicCacheSnapshot.capture(m,inventory(m)).status==Status.INCOMPLETE,"restored unsupported ghost cannot borrow inactive-row coverage");
        ghost.setDeleted(true,m);ghost.setQuery(true);need(ContextDependentSymbolicCacheSnapshot.capture(m,inventory(m)).status==Status.INCOMPLETE,"even a deleted query requires separate qualification");ghost.setQuery(false);ghost.setGenerated(false);need(ContextDependentSymbolicCacheSnapshot.capture(m,inventory(m)).status==Status.INCOMPLETE,"deleted primary is not treated as retired derived data");
    }
    public static void main(String[] args)throws Exception{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("retired-cache-home-").toString());mixed(false);mixed(true);unsupported();System.out.println("CONTEXT_DEPENDENT_RETIRED_CACHE_PASS checks="+checks);}
}
