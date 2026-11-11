/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.ContextDependentModelInspection.*;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentProductionInspection.Row;
import org.kanger.ContextDependentProductionInspection.TermView;
import org.kanger.ContextProductionCapture.*;
import org.kanger.units.Rule;

/** Bounded constructive all-value quotient search; failed guesses never prove theory inconsistency. */
public final class ContextDependentModelSearchInspection {
    private ContextDependentModelSearchInspection(){}
    public enum Status {MODELLED_PENDING,REFUTED,INCOMPLETE}
    public static final class Limits {
        public final int points,cells,choiceCells,candidates,schemaWork,searchWork,auditWork,originalWork,finalWork;
        public Limits(int points,int cells,int choiceCells,int candidates,int schemaWork,int searchWork,int auditWork,int originalWork,int finalWork){if(points<1||points>1000||cells<1||cells>1000000||choiceCells<1||choiceCells>1000000||candidates<1||schemaWork<1||searchWork<1||auditWork<1||originalWork<1||finalWork<1)throw new IllegalArgumentException("Separate positive quotient/search/native audit limits required");this.points=points;this.cells=cells;this.choiceCells=choiceCells;this.candidates=candidates;this.schemaWork=schemaWork;this.searchWork=searchWork;this.auditWork=auditWork;this.originalWork=originalWork;this.finalWork=finalWork;}
    }
    public static final class Request {
        public final ContextDependentIterationInspection.Request iteration;public final List<SemanticTermSnapshot> extraPoints;public final SemanticTermSnapshot otherRepresentative;public final Limits limits;public final ContextDependentRuntimeContinuationInspection.Projection projection;
        public final List<Map<String,Model>> proposals;
        public Request(ContextDependentIterationInspection.Request iteration,List<SemanticTermSnapshot> extraPoints,SemanticTermSnapshot otherRepresentative,Limits limits,ContextDependentRuntimeContinuationInspection.Projection projection){this(iteration,extraPoints,otherRepresentative,limits,projection,Collections.emptyList());}
        /** Proposals are guesses, never receipts: fresh native/final/indexed audits are mandatory. */
        public Request(ContextDependentIterationInspection.Request iteration,List<SemanticTermSnapshot> extraPoints,SemanticTermSnapshot otherRepresentative,Limits limits,ContextDependentRuntimeContinuationInspection.Projection projection,List<Map<String,Model>> proposals){
            if(iteration==null||extraPoints==null||extraPoints.contains(null)||otherRepresentative==null||limits==null||projection==null||extraPoints.size()>limits.points||proposals==null||proposals.size()>limits.candidates)throw new IllegalArgumentException("Actual family request, typed palette and bounded model construction required");
            this.iteration=iteration;this.extraPoints=Collections.unmodifiableList(new ArrayList<>(extraPoints));this.otherRepresentative=otherRepresentative;this.limits=limits;this.projection=projection;
            List<Map<String,Model>> copy=new ArrayList<>();for(Map<String,Model> proposal:proposals){if(proposal==null||proposal.containsKey(null)||proposal.containsValue(null))throw new IllegalArgumentException("Whole proposed family interpretation required");copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(proposal)));}this.proposals=Collections.unmodifiableList(copy);
        }
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final Map<String,Model> models;public final ContextDependentIterationInspection.Result family;public final ContextDependentRuntimeInspection.Frame frame;public final int tried,originalRows,finalObligations,runtimeObligations;
        private Result(Status status,String phase,String reason,Map<String,Model> models,ContextDependentIterationInspection.Result family,ContextDependentRuntimeInspection.Frame frame,int tried,int originals,int finals,int runtime){this.status=status;this.phase=phase;this.reason=reason;this.models=Collections.unmodifiableMap(new LinkedHashMap<>(models));this.family=family;this.frame=frame;this.tried=tried;originalRows=originals;finalObligations=finals;runtimeObligations=runtime;}
        private static Result incomplete(String phase,String why,int tried){return new Result(Status.INCOMPLETE,phase,why,Collections.emptyMap(),null,null,tried,0,0,0);}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{ContextDependentRuntimeExecution.interrupt();}
    private static final class Schema {
        final Set<Signature> relations=new LinkedHashSet<>();final Map<Function,Integer> functions=new LinkedHashMap<>();final List<SemanticTermSnapshot> points=new ArrayList<>();
    }
    private static final class Slot {
        final Signature relation;final Function function;final int rank,radix;final List<Integer> tuple;
        Slot(Signature relation,Function function,int rank,int radix,List<Integer> tuple){this.relation=relation;this.function=function;this.rank=rank;this.radix=radix;this.tuple=tuple;}
    }
    private static void ordinary(SemanticTermSnapshot v)throws Exception{need(v!=null&&SemanticTermSnapshot.isOrdinaryValue(v.materialize()),"Ordinary typed palette point required");}
    private static void point(Schema s,SemanticTermSnapshot v,Request r,Runnable work)throws Exception{work.run();ordinary(v);need(!v.semanticallyEquals(r.otherRepresentative),"ALL-other representative must be outside every actual/extra named basis");for(SemanticTermSnapshot old:s.points){work.run();if(old.semanticallyEquals(v))return;}need(s.points.size()<r.limits.points,"Whole typed named point bound");s.points.add(v);}
    private static Source source(Member m,ContextProductionInventory.Row row){return new Source(m.inventory.context,m.inventory.revision,row.nativeRule,m.inventory.localX,"inventory:"+m.inventory.fingerprint);}
    private static Map<String,Schema> schema(Member x,List<Cut> cuts,ContextDependentRoundInspection.Result checked,Request r)throws Exception{
        Runnable work=ContextGroundProductionRounds.budget(r.limits.schemaWork);ordinary(r.otherRepresentative);List<Member> members=new ArrayList<>();members.add(x);for(Cut cut:cuts)members.addAll(cut.members);Map<UUID,Schema> physical=new LinkedHashMap<>();Map<Function,Integer> declarations=new LinkedHashMap<>();Set<Source> sources=new HashSet<>();
        for(Member m:members){work.run();need(m.inventory.initialization.isEmpty()&&m.inventory.rows.size()==m.inventory.primary.size(),"Whole primary-only model-search physical view required");Schema s=new Schema();need(physical.put(m.inventory.context,s)==null,"Distinct physical model-search member required");for(ContextProductionInventory.Row row:m.inventory.primary){interrupt();work.run();Rule rule=(Rule)m.actual.getRules().get(row.nativeRule);need(rule!=null&&rule.getId()==row.nativeRule&&row.origin.equals(rule.getOrigin()),"Exact actual native model-search original required");Source source=source(m,row);need(sources.add(source),"Unique physical search Source required");if(NativeDependentProductionRows.unit(rule)){NativePrenexUnitScope p=NativePrenexUnitScope.observeFactoryRow(row.nativeRule,m.actual,work);s.relations.add(new Signature(p.predicate,p.slots.size()));for(int i=0;i<p.binders.size();i++){work.run();NativePrenexUnitScope.Binder b=p.binders.get(i);if(!b.universal){Function f=new Function(source,i);s.functions.put(f,b.precedingUniversals.size());need(declarations.put(f,b.precedingUniversals.size())==null,"Unique witness declaration required");}}for(NativePrenexUnitScope.Slot slot:p.slots)if(slot.binder<0)point(s,slot.value,r,work);}else{Clause clause=NativeDependentProductionRows.ordinary(rule,m.actual,work);for(Atom atom:clause.literals){work.run();s.relations.add(new Signature(atom.predicate,atom.arguments.size()));for(ContextProductionCapture.Slot slot:atom.arguments){work.run();if(slot.variable<0)point(s,slot.value,r,work);}}}}}
        Map<String,List<Schema>> bases=new LinkedHashMap<>();bases.put(ContextDependentCutInspection.local(x.inventory.context),Collections.singletonList(physical.get(x.inventory.context)));for(Cut cut:cuts){List<Schema> list=new ArrayList<>();list.add(physical.get(x.inventory.context));for(Member m:cut.members)list.add(physical.get(m.inventory.context));need(bases.put(cut.key,list)==null,"Whole distinct logical search cut required");}
        ContextDependentRoundInspection.Barrier last=checked.barriers.get(checked.barriers.size()-1);need(bases.keySet().equals(last.cuts.keySet()),"Exact complete checked search cut coverage required");Map<String,Schema> out=new LinkedHashMap<>();int metadata=0;
        for(String cut:bases.keySet()){interrupt();work.run();Schema s=new Schema();for(Schema base:bases.get(cut)){for(Signature sig:base.relations){work.run();s.relations.add(sig);}for(Map.Entry<Function,Integer> f:base.functions.entrySet()){work.run();s.functions.put(f.getKey(),f.getValue());}for(SemanticTermSnapshot value:base.points)point(s,value,r,work);}for(SemanticTermSnapshot extra:r.extraPoints)point(s,extra,r,work);
            List<Row> rows=new ArrayList<>();for(ContextDependentRoundInspection.Ticket ticket:last.common){work.run();Row row=last.observed.get(ticket);need(row!=null,"Every actual common occurrence required");rows.add(row);}rows.addAll(last.cuts.get(cut).products);
            for(Row row:rows){interrupt();work.run();need(sources.contains(row.rule)&&row.sources.contains(row.rule),"Exact producer/support in model-search carrier required");for(Source origin:row.sources){work.run();need(sources.contains(origin),"Whole physical carrier ancestry required");}s.relations.add(new Signature(row.predicate,row.arguments.size()));Deque<TermView> todo=new ArrayDeque<>(row.arguments);Set<TermView> seen=Collections.newSetFromMap(new IdentityHashMap<TermView,Boolean>());while(!todo.isEmpty()){work.run();TermView t=todo.pop();if(!seen.add(t))continue;switch(t.kind){case VARIABLE:need(t.variable>=0,"Exact final variable required");break;case VALUE:point(s,t.value,r,work);break;case APPLICATION:Function f=new Function(t.symbolSource,t.binder);Integer rank=declarations.get(f);need(rank!=null&&rank==t.arguments.size()&&row.sources.contains(f.source),"Exact imported function declaration/dependencies/ancestry required");s.functions.put(f,rank);for(TermView child:t.arguments){work.run();todo.push(child);}break;default:throw new IllegalStateException("Unknown actual search term shape");}}}need(s.relations.size()<=r.limits.cells-metadata,"Whole family signature/function metadata bound");metadata+=s.relations.size();need(s.functions.size()<=r.limits.cells-metadata,"Whole family function metadata bound");metadata+=s.functions.size();out.put(cut,s);
        }return out;
    }
    private static Model uniform(Schema s,Request r,Runnable work){Map<Signature,Relation> rs=new LinkedHashMap<>();for(Signature sig:s.relations){work.run();rs.put(sig,new Relation(false,Collections.emptyMap()));}Map<Function,Choice> fs=new LinkedHashMap<>();for(Map.Entry<Function,Integer> f:s.functions.entrySet()){work.run();fs.put(f.getKey(),new Choice(f.getValue(),0,Collections.emptyMap()));}return new Model(s.points,r.otherRepresentative,rs,fs);}
    private static List<Slot> slots(Schema s,boolean dense,Request r,Runnable work){List<Slot> slots=new ArrayList<>();int classes=s.points.size()+1;for(Map.Entry<Function,Integer> f:s.functions.entrySet())table(slots,null,f.getKey(),f.getValue(),classes,classes,dense,r,work);for(Signature sig:s.relations)table(slots,sig,null,sig.arity,2,classes,dense,r,work);return slots;}
    private static void table(List<Slot> out,Signature relation,Function function,int rank,int radix,int classes,boolean dense,Request r,Runnable work){
        work.run();int count=1;if(dense)for(int i=0;i<rank;i++){work.run();need(count<=(r.limits.choiceCells-out.size())/classes,"Cartesian search cells exceed bound BEFORE tuple allocation");count*=classes;}need(count<=r.limits.choiceCells-out.size(),"Whole current-cut search choice bound");if(!dense){out.add(new Slot(relation,function,rank,radix,null));return;}
        int[] digits=new int[rank];for(int row=0;row<count;row++){work.run();List<Integer> tuple=new ArrayList<>();for(int v:digits){work.run();tuple.add(v);}out.add(new Slot(relation,function,rank,radix,Collections.unmodifiableList(tuple)));for(int i=digits.length-1;i>=0;i--){work.run();if(++digits[i]<classes)break;digits[i]=0;}}
    }
    private static boolean next(List<Slot> slots,int[] digits,Runnable work){for(int i=digits.length-1;i>=0;i--){work.run();if(++digits[i]<slots.get(i).radix)return true;digits[i]=0;}return false;}
    private static Model candidate(Schema s,List<Slot> slots,int[] digits,boolean dense,Request r,Runnable work){
        Map<Signature,Boolean> defaults=new LinkedHashMap<>();Map<Signature,Map<List<Integer>,Boolean>> rs=new LinkedHashMap<>();Map<Function,Integer> values=new LinkedHashMap<>();Map<Function,Map<List<Integer>,Integer>> fs=new LinkedHashMap<>();for(Signature sig:s.relations){work.run();defaults.put(sig,false);rs.put(sig,new LinkedHashMap<>());}for(Function f:s.functions.keySet()){work.run();values.put(f,0);fs.put(f,new LinkedHashMap<>());}for(int i=0;i<slots.size();i++){work.run();Slot slot=slots.get(i);if(slot.relation!=null){if(!dense)defaults.put(slot.relation,digits[i]!=0);else if(digits[i]!=0)rs.get(slot.relation).put(slot.tuple,true);}else if(!dense)values.put(slot.function,digits[i]);else if(digits[i]!=0)fs.get(slot.function).put(slot.tuple,digits[i]);}
        Map<Signature,Relation> relations=new LinkedHashMap<>();for(Signature sig:s.relations){work.run();relations.put(sig,new Relation(defaults.get(sig),rs.get(sig)));}Map<Function,Choice> functions=new LinkedHashMap<>();for(Map.Entry<Function,Integer> f:s.functions.entrySet()){work.run();functions.put(f.getKey(),new Choice(f.getValue(),values.get(f.getKey()),fs.get(f.getKey())));}return new Model(s.points,r.otherRepresentative,relations,functions);
    }
    private static void current(Member x,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.current(x.actual,x.inventory,r.iteration.policy);for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,r.iteration.policy);}
    public static Result inspect(Member x,List<Cut> supplied,Request r)throws Exception{
        return inspect(x,supplied,r,()->{},()->{});
    }
    static Result inspect(Member x,List<Cut> supplied,Request r,Runnable chargeCandidate,Runnable chargeAudit)throws Exception{
        if(x==null||supplied==null||supplied.contains(null)||r==null)throw new IllegalArgumentException("Actual complete family required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(x,cuts,r);String phase="family";int tried=0;
        try{try{
            ContextDependentIterationInspection.Result family=ContextDependentIterationInspection.inspect(x,cuts,r.iteration);interrupt();if(family.status==ContextDependentIterationInspection.Status.COLLISION)return new Result(Status.REFUTED,"sourced-refutation","Fresh actual sourced family refutation; not failed model search",Collections.emptyMap(),family,null,0,0,0,0);if(family.status!=ContextDependentIterationInspection.Status.FIXED_POINT)return Result.incomplete(phase,family.reason,0);
            return construct(x,cuts,family.checked,r,family,chargeCandidate,chargeAudit);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){interrupt();return Result.incomplete(phase,failure.getMessage(),tried);}}finally{current(x,cuts,r);}
    }
    /** Internal fresh replay boundary only; no public receipt-taking entrypoint. */
    static Result received(Member x,List<Cut> cuts,ContextDependentRoundInspection.Result checked,Request r,Runnable chargeCandidate,Runnable chargeAudit)throws Exception{
        interrupt();current(x,cuts,r);try{return construct(x,cuts,checked,r,null,chargeCandidate,chargeAudit);}finally{current(x,cuts,r);}
    }
    private static Result construct(Member x,List<Cut> cuts,ContextDependentRoundInspection.Result checked,Request r,ContextDependentIterationInspection.Result family,Runnable chargeCandidate,Runnable chargeAudit)throws Exception{
        String phase="schema";int tried=0;try{
            phase="schema";Map<String,Schema> schemas=schema(x,cuts,checked,r);Runnable build=ContextGroundProductionRounds.budget(r.limits.searchWork),localAudit=ContextGroundProductionRounds.budget(r.limits.auditWork);Runnable auditWork=()->{localAudit.run();chargeAudit.run();};
            phase="proposed-native-confirmation";
            for(Map<String,Model> proposal:r.proposals){
                interrupt();build.run();need(tried<r.limits.candidates,"Whole-family candidate-attempt bound");chargeCandidate.run();tried++;
                try{return confirm(x,cuts,checked,r,family,proposal,tried,auditWork,chargeAudit);}
                catch(ContextDependentModelAudit.Rejected rejected){/* A false proposed interpretation is only a failed guess. */}
            }
            phase="initial-models";Map<String,Model> models=new LinkedHashMap<>();for(Map.Entry<String,Schema> entry:schemas.entrySet())models.put(entry.getKey(),uniform(entry.getValue(),r,build));
            for(String cut:schemas.keySet()){
                boolean found=false;Schema s=schemas.get(cut);
                for(int pass=0;pass<2&&!found;pass++){
                    boolean dense=pass==1;if(dense&&s.points.isEmpty())break;phase=dense?"table-search":"uniform-search";List<Slot> choices=slots(s,dense,r,build);int[] digits=new int[choices.size()];boolean more=true;
                    while(more){interrupt();build.run();need(tried<r.limits.candidates,"Whole-family candidate-attempt bound");chargeCandidate.run();tried++;models.put(cut,candidate(s,choices,digits,dense,r,build));ContextDependentModelInspection.Request bounds=new ContextDependentModelInspection.Request(r.iteration,models,r.limits.points,r.limits.cells,r.limits.originalWork,r.limits.finalWork);ContextDependentModelAudit nativeAudit=new ContextDependentModelAudit(x,cuts,checked,bounds,auditWork);
                        try{nativeAudit.original(cut);nativeAudit.finalModel(cut);found=true;break;}catch(ContextDependentModelAudit.Rejected rejected){/* A false interpretation is only a failed guess. */}more=next(choices,digits,build);
                    }
                }
                if(!found)return Result.incomplete("search-space","No audited model found in the chosen bounded quotient; not a theory refutation",tried);
            }
            phase="native-confirmation";return confirm(x,cuts,checked,r,family,models,tried,auditWork,chargeAudit);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){interrupt();return Result.incomplete(phase,failure.getMessage(),tried);}
    }
    private static Result confirm(Member x,List<Cut> cuts,ContextDependentRoundInspection.Result checked,Request r,ContextDependentIterationInspection.Result family,Map<String,Model> models,int tried,Runnable auditWork,Runnable chargeAudit)throws Exception{
        ContextDependentModelAudit finalAudit=new ContextDependentModelAudit(x,cuts,checked,new ContextDependentModelInspection.Request(r.iteration,models,r.limits.points,r.limits.cells,r.limits.originalWork,r.limits.finalWork),auditWork);
        int originals=finalAudit.original(),finals=finalAudit.finalModel();interrupt();
        String phase="runtime-projection";
        try{
            ContextDependentRuntimeContinuationInspection.Projection p=r.projection;
            ContextDependentRuntimeInspection.Frame frame=ContextDependentRuntimeInspection.project(x,checked,new ContextDependentRuntimeInspection.Request(p.products,p.nodes,p.copyWork,p.auditWork));
            phase="runtime-shape";ContextDependentRuntimeModelAudit runtime=new ContextDependentRuntimeModelAudit(x,cuts,frame,models,r.limits.points,r.limits.cells,p.products,p.nodes,p.truthWork,ContextGroundProductionRounds.budget(p.shapeWork),chargeAudit);
            phase="runtime-truth";int obligations=runtime.truth();need(obligations==finals,"Every actual final occurrence must pass independent indexed truth");interrupt();
            return new Result(Status.MODELLED_PENDING,"constructed-and-independently-audited","Proposed or constructed per-cut all-value interpretations passed native original/final and indexed truth; not runtime/write admission",models,family,frame,tried,originals,finals,obligations);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
        catch(Exception failure){interrupt();return Result.incomplete(phase,failure.getMessage(),tried);}
    }
}
