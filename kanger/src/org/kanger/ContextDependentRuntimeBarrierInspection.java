/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentProductionInspection.Kind;
import org.kanger.ContextDependentProductionInspection.Row;
import org.kanger.ContextDependentProductionInspection.TermView;
import org.kanger.ContextDependentRoundInspection.Ticket;
import org.kanger.ContextDependentRoundInspection.Round;
import org.kanger.ContextDependentRoundInspection.Barrier;
import org.kanger.ContextDependentModelInspection.Model;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentModelInspection.Choice;
import org.kanger.ContextProductionCapture.Source;

/** Fresh owned execution bridged to native proof-history production and a frozen receiving pass. */
public final class ContextDependentRuntimeBarrierInspection {
    private ContextDependentRuntimeBarrierInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,NO_MATCH,ROUND_REFUTED,RECEIVED_PENDING,INCOMPLETE}
    public static final class Request {
        public final ContextDependentRuntimeExecution.Request execution;public final Map<String,Model> receivingOriginalModels;
        public final int bridgeWork,originalWork,finalWork;
        /** Preserved X functions use parent-original Sources; incoming/foreign keys remain exact. */
        public Request(ContextDependentRuntimeExecution.Request execution,Map<String,Model> receivingOriginalModels,int bridgeWork,int originalWork,int finalWork){if(execution==null||bridgeWork<1)throw new IllegalArgumentException("Fresh execution and bounded barrier bridge required");ContextDependentCandidateModelInspection.Request q=execution.preparation.projection.qualification;ContextDependentModelInspection.Request m=new ContextDependentModelInspection.Request(q.append.iteration,receivingOriginalModels,q.points,q.cells,originalWork,finalWork);this.execution=execution;this.receivingOriginalModels=m.models;this.bridgeWork=bridgeWork;this.originalWork=originalWork;this.finalWork=finalWork;}
    }
    public static final class Opposition {
        public final String cut;public final ContextDependentProductionInspection.Refutation proof;public final Set<Source> candidates,originalSources;
        private Opposition(String cut,ContextDependentProductionInspection.Refutation proof,Set<Source> candidates,Set<Source> originals){this.cut=cut;this.proof=proof;this.candidates=Collections.unmodifiableSet(new LinkedHashSet<>(candidates));originalSources=Collections.unmodifiableSet(new LinkedHashSet<>(originals));}
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final UUID epoch;
        public final ContextDependentRuntimeExecution.Result execution;public final ContextDependentRoundInspection.Result checked;
        public final int replayedOutputs,receivingOriginalRows,receivingFinalObligations;public final List<Opposition> oppositions;
        public final Map<String,List<Ticket>> runtimeTickets;public final List<Ticket> outputTickets;public final List<Round> history;
        private Result(Status status,String phase,String reason,ContextDependentRuntimeExecution.Result execution,Replay replay,int originals,int finals,List<Opposition> oppositions){this.status=status;this.phase=phase;this.reason=reason;this.execution=execution;checked=replay==null?null:replay.checked;replayedOutputs=replay==null?0:replay.outputs.size();receivingOriginalRows=originals;receivingFinalObligations=finals;this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));epoch=status==Status.RECEIVED_PENDING?UUID.randomUUID():null;runtimeTickets=replay==null?Collections.emptyMap():replay.runtime;outputTickets=replay==null?Collections.emptyList():replay.outputs;history=replay==null?Collections.emptyList():replay.history;}
        private static Result incomplete(String phase,String why){return new Result(Status.INCOMPLETE,phase,why,null,null,0,0,Collections.emptyList());}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Frozen runtime barrier interrupted");}
    private interface Graph {List<Integer> roots();Kind kind(int n);int variable(int n);SemanticTermSnapshot value(int n);Source function(int n);int binder(int n);List<Integer> args(int n);}
    private static final class RuntimeGraph implements Graph {
        final ContextDependentRuntimeInspection.Product p;RuntimeGraph(ContextDependentRuntimeInspection.Product p){this.p=p;}
        public List<Integer> roots(){return p.arguments;}public Kind kind(int n){return p.nodes.get(n).kind;}public int variable(int n){return p.nodes.get(n).variable;}public SemanticTermSnapshot value(int n){return p.nodes.get(n).value;}public Source function(int n){return p.nodes.get(n).function;}public int binder(int n){return p.nodes.get(n).binder;}public List<Integer> args(int n){return p.nodes.get(n).arguments;}
    }
    private static final class ExecutionGraph implements Graph {
        final ContextDependentRuntimeExecution.Output p;ExecutionGraph(ContextDependentRuntimeExecution.Output p){this.p=p;}
        public List<Integer> roots(){return p.arguments;}public Kind kind(int n){return p.nodes.get(n).kind;}public int variable(int n){return p.nodes.get(n).variable;}public SemanticTermSnapshot value(int n){return p.nodes.get(n).value;}public Source function(int n){return p.nodes.get(n).function;}public int binder(int n){return p.nodes.get(n).binder;}public List<Integer> args(int n){return p.nodes.get(n).arguments;}
    }
    private static final class Pair {final int index;final TermView term;Pair(int index,TermView term){this.index=index;this.term=term;}}
    private static void graph(Graph graph, Row row, Runnable work, long termEntries) {
        List<Integer> roots = graph.roots();
        need(roots.size() == row.arguments.size(), "Exact typed replay conclusion rank required");
        checkTermEntries(roots.size(), termEntries);
        Map<Integer,Integer> forward = new HashMap<>();
        Map<Integer,Integer> back = new HashMap<>();
        Deque<Pair> todo = new ArrayDeque<>();
        for (int i = 0; i < roots.size(); i++) {
            work.run();
            todo.push(new Pair(roots.get(i), row.arguments.get(i)));
        }
        while (!todo.isEmpty()) {
            work.run();
            Pair pair = todo.pop();
            TermView term = pair.term;
            int node = pair.index;
            need(graph.kind(node) == term.kind, "Typed replay term kind differs");
            long liveEntries = todo.size() + 2L * forward.size();
            if (term.kind == Kind.VARIABLE) {
                int variable = graph.variable(node);
                Integer a = forward.get(variable), b = back.get(term.variable);
                if (a == null && b == null) {
                    checkTermEntries(liveEntries + 2, termEntries);
                    forward.put(variable, term.variable);
                    back.put(term.variable, variable);
                } else {
                    need(a != null && b != null && a == term.variable && b == variable,
                            "Replay must preserve bijective variable correlation");
                }
            } else if (term.kind == Kind.VALUE) {
                need(graph.value(node).semanticallyEquals(term.value), "Replay typed value differs");
            } else {
                List<Integer> arguments = graph.args(node);
                need(graph.binder(node) == term.binder && graph.function(node).equals(term.symbolSource)
                        && arguments.size() == term.arguments.size(), "Replay function Source/binder/dependency rank differs");
                checkTermEntries(liveEntries + arguments.size(), termEntries);
                for (int i = 0; i < arguments.size(); i++) {
                    work.run();
                    todo.push(new Pair(arguments.get(i), term.arguments.get(i)));
                }
            }
        }
    }

    private static void checkTermEntries(long required, long limit) {
        need(required <= limit, "Replay term comparison entry limit");
    }
    private static void runtime(ContextDependentRuntimeInspection.Product p,Row row,Runnable work,long termEntries){work.run();need(row!=null&&p.producer.equals(row.rule)&&p.sources.equals(row.sources)&&p.depth==row.depth&&p.retainedLiteral==row.retainedLiteral&&p.positive==row.positive&&p.predicate.equals(row.predicate),"Exact runtime proof occurrence must match its historical ticket");graph(new RuntimeGraph(p),row,work,termEntries);}
    private static void output(ContextDependentRuntimeExecution.Output p,Row row,Runnable work,long termEntries){work.run();need(row!=null&&p.producer.equals(row.rule)&&p.sources.equals(row.sources)&&p.depth==row.depth&&p.retainedLiteral==row.retainedLiteral&&p.positive==row.positive&&p.predicate.equals(row.predicate),"Exact executed proof ancestry/depth/head must match native replay");graph(new ExecutionGraph(p),row,work,termEntries);}
    static final class Replay {
        final ContextDependentRoundInspection.Result checked;final Map<String,List<Ticket>> runtime;final List<Ticket> outputs;final List<Round> history;final int produced,received;
        Replay(ContextDependentRoundInspection.Result checked,Map<String,List<Ticket>> runtime,List<Ticket> outputs,int produced,List<Round> history){this.checked=checked;this.history=Collections.unmodifiableList(new ArrayList<>(history));Map<String,List<Ticket>> copy=new LinkedHashMap<>();for(Map.Entry<String,List<Ticket>> e:runtime.entrySet())copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));this.runtime=Collections.unmodifiableMap(copy);this.outputs=Collections.unmodifiableList(new ArrayList<>(outputs));this.produced=produced;received=produced+1;}
    }
    /** Internal bridge verifier. Public/owning entry points only call it with their own fresh preparation. */
    static Replay replay(Member child,List<Cut> cuts,ContextDependentRoundInspection.Request prefix,ContextDependentRoundInspection.Result original,ContextDependentRuntimeInspection.Frame frame,List<ContextDependentRuntimeExecution.Step> steps,List<ContextDependentRuntimeExecution.Output> outputs,int maximumWork)throws Exception{
        return replay(child,cuts,prefix,original,frame,steps,outputs,maximumWork,()->{});
    }
    static Replay replay(Member child,List<Cut> cuts,ContextDependentRoundInspection.Request prefix,ContextDependentRoundInspection.Result original,ContextDependentRuntimeInspection.Frame frame,List<ContextDependentRuntimeExecution.Step> steps,List<ContextDependentRuntimeExecution.Output> outputs,int maximumWork,Runnable chargeWork)throws Exception{
        return replay(child,cuts,prefix,original,frame,steps,outputs,maximumWork,chargeWork,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE);
    }
    static Replay replay(Member child,List<Cut> cuts,ContextDependentRoundInspection.Request prefix,ContextDependentRoundInspection.Result original,ContextDependentRuntimeInspection.Frame frame,List<ContextDependentRuntimeExecution.Step> steps,List<ContextDependentRuntimeExecution.Output> outputs,int maximumWork,Runnable chargeWork,long replaySnapshotEntries,long replayScratchEntries,long replayBridgeEntries,long replayTermEntries)throws Exception{
        Runnable localWork=ContextGroundProductionRounds.budget(maximumWork);
        Runnable work=()->{localWork.run();chargeWork.run();};need(!prefix.rounds.isEmpty()&&prefix.rounds.size()==original.barriers.size()&&steps.size()==outputs.size(),"Whole fresh execution/prefix required");int last=prefix.rounds.size()-1;Barrier barrier=original.barriers.get(last);need(frame.context.equals(child.inventory.context)&&frame.revision==child.inventory.revision&&frame.fingerprint.equals(child.inventory.fingerprint)&&frame.cuts.keySet().equals(barrier.cuts.keySet()),"Same exact child-bound runtime/cut basis required");
        if (replayBridgeEntries != Long.MAX_VALUE) {
            checkBridgeEntries(prefix, barrier, frame, steps, replayBridgeEntries, work);
        }
        List<Ticket> all=new ArrayList<>();for(int r=0;r<prefix.rounds.size();r++){need(prefix.rounds.get(r).recipes.keySet().equals(barrier.cuts.keySet()),"Complete historical cut coverage");for(String cut:barrier.cuts.keySet())for(int i=0;i<prefix.rounds.get(r).recipes.get(cut).size();i++){work.run();all.add(new Ticket(r,cut,i));}}need(new LinkedHashSet<>(all).equals(barrier.observed.keySet()),"Exact whole historical proof-ticket manifest required");for(Ticket t:all){work.run();need(barrier.common.contains(t)==barrier.observed.get(t).sources.stream().anyMatch(s->s.localX),"Only and every local-X ancestry ticket may be common");}need(barrier.observed.keySet().containsAll(barrier.common),"No invented common ticket");
        Map<String,List<Ticket>> manifest=new LinkedHashMap<>();for(String cut:barrier.cuts.keySet()){
            work.run();List<Ticket> tickets=new ArrayList<>();List<ContextDependentRuntimeInspection.Product> ps=frame.cuts.get(cut);int at=0,ordinal=0;for(Ticket t:barrier.common){work.run();need(at<ps.size(),"Missing common runtime occurrence");ContextDependentRuntimeInspection.Product p=ps.get(at++);need(p.cut.equals(cut)&&p.role==ContextDependentRuntimeInspection.Role.COMMON&&p.occurrence==ordinal++,"Exact common role/ordinal required");runtime(p,barrier.observed.get(t),work,replayTermEntries);tickets.add(t);}ordinal=0;
            for(Ticket t:all)if(t.cut.equals(cut)||t.round<last&&barrier.common.contains(t)){work.run();need(at<ps.size()&&ordinal<barrier.cuts.get(cut).products.size(),"Missing visible runtime occurrence");ContextDependentRuntimeInspection.Product p=ps.get(at++);Row row=barrier.cuts.get(cut).products.get(ordinal);need(p.cut.equals(cut)&&p.role==ContextDependentRuntimeInspection.Role.VISIBLE&&p.occurrence==ordinal++,"Exact visible role/ordinal required");runtime(p,row,work,replayTermEntries);runtime(p,barrier.observed.get(t),work,replayTermEntries);tickets.add(t);}
            need(at==ps.size()&&ordinal==barrier.cuts.get(cut).products.size(),"No missing/extra raw runtime occurrences");manifest.put(cut,tickets);
        }
        Map<String,List<ContextDependentRoundInspection.Step>> next=new LinkedHashMap<>(),empty=new LinkedHashMap<>();for(String cut:barrier.cuts.keySet()){next.put(cut,new ArrayList<>());empty.put(cut,Collections.emptyList());}List<Ticket> made=new ArrayList<>();int produced=prefix.rounds.size();need(produced+2<=prefix.policy.steps,"Room for producing and complete receiving barrier required");
        for(int i=0;i<steps.size();i++){interrupt();work.run();ContextDependentRuntimeExecution.Step step=steps.get(i);List<ContextDependentRoundInspection.Step> local=next.get(step.cut);need(local!=null&&outputs.get(i).cut.equals(step.cut),"Exact receiving execution cut required");List<ContextDependentRoundInspection.Donor> donors=new ArrayList<>();for(ContextDependentRuntimeExecution.Donor d:step.donors){work.run();if(d.original!=null)donors.add(ContextDependentRoundInspection.Donor.original(d.original));else if(d.earlier){need(d.index<i&&made.get(d.index).cut.equals(step.cut),"Only earlier outputs of the same frozen cut");donors.add(ContextDependentRoundInspection.Donor.earlier(made.get(d.index).product));}else{List<Ticket> pool=manifest.get(step.cut);need(d.index>=0&&d.index<pool.size(),"Exact frozen input occurrence index");donors.add(ContextDependentRoundInspection.Donor.carried(pool.get(d.index)));}}made.add(new Ticket(produced,step.cut,local.size()));local.add(new ContextDependentRoundInspection.Step(step.rule,step.retainedLiteral,donors));}
        List<Round> history=new ArrayList<>(prefix.rounds);history.add(new Round(next));history.add(new Round(empty));ContextDependentRoundInspection.Result checked=ContextDependentRoundInspection.inspect(child,cuts,new ContextDependentRoundInspection.Request(prefix.policy,prefix.capacity,history),chargeWork,replaySnapshotEntries,replayScratchEntries);
        for(int i=0;i<original.barriers.size();i++){work.run();Barrier before=original.barriers.get(i),after=checked.barriers.get(i);need(before.common.equals(after.common)&&before.basis.equals(after.basis)&&before.scopes.equals(after.scopes)&&before.cuts.keySet().equals(after.cuts.keySet()),"Historical basis/common/scopes changed during replay");for(String cut:before.cuts.keySet()){work.run();need(before.cuts.get(cut).auditDigest.equals(after.cuts.get(cut).auditDigest),"Independent native replay changed the frozen proof prefix");}}
        Barrier born=checked.barriers.get(produced);for(int i=0;i<outputs.size();i++)output(outputs.get(i),born.observed.get(made.get(i)),work,replayTermEntries);return new Replay(checked,manifest,made,produced,history);
    }
    private static void checkBridgeEntries(ContextDependentRoundInspection.Request prefix, Barrier barrier,
                                           ContextDependentRuntimeInspection.Frame frame,
                                           List<ContextDependentRuntimeExecution.Step> steps,
                                           long limit, Runnable work) {
        long remaining = reserveBridgeEntries(limit, prefix.rounds.size() + 2L);
        for (Round round : prefix.rounds) {
            for (List<ContextDependentRoundInspection.Step> recipes : round.recipes.values()) {
                work.run();
                // Ordered ticket list and the verification set.
                remaining = reserveBridgeEntries(remaining, 2L * recipes.size());
            }
        }
        for (String cut : barrier.cuts.keySet()) {
            work.run();
            // Cut keys in the manifest and in the two new round maps.
            remaining = reserveBridgeEntries(remaining, 3L + frame.cuts.get(cut).size());
        }
        for (ContextDependentRuntimeExecution.Step step : steps) {
            work.run();
            // Output ticket, translated recipe and donor references.
            remaining = reserveBridgeEntries(remaining, 2L + step.donors.size());
        }
    }

    private static long reserveBridgeEntries(long remaining, long entries) {
        need(entries <= remaining, "Replay bridge entry limit");
        return remaining - entries;
    }

    static List<Opposition> oppositions(Barrier barrier,ContextDependentCandidateInspection.Result candidate,Runnable work){List<Opposition> out=new ArrayList<>();for(Map.Entry<String,ContextDependentProductionInspection.Result> cut:barrier.cuts.entrySet())for(List<ContextDependentProductionInspection.Refutation> list:Arrays.asList(cut.getValue().originalUnitRefutations,cut.getValue().finalUnitRefutations))for(ContextDependentProductionInspection.Refutation p:list){work.run();need(p.sources.contains(p.constraint),"Actual whole original constraint Source required");Set<Source> incoming=new LinkedHashSet<>(),originals=new LinkedHashSet<>();for(Source s:p.sources){work.run();Source original=candidate.originals.get(s);need(original!=null&&originals.add(original),"Exact unaliased original refutation Source mapping required");if(candidate.candidates.contains(s))incoming.add(s);}out.add(new Opposition(cut.getKey(),p,incoming,originals));}return out;}
    static Map<String,Model> models(Request request,ContextDependentCandidateInspection.Result candidate,Runnable work){Map<Source,Source> forward=new LinkedHashMap<>();for(Map.Entry<Source,Source> e:candidate.originals.entrySet()){work.run();need(forward.put(e.getValue(),e.getKey())==null,"One-to-one actual receiving Source rebase");}Map<String,Model> out=new LinkedHashMap<>();for(Map.Entry<String,Model> e:request.receivingOriginalModels.entrySet()){work.run();Map<Function,Choice> fs=new LinkedHashMap<>();for(Map.Entry<Function,Choice> f:e.getValue().functions.entrySet()){work.run();Source source=forward.get(f.getKey().source);need(source!=null,"Receiving models require parent-original preserved X function keys; candidate/foreign keys stay exact");need(fs.put(new Function(source,f.getKey().binder),f.getValue())==null,"Aliased receiving function declaration");}Model m=e.getValue();out.put(e.getKey(),new Model(m.points,m.otherRepresentative,m.relations,fs));}return out;}
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.Policy p=r.execution.preparation.projection.qualification.append.iteration.policy;ContextDependentReplayInspection.current(base.actual,base.inventory,p);ContextDependentReplayInspection.current(child.actual,child.inventory,p);for(Cut cut:cuts)for(Member member:cut.members)ContextDependentReplayInspection.currentMember(member.actual,member.inventory,p);}
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request request)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual parent/child and whole cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,request);
        try{ContextDependentRuntimeExecution.Result execution=ContextDependentRuntimeExecution.inspect(base,child,cuts,request.execution);if(execution.status==ContextDependentRuntimeExecution.Status.BASE_REFUTED)return new Result(Status.BASE_REFUTED,"preparation",execution.reason,execution,null,0,0,Collections.emptyList());if(execution.status==ContextDependentRuntimeExecution.Status.CANDIDATE_REFUTED)return new Result(Status.CANDIDATE_REFUTED,"preparation",execution.reason,execution,null,0,0,Collections.emptyList());if(execution.status==ContextDependentRuntimeExecution.Status.NO_MATCH)return new Result(Status.NO_MATCH,"execution",execution.reason,null,null,0,0,Collections.emptyList());if(execution.status!=ContextDependentRuntimeExecution.Status.EXECUTED_PENDING)return Result.incomplete("execution",execution.reason);
            String phase="frozen-bridge";try{
                ContextDependentCandidateInspection.Result candidate=execution.preparation.projection.qualification.candidate;ContextDependentIterationInspection.Result prefix=candidate.proposed;ContextDependentIterationInspection.Request policy=request.execution.preparation.projection.qualification.append.iteration;Replay replay=replay(child,cuts,new ContextDependentRoundInspection.Request(policy.policy,policy.capacity,prefix.history),prefix.checked,execution.preparation.frame,request.execution.steps,execution.outputs,request.bridgeWork);Runnable work=ContextGroundProductionRounds.budget(request.bridgeWork);
                phase="producing-qualification";List<Opposition> refusals=oppositions(replay.checked.barriers.get(replay.produced),candidate,work);if(!refusals.isEmpty())return new Result(Status.ROUND_REFUTED,phase,"Actual sourced producing-round refutation; incoming causality is recorded, not inferred",null,null,0,0,refusals);
                phase="receiving-qualification";refusals=oppositions(replay.checked.barriers.get(replay.received),candidate,work);if(!refusals.isEmpty())return new Result(Status.ROUND_REFUTED,phase,"Actual sourced receiving-round refutation; incoming causality is recorded, not inferred",null,null,0,0,refusals);
                phase="receiving-model-rebase";Map<String,Model> models=models(request,candidate,work);ContextDependentCandidateModelInspection.Request q=request.execution.preparation.projection.qualification;
                phase="receiving-model-preflight";ContextDependentModelAudit audit=new ContextDependentModelAudit(child,cuts,replay.checked,new ContextDependentModelInspection.Request(policy,models,q.points,q.cells,request.originalWork,request.finalWork));phase="receiving-original";int originals=audit.original();interrupt();phase="receiving-final";int finals=audit.finalModel();interrupt();
                return new Result(Status.RECEIVED_PENDING,"frozen-receiving-audited","Executed outputs replayed and received by every exact cut; not automatic saturation/default admission",execution,replay,originals,finals,Collections.emptyList());
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){interrupt();return Result.incomplete(phase,failure.getMessage());}
        }finally{current(base,child,cuts,request);}
    }
}
