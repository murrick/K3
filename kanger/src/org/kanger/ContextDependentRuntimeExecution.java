/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextDependentProductionInspection.Kind;
import org.kanger.units.Rule;

/** Explicit native ordinary residual steps consuming fresh model-audited runtime graphs. */
public final class ContextDependentRuntimeExecution {
    private ContextDependentRuntimeExecution(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,EXECUTED_PENDING,NO_MATCH,INCOMPLETE}
    public static final class Donor {
        public final boolean earlier;public final int index;public final Ref original;
        private Donor(boolean earlier,int index,Ref original){if(original==null?index<0:earlier||index!=-1)throw new IllegalArgumentException("Exact visible occurrence or physical original required");this.earlier=earlier;this.index=index;this.original=original;}
        public static Donor runtime(int index){return new Donor(false,index,null);}
        public static Donor earlier(int index){return new Donor(true,index,null);}
        public static Donor original(Ref original){if(original==null)throw new IllegalArgumentException("Physical original required");return new Donor(false,-1,original);}
    }
    public static final class Step {
        public final String cut;public final Ref rule;public final int retainedLiteral;public final List<Donor> donors;
        public Step(String cut,Ref rule,int retainedLiteral,List<Donor> donors){if(cut==null||rule==null||retainedLiteral<0||donors==null||donors.contains(null))throw new IllegalArgumentException("Exact native retained literal and complete ordered donor tuple required");this.cut=cut;this.rule=rule;this.retainedLiteral=retainedLiteral;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));}
    }
    public static final class Request {
        public final ContextDependentRuntimeModelInspection.Request preparation;public final List<Step> steps;public final int work,auditWork,nodes;
        public Request(ContextDependentRuntimeModelInspection.Request preparation,List<Step> steps,int work,int auditWork,int nodes){if(preparation==null||steps==null||steps.isEmpty()||steps.contains(null)||steps.size()>preparation.projection.qualification.append.iteration.policy.steps||work<1||auditWork<1||nodes<1)throw new IllegalArgumentException("Bounded explicit whole execution plan required");this.preparation=preparation;this.steps=Collections.unmodifiableList(new ArrayList<>(steps));this.work=work;this.auditWork=auditWork;this.nodes=nodes;}
    }
    public static final class Node {
        public final Kind kind;public final int variable,binder;public final SemanticTermSnapshot value;public final Source function;public final List<Integer> arguments;
        private Node(Kind kind,int variable,SemanticTermSnapshot value,Source function,int binder,List<Integer> arguments){this.kind=kind;this.variable=variable;this.value=value;this.function=function;this.binder=binder;this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));}
    }
    /** One graph includes the conclusion, instantiated whole clause/donors and the substitution certificate. */
    public static final class Output {
        public final String cut,predicate;public final boolean positive;public final Source producer;public final Set<Source> sources;public final int depth,retainedLiteral;
        public final List<Node> nodes;public final List<Integer> arguments;public final List<List<Integer>> literals,donors;public final Map<Integer,Integer> ruleBindings;public final List<Map<Integer,Integer>> donorBindings;
        private Output(Step step,Entry rule,Set<Source> sources,int depth,Writer writer,List<List<Integer>> literals,List<List<Integer>> donors,Map<Integer,Integer> ruleBindings,List<Map<Integer,Integer>> donorBindings){cut=step.cut;producer=rule.source;this.sources=Collections.unmodifiableSet(new LinkedHashSet<>(sources));this.depth=depth;retainedLiteral=step.retainedLiteral;ContextProductionCapture.Atom head=rule.clause.literals.get(retainedLiteral);predicate=head.predicate;positive=head.positive;nodes=Collections.unmodifiableList(new ArrayList<>(writer.nodes));this.literals=freeze(literals);this.donors=freeze(donors);arguments=this.literals.get(retainedLiteral);this.ruleBindings=Collections.unmodifiableMap(new LinkedHashMap<>(ruleBindings));List<Map<Integer,Integer>> bs=new ArrayList<>();for(Map<Integer,Integer> b:donorBindings)bs.add(Collections.unmodifiableMap(new LinkedHashMap<>(b)));this.donorBindings=Collections.unmodifiableList(bs);}
        private static List<List<Integer>> freeze(List<List<Integer>> list){List<List<Integer>> out=new ArrayList<>();for(List<Integer> row:list)out.add(Collections.unmodifiableList(new ArrayList<>(row)));return Collections.unmodifiableList(out);}
    }
    public static final class Result {
        public final Status status;public final String reason;public final List<Output> outputs;public final ContextDependentRuntimeModelInspection.Result preparation;
        private Result(Status status,String reason,List<Output> outputs,ContextDependentRuntimeModelInspection.Result preparation){this.status=status;this.reason=reason;this.outputs=Collections.unmodifiableList(new ArrayList<>(outputs));this.preparation=preparation;}
    }
    static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Runtime execution interrupted");}
    static final class Entry {final Source source;final Clause clause;Entry(Source source,Clause clause){this.source=source;this.clause=clause;}}
    static Entry entry(Member child,List<Cut> cuts,Step step,Runnable work)throws Exception{
        work.run();List<Member> allowed=new ArrayList<>();allowed.add(child);boolean known=step.cut.equals(ContextDependentCutInspection.local(child.inventory.context));for(Cut cut:cuts)if(cut.key.equals(step.cut)){known=true;allowed.addAll(cut.members);}need(known,"Exact receiving runtime cut required");
        for(Member member:allowed)if(member.inventory.context.equals(step.rule.context))for(ContextProductionInventory.Row row:member.inventory.primary){work.run();if(row.nativeRule==step.rule.rule){Rule rule=(Rule)member.actual.getRules().get(row.nativeRule);need(rule!=null&&row.origin.equals(rule.getOrigin()),"Exact current native rule required");Clause clause=NativeDependentProductionRows.ordinary(rule,member.actual,work);need(clause.literals.size()>=2&&step.retainedLiteral<clause.literals.size()&&step.donors.size()==clause.literals.size()-1,"Whole ordinary retained clause and every other literal required");return new Entry(new Source(member.inventory.context,member.inventory.revision,row.nativeRule,member.inventory.localX,"inventory:"+member.inventory.fingerprint),clause);}}
        throw new IllegalStateException("Native rule is not an original of the receiving cut");
    }
    /** Actual primary unit descriptor; no projected input graph or chosen witness value. */
    static final class NativeUnit {
        final Source source;final NativePrenexUnitScope prefix;final ContextProductionCapture.Atom atom;
        private NativeUnit(Source source,NativePrenexUnitScope prefix,ContextProductionCapture.Atom atom){this.source=source;this.prefix=prefix;this.atom=atom;}
        String predicate(){return prefix==null?atom.predicate:prefix.predicate;}
        boolean positive(){return prefix!=null||atom.positive;}
        int arity(){return prefix==null?atom.arguments.size():prefix.slots.size();}
    }
    static NativeUnit unit(Member child,List<Cut> cuts,String cut,Ref ref,Runnable work)throws Exception{
        work.run();List<Member> allowed=new ArrayList<>();allowed.add(child);boolean known=cut.equals(ContextDependentCutInspection.local(child.inventory.context));for(Cut c:cuts)if(c.key.equals(cut)){known=true;allowed.addAll(c.members);}need(known,"Exact original-unit receiving cut required");
        for(Member member:allowed)if(member.inventory.context.equals(ref.context))for(ContextProductionInventory.Row row:member.inventory.primary){work.run();if(row.nativeRule==ref.rule){Rule rule=(Rule)member.actual.getRules().get(row.nativeRule);need(rule!=null&&rule.getId()==row.nativeRule&&row.origin.equals(rule.getOrigin()),"Exact actual primary native unit required");Source source=new Source(member.inventory.context,member.inventory.revision,row.nativeRule,member.inventory.localX,"inventory:"+member.inventory.fingerprint);
            if(NativeDependentProductionRows.unit(rule))return new NativeUnit(source,NativePrenexUnitScope.observeFactoryRow(row.nativeRule,member.actual,work),null);
            Clause clause=NativeDependentProductionRows.ordinary(rule,member.actual,work);need(clause.literals.size()==1,"Original donor must be a whole unit, not one literal of a rule");return new NativeUnit(source,null,clause.literals.get(0));}}
        throw new IllegalStateException("Original unit is not a physical member of the receiving cut");
    }
    static final class Input {
        final List<Node> nodes;final List<Integer> arguments;final String predicate;final boolean positive;final Set<Source> sources;final int depth;
        Input(NativeUnit unit,Runnable work){
            List<Node> ns=new ArrayList<>();List<Integer> roots=new ArrayList<>();
            if(unit.prefix!=null){NativePrenexUnitScope p=unit.prefix;List<Integer> binders=new ArrayList<>();for(int i=0;i<p.binders.size();i++){work.run();NativePrenexUnitScope.Binder binder=p.binders.get(i);List<Integer> args=new ArrayList<>();if(!binder.universal)for(int dependency:binder.precedingUniversals){work.run();args.add(binders.get(dependency));}binders.add(ns.size());ns.add(new Node(binder.universal?Kind.VARIABLE:Kind.APPLICATION,binder.universal?i:-1,null,binder.universal?null:unit.source,binder.universal?-1:i,args));}for(NativePrenexUnitScope.Slot slot:p.slots){work.run();if(slot.binder>=0)roots.add(binders.get(slot.binder));else{roots.add(ns.size());ns.add(new Node(Kind.VALUE,-1,slot.value,null,-1,Collections.emptyList()));}}}
            else{Map<Integer,Integer> variables=new HashMap<>();for(Slot slot:unit.atom.arguments){work.run();if(slot.variable>=0){Integer id=variables.get(slot.variable);if(id==null){id=ns.size();variables.put(slot.variable,id);ns.add(new Node(Kind.VARIABLE,slot.variable,null,null,-1,Collections.emptyList()));}roots.add(id);}else{roots.add(ns.size());ns.add(new Node(Kind.VALUE,-1,slot.value,null,-1,Collections.emptyList()));}}}
            nodes=Collections.unmodifiableList(ns);arguments=Collections.unmodifiableList(roots);predicate=unit.predicate();positive=unit.positive();sources=Collections.singleton(unit.source);depth=0;
        }
        Input(Output output){nodes=output.nodes;arguments=output.arguments;predicate=output.predicate;positive=output.positive;sources=output.sources;depth=output.depth;}
        Input(ContextDependentRuntimeInspection.Product product,Runnable work){List<Node> ns=new ArrayList<>();for(ContextDependentRuntimeInspection.Node n:product.nodes){work.run();ns.add(new Node(n.kind,n.variable,n.value,n.function,n.binder,n.arguments));}nodes=ns;arguments=product.arguments;predicate=product.predicate;positive=product.positive;sources=product.sources;depth=product.depth;}
    }
    static Input input(ContextDependentRuntimeInspection.Frame frame,List<Output> earlier,Step step,Donor donor,Runnable work){work.run();need(donor.original==null,"Original unit requires actual native capture");if(donor.earlier){need(donor.index<earlier.size(),"Only internally executed earlier outputs may be donors");Output output=earlier.get(donor.index);need(output.cut.equals(step.cut),"Earlier output cannot cross receiving cuts without a barrier");return new Input(output);}List<ContextDependentRuntimeInspection.Product> pool=frame.cuts.get(step.cut);need(pool!=null&&donor.index<pool.size(),"Exact visible runtime occurrence required");return new Input(pool.get(donor.index),work);}
    static Set<Integer> variables(Input input,Runnable work){Set<Integer> vars=new LinkedHashSet<>(),seen=new HashSet<>();Deque<Integer> pending=new ArrayDeque<>(input.arguments);while(!pending.isEmpty()){work.run();int id=pending.pop();need(id>=0&&id<input.nodes.size(),"Input graph root in range");if(!seen.add(id))continue;Node n=input.nodes.get(id);if(n.kind==Kind.VARIABLE)vars.add(n.variable);for(int arg:n.arguments){work.run();need(arg>=0&&arg<id,"Input graph topological");pending.push(arg);}}return vars;}
    private static final class T {
        final Kind kind;final int variable,binder;final SemanticTermSnapshot value;final Source function;final List<T> args;
        T(Kind kind,int variable,SemanticTermSnapshot value,Source function,int binder,List<T> args){this.kind=kind;this.variable=variable;this.value=value;this.function=function;this.binder=binder;this.args=args;}
    }
    private static final class Pair {final T a,b;Pair(T a,T b){this.a=a;this.b=b;}}
    private static final class NoMatch extends Exception {NoMatch(String why){super(why);}}
    private static final class Engine {
        final Member child;final List<Cut> cuts;final ContextDependentRuntimeInspection.Frame frame;final Request request;final Runnable work;int variables,nodes;
        Engine(Member child,List<Cut> cuts,ContextDependentRuntimeInspection.Frame frame,Request request){this.child=child;this.cuts=cuts;this.frame=frame;this.request=request;work=ContextGroundProductionRounds.budget(request.work);}
        void allocate(){work.run();need(++nodes<=request.nodes,"Whole execution term/certificate node bound");}
        T term(Kind kind,int variable,SemanticTermSnapshot value,Source function,int binder,List<T> args){allocate();return new T(kind,variable,value,function,binder,args);}
        T variable(){return term(Kind.VARIABLE,variables++,null,null,-1,Collections.emptyList());}
        T slot(Slot slot,Map<Integer,T> vars){work.run();if(slot.variable<0)return term(Kind.VALUE,-1,slot.value,null,-1,Collections.emptyList());T t=vars.get(slot.variable);if(t==null){t=variable();vars.put(slot.variable,t);}return t;}
        List<T> imported(Input input,Map<Integer,T> vars)throws Exception{
            boolean[] used=new boolean[input.nodes.size()];Deque<Integer> todo=new ArrayDeque<>(input.arguments);while(!todo.isEmpty()){work.run();int id=todo.pop();need(id>=0&&id<used.length,"Runtime root in range");if(used[id])continue;used[id]=true;for(int arg:input.nodes.get(id).arguments){work.run();need(arg>=0&&arg<id,"Runtime input DAG required");todo.push(arg);}}
            T[] ts=new T[used.length];for(int i=0;i<used.length;i++)if(used[i]){interrupt();work.run();Node n=input.nodes.get(i);if(n.kind==Kind.VARIABLE){T t=vars.get(n.variable);if(t==null){t=variable();vars.put(n.variable,t);}ts[i]=t;}else{List<T> args=new ArrayList<>();for(int arg:n.arguments){work.run();args.add(ts[arg]);}ts[i]=term(n.kind,-1,n.value,n.function,n.binder,args);}}List<T> out=new ArrayList<>();for(int root:input.arguments){work.run();out.add(ts[root]);}return out;
        }
        T deref(T t,Map<Integer,T> bindings){int hops=0;while(t.kind==Kind.VARIABLE&&bindings.containsKey(t.variable)){work.run();need(++hops<=bindings.size(),"Cyclic runtime substitution");t=bindings.get(t.variable);}return t;}
        boolean occurs(int variable,T t,Map<Integer,T> bindings){Deque<T> todo=new ArrayDeque<>();todo.push(t);Set<T> seen=Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());while(!todo.isEmpty()){work.run();T n=deref(todo.pop(),bindings);if(!seen.add(n))continue;if(n.kind==Kind.VARIABLE&&n.variable==variable)return true;for(T arg:n.args){work.run();todo.push(arg);}}return false;}
        boolean unify(List<T> left,List<T> right,Map<Integer,T> bindings){if(left.size()!=right.size())return false;Deque<Pair> todo=new ArrayDeque<>();for(int i=0;i<left.size();i++){work.run();todo.push(new Pair(left.get(i),right.get(i)));}while(!todo.isEmpty()){work.run();Pair p=todo.pop();T a=deref(p.a,bindings),b=deref(p.b,bindings);if(a==b)continue;if(a.kind==Kind.VARIABLE){if(occurs(a.variable,b,bindings))return false;bindings.put(a.variable,b);}else if(b.kind==Kind.VARIABLE){if(occurs(b.variable,a,bindings))return false;bindings.put(b.variable,a);}else if(a.kind==Kind.VALUE&&b.kind==Kind.VALUE){if(!a.value.semanticallyEquals(b.value))return false;}else if(a.kind==Kind.APPLICATION&&b.kind==Kind.APPLICATION&&a.binder==b.binder&&a.function.equals(b.function)&&a.args.size()==b.args.size()){for(int i=0;i<a.args.size();i++){work.run();todo.push(new Pair(a.args.get(i),b.args.get(i)));}}else return false;}return true;}
        Output step(Step step,List<Output> earlier)throws Exception{
            Entry rule=entry(child,cuts,step,work);Map<Integer,T> ruleVars=new LinkedHashMap<>(),bindings=new LinkedHashMap<>();List<List<T>> literals=new ArrayList<>(),donors=new ArrayList<>();List<Map<Integer,T>> donorVars=new ArrayList<>();
            for(ContextProductionCapture.Atom atom:rule.clause.literals){work.run();List<T> terms=new ArrayList<>();for(Slot slot:atom.arguments)terms.add(slot(slot,ruleVars));literals.add(terms);}Set<Source> support=new LinkedHashSet<>();support.add(rule.source);int depth=0,at=0;
            for(int i=0;i<rule.clause.literals.size();i++)if(i!=step.retainedLiteral){interrupt();work.run();Donor selected=step.donors.get(at++);Input donor=selected.original==null?input(frame,earlier,step,selected,work):new Input(unit(child,cuts,step.cut,selected.original,work),work);Map<Integer,T> vars=new LinkedHashMap<>();List<T> terms=imported(donor,vars);ContextProductionCapture.Atom literal=rule.clause.literals.get(i);if(donor.positive==literal.positive||!donor.predicate.equals(literal.predicate)||!unify(literals.get(i),terms,bindings))throw new NoMatch("Complete ordered tuple has no rigid typed unifier; no inequality/refutation inferred");donors.add(terms);donorVars.add(vars);for(Source source:donor.sources){work.run();support.add(source);}depth=Math.max(depth,donor.depth);}
            need(depth<request.preparation.projection.qualification.append.iteration.policy.steps,"Runtime consequence depth bound");Writer writer=new Writer(this,bindings);List<List<Integer>> ls=new ArrayList<>(),ds=new ArrayList<>();for(List<T> row:literals)ls.add(writer.roots(row));for(List<T> row:donors)ds.add(writer.roots(row));Map<Integer,Integer> rb=writer.bindings(ruleVars);List<Map<Integer,Integer>> db=new ArrayList<>();for(Map<Integer,T> vars:donorVars)db.add(writer.bindings(vars));return new Output(step,rule,support,depth+1,writer,ls,ds,rb,db);
        }
    }
    private static final class Writer {
        final Engine engine;final Map<Integer,T> substitution;final IdentityHashMap<T,Integer> ids=new IdentityHashMap<>();final Map<Integer,Integer> variables=new HashMap<>();final List<Node> nodes=new ArrayList<>();
        Writer(Engine engine,Map<Integer,T> substitution){this.engine=engine;this.substitution=substitution;}
        int take(T root){T start=engine.deref(root,substitution);Deque<T> todo=new ArrayDeque<>();todo.push(start);while(!todo.isEmpty()){engine.work.run();T t=engine.deref(todo.peek(),substitution);if(ids.containsKey(t)){todo.pop();continue;}boolean ready=true;for(T arg:t.args){engine.work.run();T a=engine.deref(arg,substitution);if(!ids.containsKey(a)){todo.push(a);ready=false;break;}}if(!ready)continue;List<Integer> args=new ArrayList<>();for(T arg:t.args){engine.work.run();args.add(ids.get(engine.deref(arg,substitution)));}int variable=-1;if(t.kind==Kind.VARIABLE){Integer v=variables.get(t.variable);if(v==null){v=variables.size();variables.put(t.variable,v);}variable=v;}engine.allocate();ids.put(t,nodes.size());nodes.add(new Node(t.kind,variable,t.value,t.function,t.binder,args));todo.pop();}return ids.get(start);}
        List<Integer> roots(List<T> roots){List<Integer> out=new ArrayList<>();for(T t:roots){engine.work.run();out.add(take(t));}return out;}
        Map<Integer,Integer> bindings(Map<Integer,T> input){Map<Integer,Integer> out=new LinkedHashMap<>();for(Map.Entry<Integer,T> e:input.entrySet()){engine.work.run();out.put(e.getKey(),take(e.getValue()));}return out;}
    }
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.current(base.actual,base.inventory,r.preparation.projection.qualification.append.iteration.policy);ContextDependentReplayInspection.current(child.actual,child.inventory,r.preparation.projection.qualification.append.iteration.policy);for(Cut c:cuts)for(Member m:c.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,r.preparation.projection.qualification.append.iteration.policy);}
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request request)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual parent/child/cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,request);
        try{ContextDependentRuntimeModelInspection.Result prepared=ContextDependentRuntimeModelInspection.inspect(base,child,cuts,request.preparation);if(prepared.status==ContextDependentRuntimeModelInspection.Status.BASE_REFUTED)return new Result(Status.BASE_REFUTED,prepared.reason,Collections.emptyList(),prepared);if(prepared.status==ContextDependentRuntimeModelInspection.Status.CANDIDATE_REFUTED)return new Result(Status.CANDIDATE_REFUTED,prepared.reason,Collections.emptyList(),prepared);if(prepared.status!=ContextDependentRuntimeModelInspection.Status.RUNTIME_MODELLED_PENDING)return new Result(Status.INCOMPLETE,prepared.phase+": "+prepared.reason,Collections.emptyList(),null);
            try{Engine engine=new Engine(child,cuts,prepared.frame,request);List<Output> outputs=new ArrayList<>();for(Step step:request.steps){interrupt();outputs.add(engine.step(step,outputs));}new ContextDependentRuntimeExecutionAudit(child,cuts,prepared.frame,request).check(outputs);interrupt();return new Result(Status.EXECUTED_PENDING,"Explicit native residual steps checked against actual runtime graphs; receiving-barrier/default admission unknown",outputs,prepared);}catch(NoMatch missing){interrupt();return new Result(Status.NO_MATCH,missing.getMessage(),Collections.emptyList(),null);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){interrupt();return new Result(Status.INCOMPLETE,failure.getMessage(),Collections.emptyList(),null);}
        }finally{current(base,child,cuts,request);}
    }
}
