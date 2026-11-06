/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import java.nio.file.*;
import org.kanger.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.ContextDependentConstructedIterationInspection.*;
import org.kanger.ContextDependentModelInspection.Model;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.units.Rule;
import org.kanger.units.Term;
public final class ContextDependentConstructedIterationRunner {
    static int checks;
    interface Attempt{void run()throws Exception;}
    static void need(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}need(no,why);}
    static Mind memory()throws Exception{return ContextDependentConstructedStartupRunner.memory();}
    static Rule raw(Mind m,String s)throws Exception{return ContextDependentConstructedStartupRunner.raw(m,s);}
    static Member member(Mind m,int id,boolean local)throws Exception{return ContextDependentConstructedStartupRunner.member(m,id,local);}
    static ContextDependentConstructedStartupInspection.Request startup(List<String> added)throws Exception{return ContextDependentConstructedStartupRunner.request(added,20000,4000000);}
    static RoundLimits round(){return new RoundLimits(new ContextDependentRuntimeDiscovery.Limits(100000,100000,8000000),8000000,8000000,1000000,8000000,8000000,8000000);}
    static Request request(ContextDependentConstructedStartupInspection.Request startup,int rounds){return new Request(startup,startup.search,Collections.nCopies(rounds,round()),8000000,8000000,100000);}
    static ContextDependentRuntimeIterationInspection.Request kernel(ContextDependentConstructedStartupInspection.Result startup,Request q){List<ContextDependentRuntimeContinuationInspection.AutomaticPlan> plans=new ArrayList<>();for(RoundLimits r:q.rounds)plans.add(new ContextDependentRuntimeContinuationInspection.AutomaticPlan(r.discovery,Collections.emptyMap(),r.work,r.auditWork,r.nodes,r.bridgeWork,r.originalWork,r.finalWork));return ContextDependentRuntimeIterationInspection.constructedReceiving(startup.preparation,q.receivingSearch.projection,plans,q.initialWork,q.frontierWork,q.frontierCapacity,q.receivingSearch);}
    static void stable(ContextDependentRuntimeIterationInspection.Result r){need(r.status==ContextDependentRuntimeIterationInspection.Status.STABLE_PENDING,r.status+"/"+r.phase+"/"+r.reason);need(r.epoch!=null&&!r.cycles.isEmpty()&&!r.cycles.get(r.cycles.size()-1).comparison.changed,"only final unchanged receiving frontier has epoch");for(ContextDependentRuntimeIterationInspection.Cycle c:r.cycles){need(c.modelAttempts>0&&!c.constructedModels.isEmpty(),"every receiving boundary constructed, including stable duplicates");int count=0;for(List<ContextDependentRuntimeInspection.Product> pool:c.received.cuts.values())count+=pool.size();need(count==c.finalObligations,"every raw common/private occurrence survives");}}
    static void stable(Result r){need(r.status==Status.STABLE_PENDING,r.status+"/"+r.phase+"/"+r.reason);need(r.startup!=null&&r.iteration!=null&&r.epoch==r.iteration.epoch,"complete constructed startup and final iteration only");stable(r.iteration);}
    static void incomplete(Result r){need(r.status==Status.INCOMPLETE,r.status+"/"+r.phase+"/"+r.reason);need(r.startup==null&&r.iteration==null&&r.epoch==null,"no model/startup/cycle prefix after failure");}
    static void incomplete(ContextDependentRuntimeIterationInspection.Result r){need(r.status==ContextDependentRuntimeIterationInspection.Status.INCOMPLETE,r.status+"/"+r.phase+"/"+r.reason);need(r.startup==null&&r.first==null&&r.epoch==null&&r.frame==null&&r.checked==null&&r.frontier==null&&r.cycles.isEmpty()&&r.history.isEmpty(),"no partially constructed round prefix");}
    static void basic()throws Exception{
        for(boolean empty:new boolean[]{false,true}){Mind p=memory();raw(p,"!seed(1);");Member b=member(p,5101,true);Mind c=new Mind(p);Rule out=raw(c,empty?"!other(2);":"!@x seed(x)->out(x);");Member child=member(c,5101,true);Request q=request(startup(Collections.singletonList(out.getOrigin())),2);int terms=c.getTerms().size();Result r=ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),q);stable(r);need(r.iteration.completedRounds==1&&r.iteration.cycles.get(0).discovery.outputs.isEmpty()==empty,"empty/productive first round both qualify models before stability");ContextDependentConstructedIterationAuditProbe.audit(child,Collections.emptyList(),r.iteration,q.receivingSearch);need(c.getTerms().size()==terms,"no native assignments");reject(()->r.iteration.cycles.get(0).constructedModels.clear(),"constructed cut models immutable");p.release(c);}
        Mind p=memory();raw(p,"!seed(1);");raw(p,"!@x seed(x)->a(x);");raw(p,"!@x a(x)->b(x);");raw(p,"!@x b(x)->c(x);");Member b=member(p,5111,true);Mind c=new Mind(p);Rule end=raw(c,"!@x c(x)->end(x);");Member child=member(c,5111,true);Request q=request(startup(Collections.singletonList(end.getOrigin())),6);ContextDependentConstructedStartupInspection.Result start=ContextDependentConstructedStartupInspection.inspect(b,child,Collections.emptyList(),q.startup);ContextDependentRuntimeIterationInspection.Request k=kernel(start,q);ContextDependentRuntimeIterationInspection.Result r=ContextDependentRuntimeIterationProbe.minimal(b,child,Collections.emptyList(),k);stable(r);need(r.completedRounds==5,"four productive passes plus final receiving stability, no same-pass acceleration");for(int i=0;i<4;i++)need(r.cycles.get(i).comparison.changed,"every productive frontier change retained");ContextDependentConstructedIterationAuditProbe.audit(child,Collections.emptyList(),r,q.receivingSearch);ContextDependentRuntimeIterationProbe.audit(child,Collections.emptyList(),r,k);
        ContextDependentRuntimeIterationInspection.Result shortRun=ContextDependentRuntimeIterationProbe.minimal(b,child,Collections.emptyList(),kernel(start,request(q.startup,4)));incomplete(shortRun);need(shortRun.phase.equals("round-limit"),"productive schedule exhaustion is not saturation");RoundLimits bad=new RoundLimits(round().discovery,8000000,8000000,1000000,8000000,8000000,1);Request late=new Request(q.startup,q.receivingSearch,Arrays.asList(round(),round(),bad),q.initialWork,q.frontierWork,q.frontierCapacity);ContextDependentRuntimeIterationInspection.Result no=ContextDependentRuntimeIterationProbe.minimal(b,child,Collections.emptyList(),kernel(start,late));incomplete(no);need(no.phase.equals("round-final"),"late independent audit clears already constructed earlier round models");p.release(c);
    }
    static void functions()throws Exception{
        Mind scanMind = memory();
        org.kanger.units.TVariable variable = new org.kanger.units.TVariable(scanMind);
        org.kanger.primitives.ArgumentsList nested = new org.kanger.primitives.ArgumentsList();
        nested.add(new org.kanger.primitives.Argument(variable));
        for (int i = 0; i < 20000; i++) {
            org.kanger.units.Function function = new org.kanger.units.Function(scanMind);
            function.getArguments().addAll(nested);
            nested = new org.kanger.primitives.ArgumentsList();
            nested.add(new org.kanger.primitives.Argument(function));
        }
        org.kanger.primitives.ArgumentsList deep = nested;
        need(deep.getTVariables(scanMind, true).equals(Collections.singletonList(variable)),
                "deep function arguments retain their variable without using the Java stack");
        int[] remaining = {64};
        reject(() -> deep.getTVariables(scanMind, true, () -> {
            if (remaining[0]-- == 0) throw new IllegalStateException("expansion test allowance");
        }), "deep traversal still spends the expansion allowance");
        org.kanger.units.Function cyclic = new org.kanger.units.Function(scanMind);
        cyclic.getArguments().add(new org.kanger.primitives.Argument(cyclic));
        reject(() -> cyclic.getArguments().getTVariables(scanMind, true),
                "a function cycle cannot grow the explicit traversal stack indefinitely");


        for(int rank:new int[]{1,7,17,65}){Mind p=memory();StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<rank;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append('x').append(i);}Rule witness=raw(p,"!"+prefix+"$u rel("+args+",u);");Member b=member(p,5200+rank,true);Mind c=new Mind(p);Rule out=raw(c,"!"+prefix+"@u rel("+args+",u)->out("+args+",u);");Member child=member(c,5200+rank,true);Request q=request(startup(Collections.singletonList(out.getOrigin())),2);Result r=ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),q);stable(r);for(Model model:r.iteration.cycles.get(0).constructedModels.values()){Function f=model.functions.keySet().iterator().next();need(f.binder==rank&&model.functions.get(f).arity==rank&&f.source.view.equals("inventory:"+child.inventory.fingerprint),"receiving model uses exact actual child Source/rank "+rank);}need(((Term)witness.getDomain().get(rank).getValue(p)).getParent(c)==null,"chosen model values never assigned to Cvar");ContextDependentConstructedIterationAuditProbe.audit(child,Collections.emptyList(),r.iteration,q.receivingSearch);p.release(c);}
    }
    static void candidateFunction()throws Exception{
        Mind p=memory();Member b=member(p,5291,true);Mind c=new Mind(p);Rule root=raw(c,"!@x $u rel(x,u);"),out=raw(c,"!@x @u rel(x,u)->out(x,u);");Member child=member(c,5291,true);Request q=request(startup(Arrays.asList(root.getOrigin(),out.getOrigin())),2);Result r=ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),q);stable(r);Function f=r.iteration.cycles.get(0).constructedModels.values().iterator().next().functions.keySet().iterator().next();need(f.source.rule==root.getId()&&r.startup.candidate.candidates.contains(f.source),"new candidate witness keeps exact incoming Source identity");need(((Term)root.getDomain().get(1).getValue(c)).getParent(c)==null,"incoming function remains symbolic");p.release(c);
    }
    static void sourceRefusals()throws Exception{
        Mind p=memory();Member b=member(p,5391,true);Mind c=new Mind(p);Rule incoming=raw(c,"!@x left(x)&&right(x)->p(x);");Member child=member(c,5391,true);Mind am=memory(),bm=memory();raw(am,"!left(1);");raw(am,"!p(1);");raw(bm,"!right(1);");raw(bm,"!~p(1);");Member a=member(am,5392,false),z=member(bm,5393,false);List<Cut> cuts=Arrays.asList(new Cut(null,Collections.singletonList(a)),new Cut(null,Collections.singletonList(z)));ContextDependentConstructedStartupInspection.Request impossible=ContextDependentConstructedStartupRunner.request(Collections.singletonList(incoming.getOrigin()),1,1);Result no=ContextDependentConstructedIterationInspection.inspect(b,child,cuts,request(impossible,2));need(no.status==Status.CANDIDATE_REFUTED&&no.startup!=null&&no.iteration==null&&no.epoch==null,"actual cross-cut incoming refutation precedes inadequate construction bounds");need(no.startup.baseTried==0&&no.startup.proposedTried==0&&no.startup.candidate.oppositions.stream().allMatch(o->!o.candidates.isEmpty()),"fresh source proofs retain incoming causality, no guessed model blame");Collections.reverse(cuts);Result reversed=ContextDependentConstructedIterationInspection.inspect(b,child,cuts,request(impossible,2));need(reversed.status==Status.CANDIDATE_REFUTED,"reverse source order refuses same incoming rule");p.release(c);
        Mind parent=memory();raw(parent,"!p(1);");Member base=member(parent,5394,true);Mind proposed=new Mind(parent);Rule add=raw(proposed,"!fresh(1);");Mind foreign=memory();raw(foreign,"!~p(1);");Result old=ContextDependentConstructedIterationInspection.inspect(base,member(proposed,5394,true),Collections.singletonList(new Cut(null,Collections.singletonList(member(foreign,5395,false)))),request(startup(Collections.singletonList(add.getOrigin())),2));need(old.status==Status.BASE_REFUTED&&old.startup!=null&&old.startup.candidate.oppositions.stream().allMatch(o->o.candidates.isEmpty()),"pre-existing source conflict not blamed on incoming assertion");parent.release(proposed);
    }
    static void scopes()throws Exception{
        Mind p=memory();Member b=member(p,5301,true);Mind c=new Mind(p);Rule incoming=raw(c,"!@x @u edge(x,u)->p(u);");Member child=member(c,5301,true);Mind am=memory(),bm=memory();Rule witness=raw(am,"!@x $u edge(x,u);");raw(am,"!@x @u edge(x,u)->hidden(u);");raw(bm,"!~p(1);");Member a=member(am,5302,false),z=member(bm,5303,false);Request q=request(startup(Collections.singletonList(incoming.getOrigin())),2);List<Cut> cuts=Arrays.asList(new Cut(null,Collections.singletonList(a)),new Cut(null,Collections.singletonList(z)));Result r=ContextDependentConstructedIterationInspection.inspect(b,child,cuts,q);stable(r);String receiving=cuts.get(1).key;Model model=r.iteration.cycles.get(0).constructedModels.get(receiving);need(model.functions.keySet().stream().anyMatch(f->f.source.context.equals(a.inventory.context)&&f.binder==1),"imported foreign witness declared in receiving B model");need(r.iteration.frame.cuts.get(receiving).stream().anyMatch(v->v.predicate.equals("p"))&&r.iteration.frame.cuts.get(receiving).stream().noneMatch(v->v.predicate.equals("hidden")),"X-supported import enters B; foreign-only product stays private");need(!model.relations.containsKey(new org.kanger.ContextDependentModelInspection.Signature("hidden",1)),"foreign private original not a receiving premise");ContextDependentConstructedIterationAuditProbe.audit(child,cuts,r.iteration,q.receivingSearch);List<Cut> reversed=new ArrayList<>(cuts);Collections.reverse(reversed);Result reverse=ContextDependentConstructedIterationInspection.inspect(b,child,reversed,q);stable(reverse);need(reverse.iteration.cycles.get(0).finalObligations==r.iteration.cycles.get(0).finalObligations,"both attachment orders retain all raw receiving obligations");Result trust=ContextDependentConstructedIterationInspection.inspect(b,child,Collections.singletonList(new Cut("team",Arrays.asList(a,z))),q);stable(trust);need(trust.iteration.frame.cuts.size()==2,"whole TRUST is one receiving world");p.release(c);
    }
    static void bounds()throws Exception{
        Mind p=memory();raw(p,"!seed(1,1);");Member b=member(p,5401,true);Mind c=new Mind(p);Rule out=raw(c,"!@x seed(x,x)->out(x,x);");Member child=member(c,5401,true);Request q=request(startup(Collections.singletonList(out.getOrigin())),2);ContextDependentModelSearchInspection.Limits l=q.receivingSearch.limits;for(int mode=0;mode<3;mode++){
            ContextDependentModelSearchInspection.Limits limited=new ContextDependentModelSearchInspection.Limits(l.points,l.cells,l.choiceCells,mode==0?1:l.candidates,mode==1?1:l.schemaWork,l.searchWork,mode==2?1:l.auditWork,l.originalWork,l.finalWork);ContextDependentModelSearchInspection.Request search=new ContextDependentModelSearchInspection.Request(q.startup.append.iteration,q.receivingSearch.extraPoints,q.receivingSearch.otherRepresentative,limited,q.receivingSearch.projection);Result no=ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),new Request(q.startup,search,q.rounds,q.initialWork,q.frontierWork,q.frontierCapacity));incomplete(no);need(no.phase.equals("round-model-construction"),"receiving search limits cannot reuse startup interpretation");}
        need(!c.getLog().isEmpty(), "ordinary compilation still records diagnostics");
        need(ContextDependentRuntimeIterationProbe.replayProjectionIsQuiet(child, q.startup.append.iteration.policy),
                "complete native replay projection does not render compiler diagnostics");
        Mind storageMind = memory();
        Rule stored = raw(storageMind, "!stored_probe(1,1);");
        int[] storageTicks = {0};
        storageMind.getRules().expand(stored, () -> storageTicks[0]++);
        need(storageTicks[0] == 5, "ground expansion charges branch, two arguments, storage and domain binding");
        Result unrestricted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), q);
        stable(unrestricted);
        long startupAttempts = (long) unrestricted.startup.baseTried + unrestricted.startup.proposedTried;
        long exactAttempts = startupAttempts;
        for (ContextDependentRuntimeIterationInspection.Cycle cycle : unrestricted.iteration.cycles) {
            exactAttempts += cycle.modelAttempts;
        }
        Request exact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), exact));
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), exact));
        Request tight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts - 1);
        Result exhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), tight);
        incomplete(exhausted);
        need(exhausted.phase.equals("round-model-construction")
                && exhausted.reason.contains("Whole-cycle candidate-attempt limit"), "startup and receiving share one allowance");
        long replayBarriers = 0;
        for (ContextDependentRuntimeIterationInspection.Cycle cycle : unrestricted.iteration.cycles) {
            replayBarriers += cycle.history.size();
        }
        Request replayExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), replayExact));
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), replayExact));
        Request replayTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers - 1);
        Result replayExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), replayTight);
        incomplete(replayExhausted);
        need(replayExhausted.phase.equals("round-bridge")
                && replayExhausted.reason.contains("Whole-cycle replay barrier limit"), "replay charges the complete prefix before starting");
        Request workTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1);
        Result workExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), workTight);
        incomplete(workExhausted);
        need(workExhausted.phase.equals("round-bridge")
                && workExhausted.reason.contains("Whole-cycle replay work limit"), "replay work limit is independent of replay length");
        Request workRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), workRoom));
        Request auditTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1);
        Result auditExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), auditTight);
        incomplete(auditExhausted);
        need(auditExhausted.phase.equals("constructed-startup")
                && auditExhausted.reason.contains("Whole-cycle model audit work limit"), "audit allowance applies during startup search");
        Request auditRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), auditRoom));
        long initialProofEntries = ContextDependentRuntimeIterationProbe.proofEntries(
                unrestricted.iteration.startup.projection.qualification.candidate.proposed.checked);
        long retainedProofEntries = initialProofEntries;
        for (ContextDependentRuntimeIterationInspection.Cycle cycle : unrestricted.iteration.cycles) {
            retainedProofEntries += ContextDependentRuntimeIterationProbe.proofEntries(cycle.checked);
        }
        Request historyTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries - 1);
        Result historyExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), historyTight);
        incomplete(historyExhausted);
        need(historyExhausted.phase.equals("round-proof-history")
                && historyExhausted.reason.contains("Retained proof history limit"), "the retained snapshots share a history limit");
        Request historyExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), historyExact));
        long largestReplay = unrestricted.iteration.cycles.stream()
                .mapToLong(cycle -> ContextDependentRuntimeIterationProbe.proofEntries(cycle.checked))
                .max().getAsLong();
        Request snapshotTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay - 1);
        Result snapshotExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), snapshotTight);
        incomplete(snapshotExhausted);
        need(snapshotExhausted.phase.equals("round-bridge")
                && snapshotExhausted.reason.contains("Replay snapshot entry limit"), "replay enforces the snapshot limit before retention");
        Request snapshotExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), snapshotExact));
        Request scratchTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1);
        Result scratchExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), scratchTight);
        incomplete(scratchExhausted);
        need(scratchExhausted.phase.equals("round-bridge")
                && scratchExhausted.reason.contains("Replay scratch entry limit"), "mutable replay entries have an independent limit");
        Request scratchRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), scratchRoom));
        Request bridgeTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1);
        Result bridgeExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), bridgeTight);
        incomplete(bridgeExhausted);
        need(bridgeExhausted.phase.equals("round-bridge")
                && bridgeExhausted.reason.contains("Replay bridge entry limit"), "bridge entries have an independent limit");
        Request bridgeRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), bridgeRoom));
        Request termTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 1);
        Result termExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), termTight);
        incomplete(termExhausted);
        need(termExhausted.phase.equals("round-bridge")
                && termExhausted.reason.contains("Replay term comparison entry limit"), "two pending roots exceed one entry");
        Request termExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), termExact));
        long projectionPairEntries = 2L * (1 + child.inventory.rows.size());
        Request projectionTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries - 1);
        Result projectionExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), projectionTight);
        incomplete(projectionExhausted);
        need(projectionExhausted.phase.equals("round-bridge")
                && projectionExhausted.reason.contains("Replay projection entry limit"), "both independent projections consume entries");
        Request projectionExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), projectionExact));
        Request compilerTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1);
        Result compilerExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), compilerTight);
        incomplete(compilerExhausted);
        need(compilerExhausted.phase.equals("round-bridge")
                && compilerExhausted.reason.contains("Replay compiler term limit"), "the projection pair shares its term allowance");
        Request compilerRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), compilerRoom));
        Request factoryTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1);
        Result factoryExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), factoryTight);
        incomplete(factoryExhausted);
        need(factoryExhausted.phase.equals("round-bridge")
                && factoryExhausted.reason.contains("Replay compiler factory entry limit"),
                "non-dictionary registrations have an independent allowance");
        Request factoryRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), factoryRoom));
        Request parserTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000, 1);
        Result parserExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), parserTight);
        incomplete(parserExhausted);
        need(parserExhausted.phase.equals("round-bridge")
                && parserExhausted.reason.contains("Replay parser node limit"), "parser nodes have an independent allowance");
        Request parserRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), parserRoom));
        Request tokenTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000, 1000, 3);
        Result tokenExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), tokenTight);
        incomplete(tokenExhausted);
        need(tokenExhausted.phase.equals("round-bridge")
                && tokenExhausted.reason.contains("Replay parser token limit"), "two rows in two projections need four tokens");
        Request tokenExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000, 1000, 4);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), tokenExact));
        Request depthTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000, 1000, 4, 1);
        Result depthExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), depthTight);
        incomplete(depthExhausted);
        need(depthExhausted.phase.equals("round-bridge")
                && depthExhausted.reason.contains("Replay parser depth limit"), "parser depth has an independent limit");
        Request depthRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries, 1000, 1000, 1000, 4, 64);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), depthRoom));
        Request textTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1);
        Result textExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), textTight);
        incomplete(textExhausted);
        need(textExhausted.phase.equals("round-bridge")
                && textExhausted.reason.contains("Replay lexeme character limit"), "lexeme text has an independent limit");
        Request textRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), textRoom));
        long sourcePairChars = 2L * child.inventory.rows.stream().mapToLong(row -> row.origin.length()).sum();
        Request sourceTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars - 1);
        Result sourceExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), sourceTight);
        incomplete(sourceExhausted);
        need(sourceExhausted.phase.equals("round-bridge")
                && sourceExhausted.reason.contains("Replay source character limit"), "both projections count source characters");
        Request sourceExact = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), sourceExact));
        Request compilerTextTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars, 1);
        Result compilerTextExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), compilerTextTight);
        incomplete(compilerTextExhausted);
        need(compilerTextExhausted.phase.equals("round-bridge")
                && compilerTextExhausted.reason.contains("Replay compiler character limit"), "generated compiler text has an independent limit");
        Request compilerTextRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), compilerTextRoom));
        Request compilerDepthTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars, 1000, 1);
        Result compilerDepthExhausted = ContextDependentConstructedIterationInspection.inspect(
                b, child, Collections.emptyList(), compilerDepthTight);
        incomplete(compilerDepthExhausted);
        need(compilerDepthExhausted.phase.equals("round-bridge")
                && compilerDepthExhausted.reason.contains("Replay compiler depth limit"),
                "compiler recursion has an independent limit");
        Request compilerDepthRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars, 1000, 64);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), compilerDepthRoom));
        Request expansionTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars, 1000, 64, 1);
        Result expansionExhausted = ContextDependentConstructedIterationInspection.inspect(
                b, child, Collections.emptyList(), expansionTight);
        incomplete(expansionExhausted);
        need(expansionExhausted.phase.equals("round-bridge")
                && expansionExhausted.reason.contains("Replay expansion work limit"),
                "rule expansion has an independent allowance");
        Request expansionRoom = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, exactAttempts, replayBarriers, 1000000, 1000000,
                retainedProofEntries, largestReplay, 1000, 1000, 2, projectionPairEntries,
                1000, 1000, 1000, 4, 64, 1000, sourcePairChars, 1000, 64, 1000);
        stable(ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), expansionRoom));
        Request startupTight = new Request(q.startup, q.receivingSearch, q.rounds, q.initialWork,
                q.frontierWork, q.frontierCapacity, startupAttempts - 1);
        Result startupExhausted = ContextDependentConstructedIterationInspection.inspect(b, child, Collections.emptyList(), startupTight);
        incomplete(startupExhausted);
        need(startupExhausted.phase.equals("constructed-startup")
                && startupExhausted.reason.contains("Whole-cycle candidate-attempt limit"), "base and proposed startup share one allowance");
        RoundLimits bad=new RoundLimits(round().discovery,8000000,8000000,1000000,8000000,8000000,1);Result late=ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),new Request(q.startup,q.receivingSearch,Collections.singletonList(bad),q.initialWork,q.frontierWork,q.frontierCapacity));incomplete(late);need(late.phase.equals("round-final"),"independent post-search final audit still mandatory");
        ContextDependentConstructedStartupInspection.Result start=ContextDependentConstructedStartupInspection.inspect(b,child,Collections.emptyList(),q.startup);ContextDependentRuntimeIterationInspection.Request kernel=kernel(start,q);ContextDependentRuntimeContinuationInspection.AutomaticPlan supplied=new ContextDependentRuntimeContinuationInspection.AutomaticPlan(round().discovery,start.preparation.projection.qualification.proposedOriginalModels,8000000,8000000,1000000,8000000,8000000,8000000);reject(()->ContextDependentRuntimeIterationInspection.constructedReceiving(start.preparation,q.receivingSearch.projection,Collections.singletonList(supplied),q.initialWork,q.frontierWork,q.frontierCapacity,q.receivingSearch),"constructed receiving cannot silently ignore supplied models");
        Thread.currentThread().interrupt();boolean interrupted=false;try{ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),q);}catch(InterruptedException expected){interrupted=Thread.currentThread().isInterrupted();}finally{Thread.interrupted();}need(interrupted,"interrupt propagated");raw(c,"!changed(2);");reject(()->ContextDependentConstructedIterationInspection.inspect(b,child,Collections.emptyList(),q),"stale native basis refused");p.release(c);
    }
    static void owning()throws Exception{
        Path dir=Files.createTempDirectory("constructed-iteration-owner-");User user=new User();user.setDatabaseDir(dir+java.io.File.separator);user.setUserDir(Files.createDirectories(dir.resolve("operator")).toString());DB db=new DB();db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);root=(Mind)root.useStorage("X");user.setCurrentMind(root);Mind child=null;
        try{need(root.compile("!seed(1);",null,false),"stored seed");child=new Mind(root);Rule out=raw(child,"!@x seed(x)->out(x);");Request q=request(startup(Collections.singletonList(out.getOrigin())),2);try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(db,child,1000)){
            long revision=db.getRevision();int terms=child.getTerms().size();Result r=lease.prepareDependentConstructedIteration(q);stable(r);need(lease.dependentRuntimeIteration(r.epoch)==r.iteration,"only final constructed iteration epoch retained");reject(()->lease.dependentRuntimeModels(r.startup.startup.frame.id),"historical constructed startup is not live runtime capability");boolean[] denied={false};Thread wrong=new Thread(()->{try{lease.prepareDependentConstructedIteration(q);}catch(Exception expected){denied[0]=true;}});wrong.start();wrong.join();need(denied[0]&&lease.dependentRuntimeIteration(r.epoch)==r.iteration,"wrong owner cannot clear live epoch");
            ContextDependentModelSearchInspection.Limits l=q.receivingSearch.limits;ContextDependentModelSearchInspection.Request cap=new ContextDependentModelSearchInspection.Request(q.startup.append.iteration,q.receivingSearch.extraPoints,q.receivingSearch.otherRepresentative,new ContextDependentModelSearchInspection.Limits(l.points,l.cells,l.choiceCells,1,l.schemaWork,l.searchWork,l.auditWork,l.originalWork,l.finalWork),q.receivingSearch.projection);incomplete(lease.prepareDependentConstructedIteration(new Request(q.startup,cap,q.rounds,q.initialWork,q.frontierWork,q.frontierCapacity)));reject(()->lease.dependentRuntimeIteration(r.epoch),"late receiving failure clears prior live iteration");Result again=lease.prepareDependentConstructedIteration(q);stable(again);need(again.epoch!=r.epoch&&db.getRevision()==revision&&child.getTerms().size()==terms,"fresh epoch without native writes");raw(child,"!changed(2);");reject(()->lease.dependentRuntimeIteration(again.epoch),"changed native child invalidates epoch");lease.close();reject(()->lease.prepareDependentConstructedIteration(q),"closed lease refuses");
        }}finally{if(child!=null)root.release(child);if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("constructed-iteration-").toString());basic();System.out.println("CONTEXT_DEPENDENT_CONSTRUCTED_ITERATION_PASS checks="+checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
