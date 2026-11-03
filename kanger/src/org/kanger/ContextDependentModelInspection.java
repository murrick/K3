/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextProductionCapture.Source;

/** Supplied all-value quotient interpretation diagnostic; never chosen facts or admission authority. */
public final class ContextDependentModelInspection {
    private ContextDependentModelInspection(){}
    public static final class Signature {
        public final String name;public final int arity;
        public Signature(String name,int arity){if(name==null||name.isEmpty()||arity<0)throw new IllegalArgumentException("Exact predicate signature required");this.name=name;this.arity=arity;}
        @Override public boolean equals(Object o){return o instanceof Signature&&name.equals(((Signature)o).name)&&arity==((Signature)o).arity;}
        @Override public int hashCode(){return Objects.hash(name,arity);}
    }
    public static final class Function {
        public final Source source;public final int binder;
        public Function(Source source,int binder){if(source==null||binder<0)throw new IllegalArgumentException("Exact original function declaration required");this.source=source;this.binder=binder;}
        @Override public boolean equals(Object o){return o instanceof Function&&source.equals(((Function)o).source)&&binder==((Function)o).binder;}
        @Override public int hashCode(){return Objects.hash(source,binder);}
    }
    private static <T> Map<List<Integer>,T> tuples(Map<List<Integer>,T> rows){if(rows==null)throw new IllegalArgumentException("Whole default/exception table required");Map<List<Integer>,T> copy=new LinkedHashMap<>();for(Map.Entry<List<Integer>,T> e:rows.entrySet()){if(e.getKey()==null||e.getKey().contains(null)||e.getValue()==null)throw new IllegalArgumentException("Complete tuple and value required");copy.put(Collections.unmodifiableList(new ArrayList<>(e.getKey())),e.getValue());}return Collections.unmodifiableMap(copy);}
    public static final class Relation {
        public final boolean otherwise;public final Map<List<Integer>,Boolean> exceptions;
        public Relation(boolean otherwise,Map<List<Integer>,Boolean> exceptions){this.otherwise=otherwise;this.exceptions=tuples(exceptions);}
    }
    public static final class Choice {
        public final int arity,otherwise;public final Map<List<Integer>,Integer> exceptions;
        public Choice(int arity,int otherwise,Map<List<Integer>,Integer> exceptions){if(arity<0||otherwise<0)throw new IllegalArgumentException("Total dependency-function table required");this.arity=arity;this.otherwise=otherwise;this.exceptions=tuples(exceptions);}
    }
    public static final class Model {
        public final List<SemanticTermSnapshot> points;public final SemanticTermSnapshot otherRepresentative;
        public final Map<Signature,Relation> relations;public final Map<Function,Choice> functions;
        public Model(List<SemanticTermSnapshot> points,SemanticTermSnapshot otherRepresentative,Map<Signature,Relation> relations,Map<Function,Choice> functions){if(points==null||points.contains(null)||otherRepresentative==null||relations==null||functions==null||relations.containsKey(null)||relations.containsValue(null)||functions.containsKey(null)||functions.containsValue(null))throw new IllegalArgumentException("Whole typed quotient model required");this.points=Collections.unmodifiableList(new ArrayList<>(points));this.otherRepresentative=otherRepresentative;this.relations=Collections.unmodifiableMap(new LinkedHashMap<>(relations));this.functions=Collections.unmodifiableMap(new LinkedHashMap<>(functions));}
    }
    public static final class Request {
        public final ContextDependentIterationInspection.Request iteration;public final Map<String,Model> models;
        public final int points,cells,originalWork,finalWork;
        public Request(ContextDependentIterationInspection.Request iteration,Map<String,Model> models,int points,int cells,int originalWork,int finalWork){if(iteration==null||models==null||models.containsKey(null)||models.containsValue(null)||points<1||points>1000||cells<1||cells>1000000||originalWork<1||finalWork<1)throw new IllegalArgumentException("Separate bounded original and actual-final audits required");this.iteration=iteration;this.models=Collections.unmodifiableMap(new LinkedHashMap<>(models));this.points=points;this.cells=cells;this.originalWork=originalWork;this.finalWork=finalWork;}
    }
    public enum Status {AUDITED_SUPPLIED,REFUTED,INCOMPLETE}
    public static final class Result {
        public final Status status;public final String phase,reason;public final int originalRows,finalObligations;
        public final ContextDependentIterationInspection.Result iteration;
        private Result(Status status,String phase,String reason,int originals,int finals,ContextDependentIterationInspection.Result iteration){this.status=status;this.phase=phase;this.reason=reason;originalRows=originals;finalObligations=finals;this.iteration=iteration;}
    }
    private static void current(Member x,List<Cut> cuts,Request request)throws Exception{ContextDependentReplayInspection.current(x.actual,x.inventory,request.iteration.policy);for(Cut cut:cuts)for(Member member:cut.members)ContextDependentReplayInspection.currentMember(member.actual,member.inventory,request.iteration.policy);}
    public static Result inspect(Member x,List<Cut> supplied,Request request)throws Exception {
        if(x==null||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual whole family required");List<Cut> cuts=new ArrayList<>(supplied);current(x,cuts,request);
        try {
            ContextDependentIterationInspection.Result iteration=ContextDependentIterationInspection.inspect(x,cuts,request.iteration);
            if(iteration.status==ContextDependentIterationInspection.Status.COLLISION)return new Result(Status.REFUTED,"sourced-refutation","Actual checked refutation; not failed model search",0,0,iteration);
            if(iteration.status!=ContextDependentIterationInspection.Status.FIXED_POINT)return new Result(Status.INCOMPLETE,"iteration",iteration.reason,0,0,null);
            String phase="model-preflight";
            try {
                ContextDependentModelAudit audit=new ContextDependentModelAudit(x,cuts,iteration,request);phase="original-audit";int originals=audit.original();phase="actual-final-audit";int finals=audit.finalModel();if(Thread.currentThread().isInterrupted())throw new InterruptedException("Dependent model audit interrupted");
                return new Result(Status.AUDITED_SUPPLIED,"historical-supplied-model","Original and actual frozen-carrier truth checked over all value classes; native admission unknown",originals,finals,iteration);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
            catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.getMessage(),0,0,null);}
        }finally{current(x,cuts,request);}
    }
}
