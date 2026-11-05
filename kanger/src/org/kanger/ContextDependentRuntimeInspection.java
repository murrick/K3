/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.ContextProductionCapture.Source;

/** Lossless typed runtime projection of fresh model-audited pending proof products.
 * Not a RuleFactory writer, ordinary query dispatcher, model interpretation or admission capability. */
public final class ContextDependentRuntimeInspection {
    private ContextDependentRuntimeInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,PROJECTED_PENDING,INCOMPLETE}
    public enum Role {COMMON,VISIBLE}
    public static final class Request {
        public final ContextDependentCandidateModelInspection.Request qualification;
        public final int products,nodes,copyWork,auditWork;
        public Request(ContextDependentCandidateModelInspection.Request qualification,int products,int nodes,int copyWork,int auditWork){
            if(qualification==null||products<1||nodes<1||copyWork<1||auditWork<1)throw new IllegalArgumentException("Fresh qualification and separate bounded projection/audit required");
            this.qualification=qualification;this.products=products;this.nodes=nodes;this.copyWork=copyWork;this.auditWork=auditWork;
        }
        /** Internal numeric bounds for independently qualified checked-history consumers. */
        Request(int products,int nodes,int copyWork,int auditWork){if(products<1||nodes<1||copyWork<1||auditWork<1)throw new IllegalArgumentException("Positive projection bounds required");qualification=null;this.products=products;this.nodes=nodes;this.copyWork=copyWork;this.auditWork=auditWork;}
    }
    /** Per-occurrence topological DAG. APPLICATION children are ordered indices, never scalar Cvars. */
    public static final class Node {
        public final Kind kind;public final int variable,binder;
        public final SemanticTermSnapshot value;public final Source function;
        public final List<Integer> arguments;
        private Node(TermView term,List<Integer> args){kind=term.kind;variable=term.variable;binder=term.binder;value=term.value;function=term.symbolSource;arguments=Collections.unmodifiableList(new ArrayList<>(args));}
    }
    public static final class Product {
        public final String cut;public final Role role;public final int occurrence;
        public final Source producer;public final Set<Source> sources;
        public final int depth,retainedLiteral;public final boolean positive;public final String predicate;
        public final List<Node> nodes;public final List<Integer> arguments;
        private Product(String cut,Role role,int occurrence,Row row,List<Node> nodes,List<Integer> args){this.cut=cut;this.role=role;this.occurrence=occurrence;producer=row.rule;sources=Collections.unmodifiableSet(new LinkedHashSet<>(row.sources));depth=row.depth;retainedLiteral=row.retainedLiteral;positive=row.positive;predicate=row.predicate;this.nodes=Collections.unmodifiableList(new ArrayList<>(nodes));arguments=Collections.unmodifiableList(new ArrayList<>(args));}
    }
    public static final class Frame {
        public final UUID id=UUID.randomUUID(),context;public final long revision;public final String fingerprint;
        public final Map<String,List<Product>> cuts;
        private Frame(Member child,Map<String,List<Product>> cuts){context=child.inventory.context;revision=child.inventory.revision;fingerprint=child.inventory.fingerprint;Map<String,List<Product>> frozen=new LinkedHashMap<>();for(Map.Entry<String,List<Product>> e:cuts.entrySet())frozen.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));this.cuts=Collections.unmodifiableMap(frozen);}
    }
    public static final class Result {
        public final Status status;public final String reason;public final Frame frame;
        public final ContextDependentCandidateModelInspection.Result qualification;
        private Result(Status status,String reason,Frame frame,ContextDependentCandidateModelInspection.Result qualification){this.status=status;this.reason=reason;this.frame=frame;this.qualification=qualification;}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Typed runtime projection interrupted");}
    private static ContextDependentRoundInspection.Barrier last(ContextDependentCandidateModelInspection.Result q){List<ContextDependentRoundInspection.Barrier> bs=q.candidate.proposed.checked.barriers;return bs.get(bs.size()-1);}
    private static Product copy(String cut,Role role,int occurrence,Row row,Request request,Runnable work,int[] total)throws Exception{
        need(++total[0]<=request.products,"Whole runtime product occurrence bound");work.run();
        IdentityHashMap<TermView,Integer> indices=new IdentityHashMap<>();List<Node> nodes=new ArrayList<>();List<Integer> roots=new ArrayList<>();
        for(TermView root:row.arguments){Deque<TermView> todo=new ArrayDeque<>();todo.push(root);while(!todo.isEmpty()){
            interrupt();work.run();TermView t=todo.peek();if(indices.containsKey(t)){todo.pop();continue;}
            boolean ready=true;for(TermView a:t.arguments){work.run();if(!indices.containsKey(a)){todo.push(a);ready=false;break;}}if(!ready)continue;
            List<Integer> args=new ArrayList<>();for(TermView a:t.arguments){work.run();args.add(indices.get(a));}
            need(++total[1]<=request.nodes,"Whole runtime graph node bound");indices.put(t,nodes.size());nodes.add(new Node(t,args));todo.pop();
        }roots.add(indices.get(root));}
        return new Product(cut,role,occurrence,row,nodes,roots);
    }
    /** Independent traversal compares original typed terms to the indexed runtime graph; no digest equality shortcut. */
    private static void audit(Product p,String cut,Role role,int occurrence,Row row,Runnable work)throws Exception{
        interrupt();work.run();need(p.cut.equals(cut)&&p.role==role&&p.occurrence==occurrence&&p.producer.equals(row.rule)&&p.sources.equals(row.sources)&&p.depth==row.depth&&p.retainedLiteral==row.retainedLiteral&&p.positive==row.positive&&p.predicate.equals(row.predicate)&&p.arguments.size()==row.arguments.size(),"Exact runtime occurrence metadata required");
        Deque<TermView> terms=new ArrayDeque<>();Deque<Integer> ids=new ArrayDeque<>();for(int i=0;i<row.arguments.size();i++){work.run();terms.push(row.arguments.get(i));ids.push(p.arguments.get(i));}
        IdentityHashMap<TermView,Integer> mapping=new IdentityHashMap<>();Map<Integer,TermView> inverse=new HashMap<>();Map<Integer,Integer> variables=new HashMap<>(),back=new HashMap<>();Set<Integer> seen=new HashSet<>();
        while(!terms.isEmpty()){
            interrupt();work.run();TermView t=terms.pop();int id=ids.pop();need(id>=0&&id<p.nodes.size(),"Runtime node index in range");Integer old=mapping.put(t,id);if(old!=null){need(old==id,"Shared original term must remain shared");continue;}need(inverse.put(id,t)==null,"Distinct original DAG nodes must not be merged");seen.add(id);Node n=p.nodes.get(id);
            need(t.kind==n.kind&&t.binder==n.binder&&Objects.equals(t.symbolSource,n.function)&&(t.value==null?n.value==null:t.value.semanticallyEquals(n.value))&&t.arguments.size()==n.arguments.size(),"Typed value/function/source/binder/arity preserved");
            if(t.kind==Kind.VARIABLE){Integer v=variables.put(t.variable,n.variable),b=back.put(n.variable,t.variable);need((v==null||v==n.variable)&&(b==null||b==t.variable),"Bijective whole-product variable correlation required");}
            for(int i=0;i<t.arguments.size();i++){work.run();int a=n.arguments.get(i);need(a>=0&&a<id,"Runtime graph must be topological");terms.push(t.arguments.get(i));ids.push(a);}
        }need(seen.size()==p.nodes.size(),"No extra unproved runtime graph nodes");
    }
    /** Internal lossless projection of checked history. Bounds are not a supplied preparation receipt. */
    static Frame project(Member child,ContextDependentRoundInspection.Result checked,Request request)throws Exception{
                need(checked!=null&&!checked.barriers.isEmpty(),"Whole checked receiving history required");ContextDependentRoundInspection.Barrier b=checked.barriers.get(checked.barriers.size()-1);need((child.inventory.revision+":"+child.inventory.localX+":"+child.inventory.fingerprint).equals(b.basis.get(child.inventory.context)),"Runtime projection must bind the exact checked child basis");Runnable work=ContextGroundProductionRounds.budget(request.copyWork);Map<String,List<Product>> projected=new LinkedHashMap<>();int[] total={0,0};
                for(String cut:b.cuts.keySet()){work.run();List<Product> products=new ArrayList<>();int occurrence=0;for(ContextDependentRoundInspection.Ticket ticket:b.common)products.add(copy(cut,Role.COMMON,occurrence++,b.observed.get(ticket),request,work,total));occurrence=0;for(Row row:b.cuts.get(cut).products)products.add(copy(cut,Role.VISIBLE,occurrence++,row,request,work,total));projected.put(cut,products);}
                Frame frame=new Frame(child,projected);work=ContextGroundProductionRounds.budget(request.auditWork);
                need(frame.cuts.keySet().equals(b.cuts.keySet()),"Exact runtime cut coverage required");
                for(String cut:b.cuts.keySet()){work.run();List<Product> ps=frame.cuts.get(cut);need(ps.size()==b.common.size()+b.cuts.get(cut).products.size(),"All common/private raw occurrences required");int at=0,occurrence=0;for(ContextDependentRoundInspection.Ticket ticket:b.common)audit(ps.get(at++),cut,Role.COMMON,occurrence++,b.observed.get(ticket),work);occurrence=0;for(Row row:b.cuts.get(cut).products)audit(ps.get(at++),cut,Role.VISIBLE,occurrence++,row,work);}
        interrupt();return frame;
    }
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.current(base.actual,base.inventory,r.qualification.append.iteration.policy);ContextDependentReplayInspection.current(child.actual,child.inventory,r.qualification.append.iteration.policy);for(Cut c:cuts)for(Member m:c.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,r.qualification.append.iteration.policy);}
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request request)throws Exception{
        return inspect(base,child,supplied,request,()->{});
    }
    static Result inspect(Member base,Member child,List<Cut> supplied,Request request,Runnable chargeAudit)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||request==null||request.qualification==null)throw new IllegalArgumentException("Actual native parent/child/cuts and fresh qualification required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,request);
        try{
            ContextDependentCandidateModelInspection.Result q=ContextDependentCandidateModelInspection.inspect(base,child,cuts,request.qualification,chargeAudit);
            if(q.status==ContextDependentCandidateModelInspection.Status.BASE_REFUTED)return new Result(Status.BASE_REFUTED,q.reason,null,q);
            if(q.status==ContextDependentCandidateModelInspection.Status.CANDIDATE_REFUTED)return new Result(Status.CANDIDATE_REFUTED,q.reason,null,q);
            if(q.status!=ContextDependentCandidateModelInspection.Status.MODELLED_PENDING)return new Result(Status.INCOMPLETE,q.phase+": "+q.reason,null,null);
            try{
                Frame frame=project(child,q.candidate.proposed.checked,request);
                interrupt();return new Result(Status.PROJECTED_PENDING,"Fresh proof products copied and independently checked; ordinary native query/write admission unknown",frame,q);
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}catch(Exception e){return new Result(Status.INCOMPLETE,"Runtime projection/audit: "+e.getMessage(),null,null);}
        }finally{current(base,child,cuts,request);}
    }
}
