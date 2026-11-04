/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.ContextDependentConstructedStartupInspection.*;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
public final class ContextDependentConstructedStartupRunner {
    static int checks;
    static void need(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    static Rule raw(Mind m,String s)throws Exception{return (Rule)m.compileLine(s,false,new LinkedList<ITerm>());}
    static Member member(Mind m,int id,boolean local)throws Exception{return new Member(m,ContextProductionInventory.inspect(m,new UUID(0,id),3,local,Collections.emptyList(),1000));}
    static SemanticTermSnapshot value(String s)throws Exception{Mind m=memory();return SemanticTermSnapshot.capture(raw(m,"!value("+s+");").getDomain().get(0).getValue(m));}
    static Request request(List<String> added,int attempts,int rebase)throws Exception{
        ContextDependentReplayInspection.Policy policy=new ContextDependentReplayInspection.Policy(2000000,1000,1000,64000000,200000);
        ContextDependentIterationInspection.Request iteration=new ContextDependentIterationInspection.Request(policy,10000,8);
        ContextDependentModelSearchInspection.Limits limits=new ContextDependentModelSearchInspection.Limits(30,100000,100000,attempts,8000000,8000000,20000000,8000000,8000000);
        ContextDependentRuntimeContinuationInspection.Projection projection=new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,4000000,4000000,4000000,4000000);
        return new Request(new ContextDependentCandidateInspection.Request(iteration,added),new ContextDependentModelSearchInspection.Request(iteration,Collections.emptyList(),value("9"),limits,projection),rebase);
    }
    static Result run(Member b,Member c,List<Cut> cuts,List<String> added)throws Exception{return ContextDependentConstructedStartupInspection.inspect(b,c,cuts,request(added,20000,4000000));}
    static void modelled(Result r){need(r.status==Status.RUNTIME_MODELLED_PENDING,r.status+"/"+r.phase+"/"+r.reason);need(r.startup!=null&&r.preparation!=null&&r.candidate==r.startup.projection.qualification.candidate,"only final freshly checked candidate advertised");need(r.baseTried>0&&r.proposedTried>0,"both interpretations constructed");need(r.startup.runtimeObligations==r.startup.projection.qualification.proposedFinalObligations,"independent indexed/native raw count");}
    static void incomplete(Result r){need(r.status==Status.INCOMPLETE,r.status+"/"+r.phase+"/"+r.reason);need(r.startup==null&&r.candidate==null&&r.preparation==null,"failure retains no model/candidate/startup prefix");}
    static void local()throws Exception{
        Mind p=memory();raw(p,"!seed(1);");Member b=member(p,4701,true);Mind c=new Mind(p);Rule r=raw(c,"!@x seed(x)->out(x);");Member child=member(c,4701,true);List<String> added=Collections.singletonList(r.getOrigin());int terms=c.getTerms().size();Result found=run(b,child,Collections.emptyList(),added);modelled(found);need(found.startup.runtimeObligations>0,"actual derived output qualified");need(c.getTerms().size()==terms,"no native writes");
        incomplete(ContextDependentConstructedStartupInspection.inspect(b,child,Collections.emptyList(),request(added,1,4000000)));
        incomplete(run(b,child,Collections.emptyList(),Collections.singletonList("!wrong(1);")));
        raw(c,"!changed(2);");boolean stale=false;try{run(b,child,Collections.emptyList(),added);}catch(Exception expected){stale=true;}need(stale,"stale actual child refuses");p.release(c);
    }
    static void functions()throws Exception{
        for(int rank:new int[]{1,7,17,65}){Mind p=memory();StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<rank;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append('x').append(i);}Rule witness=raw(p,"!"+prefix+"$u rel("+args+",u);");Member b=member(p,4800+rank,true);Mind c=new Mind(p);Rule out=raw(c,"!"+prefix+"@u rel("+args+",u)->out("+args+",u);");Member child=member(c,4800+rank,true);Result found=run(b,child,Collections.emptyList(),Collections.singletonList(out.getOrigin()));modelled(found);Map<Function,Function> map=found.startup.projection.qualification.reboundFunctions.values().iterator().next();need(map.size()==1,"one exact preserved witness declaration");Function old=map.keySet().iterator().next(),next=map.get(old);need(old.source.equals(new ContextProductionCapture.Source(b.inventory.context,b.inventory.revision,witness.getId(),true,"inventory:"+b.inventory.fingerprint)),"constructed model uses parent ORIGINAL Source");need(!old.source.equals(next.source)&&next.binder==rank,"fresh qualification translates back to actual child Source/rank");need(found.preparation.projection.qualification.proposedOriginalModels.values().iterator().next().functions.containsKey(old),"exported configuration cannot contain actual-child alias");if(rank==1){Result cap=ContextDependentConstructedStartupInspection.inspect(b,child,Collections.emptyList(),request(Collections.singletonList(out.getOrigin()),20000,1));incomplete(cap);need(cap.phase.equals("parent-original-rebase")&&cap.baseTried>0&&cap.proposedTried>0,"late failure clears both successful searches");}p.release(c);}
    }
    static void scopes()throws Exception{
        Mind p=memory();Member b=member(p,4901,true);Mind c=new Mind(p);Rule out=raw(c,"!@x p(x)->out(x);");Member child=member(c,4901,true);Mind am=memory(),bm=memory();raw(am,"!p(1);");raw(bm,"!~p(1);");Member a=member(am,4902,false),z=member(bm,4903,false);List<Cut> cuts=Arrays.asList(new Cut(null,Collections.singletonList(a)),new Cut(null,Collections.singletonList(z)));List<String> added=Collections.singletonList(out.getOrigin());Result r=run(b,child,cuts,added);modelled(r);need(r.preparation.projection.qualification.proposedOriginalModels.size()==3,"independent cuts stay separate");List<Cut> reverse=new ArrayList<>(cuts);Collections.reverse(reverse);Result other=run(b,child,reverse,added);modelled(other);need(other.startup.runtimeObligations==r.startup.runtimeObligations,"cut reversal preserves raw obligations");Result trust=ContextDependentConstructedStartupInspection.inspect(b,child,Collections.singletonList(new Cut("team",Arrays.asList(a,z))),request(added,1,1));need(trust.status==Status.BASE_REFUTED&&trust.candidate!=null&&!trust.candidate.oppositions.isEmpty(),"whole TRUST sourced refutation precedes insufficient model bounds");need(trust.baseTried==0&&trust.proposedTried==0&&trust.preparation==null,"no guesses or model prefix for source refutation");p.release(c);
        Mind parent=memory();raw(parent,"!p(1);");Member base=member(parent,4911,true);Mind proposed=new Mind(parent);Rule conflict=raw(proposed,"!~p(1);");Result no=ContextDependentConstructedStartupInspection.inspect(base,member(proposed,4911,true),Collections.emptyList(),request(Collections.singletonList(conflict.getOrigin()),1,1));need(no.status==Status.CANDIDATE_REFUTED&&no.candidate.oppositions.stream().allMatch(o->!o.candidates.isEmpty()),"incoming sourced causality before failed guesses");parent.release(proposed);
    }
    static void owning()throws Exception{
        Path dir=Files.createTempDirectory("constructed-startup-owner-");User user=new User();user.setDatabaseDir(dir+java.io.File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());DB db=new DB();db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);root=(Mind)root.useStorage("X");user.setCurrentMind(root);Mind child=null;
        try{need(root.compile("!seed(1);",null,false),"stored seed");child=new Mind(root);Rule out=raw(child,"!@x seed(x)->out(x);");Request q=request(Collections.singletonList(out.getOrigin()),20000,4000000);
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(db,child,1000)){
                long revision=db.getRevision();int terms=child.getTerms().size();Result r=lease.inspectDependentConstructedStartup(q);modelled(r);
                ContextDependentRuntimeModelInspection.Result live=lease.prepareDependentRuntimeModels(r.preparation);need(live.status==ContextDependentRuntimeModelInspection.Status.RUNTIME_MODELLED_PENDING,"constructed configuration independently usable for preparation");
                modelled(lease.inspectDependentConstructedStartup(q));need(lease.dependentRuntimeModels(live.frame.id)==live,"read-only construction preserves previous live capability");
                incomplete(lease.inspectDependentConstructedStartup(request(q.append.statements,1,4000000)));need(lease.dependentRuntimeModels(live.frame.id)==live,"failed construction preserves live capability");
                need(db.getRevision()==revision&&child.getTerms().size()==terms,"no revision or term assignment");
                boolean[] denied={false};Thread wrong=new Thread(()->{try{lease.inspectDependentConstructedStartup(q);}catch(Exception expected){denied[0]=true;}});wrong.start();wrong.join();need(denied[0]&&lease.dependentRuntimeModels(live.frame.id)==live,"owner guard precedes any preparation change");
                Thread.currentThread().interrupt();boolean interrupted=false;try{lease.inspectDependentConstructedStartup(q);}catch(InterruptedException expected){interrupted=Thread.currentThread().isInterrupted();}finally{Thread.interrupted();}need(interrupted,"interrupt preserves flag");
                raw(child,"!changed(2);");boolean stale=false;try{lease.inspectDependentConstructedStartup(q);}catch(Exception expected){stale=true;}need(stale,"changed actual child refused");lease.close();boolean closed=false;try{lease.inspectDependentConstructedStartup(q);}catch(Exception expected){closed=true;}need(closed,"closed lease refused");
            }
        }finally{if(child!=null)root.release(child);if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("constructed-startup-").toString());local();functions();scopes();System.out.println("CONTEXT_DEPENDENT_CONSTRUCTED_STARTUP_PASS checks="+checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
