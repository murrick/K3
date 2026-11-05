/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.ContextDependentProductionInspection.CutPart;

/** Explicit synchronous proof recipes. Historical diagnostics only; no coverage or admission authority. */
public final class ContextDependentRoundInspection {
    private ContextDependentRoundInspection(){}
    public static final class Ticket {
        public final int round,product;public final String cut;
        public Ticket(int round,String cut,int product){if(round<0||cut==null||product<0)throw new IllegalArgumentException("Exact round product required");this.round=round;this.cut=cut;this.product=product;}
        @Override public boolean equals(Object o){return o instanceof Ticket&&round==((Ticket)o).round&&product==((Ticket)o).product&&cut.equals(((Ticket)o).cut);}
        @Override public int hashCode(){return Objects.hash(round,cut,product);}
    }
    public static final class Donor {
        public final Ref original;public final Ticket carried;public final int earlier;
        private Donor(Ref original,Ticket carried,int earlier){this.original=original;this.carried=carried;this.earlier=earlier;}
        public static Donor original(Ref ref){if(ref==null)throw new IllegalArgumentException("Physical original required");return new Donor(ref,null,-1);}
        public static Donor carried(Ticket ticket){if(ticket==null)throw new IllegalArgumentException("Previous barrier ticket required");return new Donor(null,ticket,-1);}
        public static Donor earlier(int index){if(index<0)throw new IllegalArgumentException("Earlier local step required");return new Donor(null,null,index);}
    }
    public static final class Step {
        public final Ref rule;public final int retainedLiteral;public final List<Donor> donors;
        public Step(Ref rule,int retainedLiteral,List<Donor> donors){if(rule==null||retainedLiteral< -1||donors==null||donors.contains(null))throw new IllegalArgumentException("Whole producer and exact donors required");this.rule=rule;this.retainedLiteral=retainedLiteral;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));}
    }
    public static final class Round {
        public final Map<String,List<Step>> recipes;
        public Round(Map<String,List<Step>> recipes){if(recipes==null)throw new IllegalArgumentException("Whole barrier required");Map<String,List<Step>> copy=new LinkedHashMap<>();for(Map.Entry<String,List<Step>> e:recipes.entrySet()){if(e.getKey()==null||e.getValue()==null||e.getValue().contains(null))throw new IllegalArgumentException("Exact barrier recipe required");copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));}this.recipes=Collections.unmodifiableMap(copy);}
    }
    public static final class Request {
        public final Policy policy;public final int capacity;public final List<Round> rounds;
        public Request(Policy policy,int capacity,List<Round> rounds){if(policy==null||capacity<1||capacity>100000||rounds==null||rounds.contains(null)||rounds.size()>policy.steps)throw new IllegalArgumentException("Bounded complete round recipes required");this.policy=policy;this.capacity=capacity;this.rounds=Collections.unmodifiableList(new ArrayList<>(rounds));}
    }
    public static final class Barrier {
        public final Map<String,ContextDependentProductionInspection.Result> cuts;
        public final Set<Ticket> common;
        final Map<Ticket,ContextDependentProductionInspection.Row> observed;
        final Map<UUID,String> basis;final Map<String,Set<UUID>> scopes;
        private Barrier(Map<String,ContextDependentProductionInspection.Result> cuts,Set<Ticket> common,Map<Ticket,ContextDependentProductionInspection.Row> observed,List<Member> members,Map<String,Set<UUID>> scopes){
            this.cuts=Collections.unmodifiableMap(new LinkedHashMap<>(cuts));this.common=Collections.unmodifiableSet(new LinkedHashSet<>(common));this.observed=Collections.unmodifiableMap(new LinkedHashMap<>(observed));Map<UUID,String> basis=new LinkedHashMap<>();for(Member member:members){ContextProductionInventory i=member.inventory;basis.put(i.context,i.revision+":"+i.localX+":"+i.fingerprint);}this.basis=Collections.unmodifiableMap(basis);Map<String,Set<UUID>> copy=new LinkedHashMap<>();for(Map.Entry<String,Set<UUID>> e:scopes.entrySet())copy.put(e.getKey(),Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));this.scopes=Collections.unmodifiableMap(copy);
        }
    }
    public static final class Result {
        public final List<Barrier> barriers;
        private Result(List<Barrier> barriers){this.barriers=Collections.unmodifiableList(new ArrayList<>(barriers));}
    }
    public static final class Discovery {
        public final Round next;public final Result checked;public final int matchedRecipes;public final ContextDependentFrontierInspection.Snapshot frontier;
        private Discovery(Round next,Result checked,int work,int capacity){this.frontier=ContextDependentFrontierInspection.inspect(checked.barriers.get(checked.barriers.size()-1),work,capacity);this.next=next;this.checked=checked;int count=0;for(List<Step> steps:next.recipes.values())count+=steps.size();matchedRecipes=count;}
    }
    private static final class Outcome {
        final Result result;final Discovery discovery;
        Outcome(Result result,Discovery discovery){this.result=result;this.discovery=discovery;}
    }
    /** Complete supported residual-unit applications over the previous finite barrier, not saturation. */
    public static Discovery discoverNext(Member x,List<Cut> supplied,Request previous)throws Exception{return run(x,supplied,previous,true,()->{},Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE).discovery;}
    private static List<CutPart> project(List<Member> members,Policy policy)throws Exception {
        return project(members,policy,()->{},null);
    }
    private static List<CutPart> project(List<Member> members,Policy policy,Runnable chargeWork,Runnable chargeNewTerm)throws Exception {
        List<CutPart> out = new ArrayList<>();
        for (Member member : members) {
            out.add(new CutPart(ContextDependentReplayInspection.projectMember(
                    member.actual,member.inventory,policy,chargeWork,chargeNewTerm),member.inventory));
        }
        return out;
    }
    private static void current(List<Member> members,Policy policy)throws Exception{for(Member m:members)if(m.inventory.localX)ContextDependentReplayInspection.current(m.actual,m.inventory,policy);else ContextDependentReplayInspection.currentMember(m.actual,m.inventory,policy);}
    public static Result inspect(Member x,List<Cut> supplied,Request request)throws Exception{return inspect(x,supplied,request,()->{});}
    static Result inspect(Member x,List<Cut> supplied,Request request,Runnable chargeWork)throws Exception {
        return inspect(x,supplied,request,chargeWork,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE);
    }
    static Result inspect(Member x,List<Cut> supplied,Request request,Runnable chargeWork,long snapshotEntries,long scratchEntries,long projectionEntries,long compilerTerms)throws Exception {
        return run(x,supplied,request,false,chargeWork,snapshotEntries,scratchEntries,projectionEntries,compilerTerms).result;
    }
    private static long reserveScratch(long remaining, long entries) {
        if (entries > remaining) throw new IllegalStateException("Replay scratch entry limit");
        return remaining - entries;
    }
    private static Outcome run(Member x,List<Cut> supplied,Request request,boolean discover,Runnable chargeWork,long snapshotEntries,long scratchEntries,long projectionEntries,long compilerTerms)throws Exception {
        if(x==null||!x.inventory.localX||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Exact whole family required");
        List<Cut> cuts=new ArrayList<>(supplied);cuts.sort(Comparator.comparing(c->c.key));Map<String,Set<UUID>> scopes=new LinkedHashMap<>();Set<UUID> local=Collections.singleton(x.inventory.context);scopes.put(ContextDependentCutInspection.local(x.inventory.context),local);List<Member> members=new ArrayList<>();members.add(x);Set<UUID> contexts=new HashSet<>(local);long rows=x.inventory.rows.size();
        for(Cut cut:cuts){Set<UUID> scope=new LinkedHashSet<>(local);if(scopes.containsKey(cut.key))throw new IllegalArgumentException("Repeated/split logical cut");for(Member m:cut.members){if(!contexts.add(m.inventory.context))throw new IllegalArgumentException("Repeated physical member");scope.add(m.inventory.context);members.add(m);rows+=m.inventory.rows.size();}scopes.put(cut.key,scope);}
        if(rows>request.policy.rows||scopes.size()>request.policy.steps||contexts.size()>request.policy.steps+1)throw new IllegalArgumentException("Bounded whole family required");
        Map<Ref,Long> keys=new LinkedHashMap<>();Map<String,Set<Long>> active=new LinkedHashMap<>();long next=0;for(Member m:members)for(ContextProductionInventory.Row row:m.inventory.rows)keys.put(new Ref(m.inventory.context,row.nativeRule),next++);
        for(Map.Entry<String,Set<UUID>> scope:scopes.entrySet()){Set<Long> ids=new LinkedHashSet<>();for(Map.Entry<Ref,Long> row:keys.entrySet())if(scope.getValue().contains(row.getKey().context))ids.add(row.getValue());active.put(scope.getKey(),ids);}
        current(members,request.policy);
        try {
            List<ContextDependentProductionInspection.Step> history=new ArrayList<>();Map<Ticket,Integer> indices=new LinkedHashMap<>();Set<Ticket> common=new LinkedHashSet<>();List<Barrier> barriers=new ArrayList<>();Map<Ticket,ContextDependentProductionInspection.Row> observed=new LinkedHashMap<>();Runnable localWork=ContextGroundProductionRounds.budget(request.policy.work);
            Runnable work=()->{localWork.run();chargeWork.run();};
            for(int r=0;r<request.rounds.size();r++) {
                Round round=request.rounds.get(r);if(!round.recipes.keySet().equals(scopes.keySet()))throw new IllegalArgumentException("Every barrier needs every exact cut");
                long remainingScratch = scratchEntries;
                Map<String,Set<Integer>> visible=new LinkedHashMap<>();Map<String,List<Ticket>> created=new LinkedHashMap<>();
                for(String cut:scopes.keySet()) {
                    Set<Integer> pool = new LinkedHashSet<>();
                    for (Map.Entry<Ticket,Integer> old : indices.entrySet()) {
                        Ticket ticket = old.getKey();
                        if (ticket.round < r && (common.contains(ticket) || ticket.cut.equals(cut))) {
                            remainingScratch = reserveScratch(remainingScratch, 1);
                            pool.add(old.getValue());
                        }
                    }
                    visible.put(cut,pool);
                    List<Ticket> made = new ArrayList<>();
                    created.put(cut,made);
                    for(Step step:round.recipes.get(cut)) {
                        // The new proof adds one pool index and one reference per donor.
                        remainingScratch = reserveScratch(remainingScratch, 1L + step.donors.size());
                        work.run();Long producer=keys.get(step.rule);if(producer==null||!active.get(cut).contains(producer))throw new IllegalArgumentException("Producer is outside exact cut");List<ContextDependentProductionInspection.Donor> donors=new ArrayList<>();
                        for(Donor donor:step.donors){work.run();if(donor.original!=null){Long id=keys.get(donor.original);if(id==null||!active.get(cut).contains(id))throw new IllegalArgumentException("Sibling original is not common knowledge");donors.add(ContextDependentProductionInspection.Donor.original(id));}else{Integer id;if(donor.carried!=null){id=indices.get(donor.carried);if(donor.carried.round>=r||id==null||!pool.contains(id))throw new IllegalArgumentException("Donor must be visible at previous barrier");}else{if(donor.earlier>=made.size())throw new IllegalArgumentException("Only earlier steps of this cut");id=indices.get(made.get(donor.earlier));}donors.add(ContextDependentProductionInspection.Donor.product(id));}}
                        if(history.size()==request.policy.steps)throw new IllegalStateException("Whole transport proof history limit");int index=history.size();history.add(new ContextDependentProductionInspection.Step(producer,step.retainedLiteral,donors));Ticket ticket=new Ticket(r,cut,made.size());made.add(ticket);indices.put(ticket,index);pool.add(index);
                    }
                }
                // All cuts receive the same PREVIOUS common barrier. Same-round sibling steps remain invisible.
                Map<String,ContextDependentProductionInspection.Result> results=new LinkedHashMap<>();Set<Ticket> exports=new LinkedHashSet<>(common);
                for(String cut:scopes.keySet()) {
                    // Both independent projections are alive during the transport audit.
                    if (2L * (rows + members.size()) > projectionEntries) {
                        throw new IllegalStateException("Replay projection entry limit");
                    }
                    long[] remainingTerms = {compilerTerms};
                    Runnable chargeNewTerm = compilerTerms == Long.MAX_VALUE ? null : () -> {
                        if (remainingTerms[0] == 0) throw new IllegalStateException("Replay compiler term limit");
                        remainingTerms[0]--;
                    };
                    work.run();ContextDependentProductionInspection.Request recipe=new ContextDependentProductionInspection.Request(request.policy,request.capacity,history,true);
                    ContextDependentProductionInspection.Result checked=ContextDependentProductionInspection.inspectTransport(project(members,request.policy,chargeWork,chargeNewTerm),project(members,request.policy,chargeWork,chargeNewTerm),"round:"+r+":"+cut,recipe,active.get(cut),visible.get(cut),chargeWork);results.put(cut,checked);
                    List<Integer> order=new ArrayList<>(visible.get(cut));Collections.sort(order);
                    for(Ticket ticket:created.get(cut)){int at=Collections.binarySearch(order,indices.get(ticket));ContextDependentProductionInspection.Row row=checked.products.get(at);observed.put(ticket,row);if(row.sources.stream().anyMatch(source->source.localX))exports.add(ticket);}
                }
                long entries = 1L + observed.size();
                if (entries > snapshotEntries) throw new IllegalStateException("Replay snapshot entry limit");
                snapshotEntries -= entries;
                common=exports;barriers.add(new Barrier(results,common,observed,members,scopes));current(members,request.policy);
            }
            if(discover){
                if(request.rounds.size()==request.policy.steps)throw new IllegalStateException("No room for complete next barrier");Map<Long,Ref> originals=new LinkedHashMap<>();for(Map.Entry<Ref,Long> e:keys.entrySet())originals.put(e.getValue(),e.getKey());Map<Integer,Ticket> tickets=new LinkedHashMap<>();for(Map.Entry<Ticket,Integer> e:indices.entrySet())tickets.put(e.getValue(),e.getKey());Map<String,List<Step>> recipes=new LinkedHashMap<>();long total=history.size();
                for(String cut:scopes.keySet()){
                    work.run();Set<Integer> pool=new LinkedHashSet<>();for(Map.Entry<Ticket,Integer> old:indices.entrySet())if(common.contains(old.getKey())||old.getKey().cut.equals(cut))pool.add(old.getValue());
                    ContextDependentProductionInspection.Request input=new ContextDependentProductionInspection.Request(request.policy,request.capacity,history,true);
                    List<ContextDependentProductionInspection.Step> found=ContextDependentProductionInspection.discoverTransport(project(members,request.policy),project(members,request.policy),"discovery:"+request.rounds.size()+":"+cut,input,active.get(cut),pool);total+=found.size();if(total>request.policy.steps)throw new IllegalStateException("Whole next barrier recipe limit");List<Step> steps=new ArrayList<>();
                    for(ContextDependentProductionInspection.Step step:found){List<Donor> donors=new ArrayList<>();for(ContextDependentProductionInspection.Donor d:step.donors)donors.add(d.original?Donor.original(originals.get(d.reference)):Donor.carried(tickets.get((int)d.reference)));steps.add(new Step(originals.get(step.rule),step.retainedLiteral,donors));}recipes.put(cut,steps);
                }
                Round nextRound=new Round(recipes);List<Round> complete=new ArrayList<>(request.rounds);complete.add(nextRound);Result checked=inspect(x,supplied,new Request(request.policy,request.capacity,complete));return new Outcome(null,new Discovery(nextRound,checked,request.policy.work,request.capacity));
            }
            return new Outcome(new Result(barriers),null);
        }finally{current(members,request.policy);}
    }
}
