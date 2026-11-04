/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentModelInspection.*;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentRuntimeInspection.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.*;

/** Independent indexed-DAG consumer. Never evaluates TermView/Arena nodes or calls carrier truth code. */
final class ContextDependentRuntimeModelAudit {
    private static final class Shape {final Set<Signature> vocabulary=new LinkedHashSet<>();final Set<Function> functions=new LinkedHashSet<>();final List<SemanticTermSnapshot> values=new ArrayList<>();}
    private final Frame frame;private final Map<String,Model> models;private final int pointLimit,cellLimit,productLimit,nodeLimit,truthWork;
    private final Map<Function,Integer> declarations=new LinkedHashMap<>();private final Set<Source> sources=new HashSet<>();
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Indexed runtime truth audit interrupted");}
    private Shape nativeShape(Member member,Runnable work)throws Exception{
        Shape shape=new Shape();need(member.inventory.initialization.isEmpty()&&member.inventory.rows.size()==member.inventory.primary.size(),"Whole primary-only actual runtime model basis required");
        for(ContextProductionInventory.Row row:member.inventory.rows){interrupt();work.run();Source source=new Source(member.inventory.context,member.inventory.revision,row.nativeRule,member.inventory.localX,"inventory:"+member.inventory.fingerprint);need(sources.add(source),"Distinct physical original runtime Source required");Rule rule=(Rule)member.actual.getRules().get(row.nativeRule);
            need(rule!=null&&rule.getId()==row.nativeRule&&row.origin.equals(rule.getOrigin())&&!rule.isQuery()&&!rule.isGenerated()&&!rule.isDeleted(member.actual)&&rule.getTree().size()==1&&!rule.getTree().get(0).isEmpty(),"Exact actual native primary clause required");
            if(rule.getTree().get(0).size()==1&&rule.getTree().get(0).get(0).isAntc()){
                NativePrenexUnitScope prefix=NativePrenexUnitScope.observeFactoryRow(row.nativeRule,member.actual,work);shape.vocabulary.add(new Signature(prefix.predicate,prefix.slots.size()));
                for(int i=0;i<prefix.binders.size();i++){work.run();NativePrenexUnitScope.Binder b=prefix.binders.get(i);if(!b.universal){Function f=new Function(source,i);need(declarations.put(f,b.precedingUniversals.size())==null,"Unique actual dependency function declaration");shape.functions.add(f);}}
                for(NativePrenexUnitScope.Slot slot:prefix.slots){work.run();if(slot.binder<0)shape.values.add(slot.value);}
            }else{
                int heads=0;for(Domain d:rule.getTree().get(0)){work.run();need(!d.isSystem(member.actual)&&!d.isCalculated(member.actual),"Ordinary original runtime vocabulary required");if(d.isAntc())heads++;shape.vocabulary.add(new Signature(d.getPredicate(member.actual).getName(member.actual),d.getRange()));for(IArgument a:d.getArguments()){work.run();if(a.getType()==ArgumentType.TVARIABLE)need(((TVariable)a.getObject(member.actual)).getRuleId()==rule.getId(),"Original variable owner required");else{need(a.getType()==ArgumentType.TERM&&SemanticTermSnapshot.isOrdinaryValue(a.getValue(member.actual)),"Ordinary actual typed value required");shape.values.add(SemanticTermSnapshot.capture(a.getValue(member.actual)));}}}need(heads<=1,"Supported actual Horn clause required");
            }
        }return shape;
    }
    ContextDependentRuntimeModelAudit(Member child,List<Cut> cuts,Frame frame,Map<String,Model> models,ContextDependentRuntimeModelInspection.Request request,Runnable work)throws Exception{
        this(child,cuts,frame,models,request.projection.qualification.points,request.projection.qualification.cells,request.projection.products,request.projection.nodes,request.truthWork,work);
    }
    ContextDependentRuntimeModelAudit(Member child,List<Cut> cuts,Frame frame,Map<String,Model> models,int pointLimit,int cellLimit,int productLimit,int nodeLimit,int truthWork,Runnable work)throws Exception{
        need(pointLimit>0&&cellLimit>0&&productLimit>0&&nodeLimit>0&&truthWork>0,"Positive independent indexed model bounds required");this.frame=frame;this.models=models;this.pointLimit=pointLimit;this.cellLimit=cellLimit;this.productLimit=productLimit;this.nodeLimit=nodeLimit;this.truthWork=truthWork;
        need(frame!=null&&frame.context.equals(child.inventory.context)&&frame.revision==child.inventory.revision&&frame.fingerprint.equals(child.inventory.fingerprint),"Actual child-bound runtime frame required");
        Map<UUID,Shape> physical=new LinkedHashMap<>();physical.put(child.inventory.context,nativeShape(child,work));for(Cut cut:cuts)for(Member member:cut.members){work.run();need(!physical.containsKey(member.inventory.context),"Distinct runtime physical members required");physical.put(member.inventory.context,nativeShape(member,work));}
        Map<String,List<Shape>> bases=new LinkedHashMap<>();bases.put(ContextDependentCutInspection.local(child.inventory.context),Collections.singletonList(physical.get(child.inventory.context)));for(Cut cut:cuts){work.run();List<Shape> basis=new ArrayList<>();basis.add(physical.get(child.inventory.context));for(Member member:cut.members){work.run();basis.add(physical.get(member.inventory.context));}need(bases.put(cut.key,basis)==null,"Exact distinct runtime cut required");}
        need(bases.keySet().equals(frame.cuts.keySet())&&bases.keySet().equals(models.keySet()),"Exact complete runtime model and physical cut coverage required");
        int cells=0,products=0,nodes=0;for(String cut:bases.keySet()){
            interrupt();work.run();Model m=models.get(cut);need(m.points.size()<=pointLimit,"Runtime named-point bound");ordinary(m.otherRepresentative);for(int i=0;i<m.points.size();i++){work.run();ordinary(m.points.get(i));need(!m.points.get(i).semanticallyEquals(m.otherRepresentative),"ALL-other representative outside named basis required");for(int j=0;j<i;j++){work.run();need(!m.points.get(i).semanticallyEquals(m.points.get(j)),"Distinct typed model points required");}}
            for(Map.Entry<Signature,Relation> relation:m.relations.entrySet()){work.run();need(++cells<=cellLimit,"Whole runtime model table bound");for(List<Integer> key:relation.getValue().exceptions.keySet()){work.run();need(++cells<=cellLimit,"Whole runtime model exception bound");tuple(key,relation.getKey().arity,m,work);}}
            for(Map.Entry<Function,Choice> f:m.functions.entrySet()){work.run();need(++cells<=cellLimit,"Whole runtime function table bound");Integer rank=declarations.get(f.getKey());need(rank!=null&&rank==f.getValue().arity,"Actual Source/binder/dependency rank required");point(f.getValue().otherwise,m);for(Map.Entry<List<Integer>,Integer> e:f.getValue().exceptions.entrySet()){work.run();need(++cells<=cellLimit,"Whole runtime function exception bound");tuple(e.getKey(),rank,m,work);point(e.getValue(),m);}}
            Set<Signature> vocabulary=new HashSet<>();Set<Function> functions=new HashSet<>();for(Shape shape:bases.get(cut)){work.run();for(Signature sig:shape.vocabulary){work.run();vocabulary.add(sig);}for(Function f:shape.functions){work.run();functions.add(f);}for(SemanticTermSnapshot value:shape.values){work.run();fixed(m,value,work);}}
            int common=0,visible=0;boolean inVisible=false;for(Product product:frame.cuts.get(cut)){
                interrupt();work.run();need(++products<=productLimit,"Whole runtime product bound");need(product.cut.equals(cut)&&product.producer!=null&&sources.contains(product.producer)&&product.sources.contains(product.producer)&&product.depth>0&&product.retainedLiteral>=-1,"Actual indexed product metadata required");for(Source source:product.sources){work.run();need(sources.contains(source),"Whole actual physical support required");}
                if(product.role==Role.COMMON){need(!inVisible&&product.occurrence==common++,"Ordered raw common occurrences required");}else{need(product.role==Role.VISIBLE&&product.occurrence==visible++,"Ordered raw visible occurrences required");inVisible=true;}
                vocabulary.add(new Signature(product.predicate,product.arguments.size()));boolean[] reachable=new boolean[product.nodes.size()];Deque<Integer> pending=new ArrayDeque<>();for(int root:product.arguments){work.run();need(root>=0&&root<reachable.length,"Runtime root in range");pending.push(root);}while(!pending.isEmpty()){work.run();int id=pending.pop();if(reachable[id])continue;reachable[id]=true;for(int arg:product.nodes.get(id).arguments){work.run();need(arg>=0&&arg<id,"Runtime graph must be topological");pending.push(arg);}}
                for(int i=0;i<product.nodes.size();i++){work.run();need(++nodes<=nodeLimit&&reachable[i],"Whole reachable runtime graph bound required");Node node=product.nodes.get(i);switch(node.kind){case VARIABLE:need(node.variable>=0&&node.value==null&&node.function==null&&node.binder==-1&&node.arguments.isEmpty(),"Runtime variable shape required");break;case VALUE:need(node.function==null&&node.binder==-1&&node.arguments.isEmpty(),"Flat runtime value shape required");fixed(m,node.value,work);break;case APPLICATION:need(node.value==null,"Function cannot contain a chosen model value");Function f=new Function(node.function,node.binder);Integer rank=declarations.get(f);need(rank!=null&&rank==node.arguments.size()&&product.sources.contains(f.source),"Exact runtime function dependency rank and physical ancestry required");functions.add(f);break;default:throw new IllegalStateException("Unknown runtime node kind");}}
            }
            need(vocabulary.equals(m.relations.keySet())&&functions.equals(m.functions.keySet()),"No missing/extra runtime model vocabulary/functions");
        }
    }
    private static void ordinary(SemanticTermSnapshot v)throws Exception{need(v!=null&&SemanticTermSnapshot.isOrdinaryValue(v.materialize()),"Ordinary typed model value required");}
    private static void point(int p,Model m){need(p>=0&&p<=m.points.size(),"Runtime named/ALL-other class in range");}
    private static void tuple(List<Integer> tuple,int rank,Model m,Runnable work){need(tuple.size()==rank,"Exact runtime interpretation tuple rank required");for(int p:tuple){work.run();point(p,m);}}
    private static int fixed(Model m,SemanticTermSnapshot value,Runnable work)throws Exception{ordinary(value);for(int i=0;i<m.points.size();i++){work.run();if(m.points.get(i).semanticallyEquals(value))return i;}throw new IllegalStateException("Every runtime/native typed constant must be a named point");}
    /** Topological array evaluation, not TermView traversal or MGU substitution. */
    private static void product(Product product,Model m,Runnable work)throws Exception{
        Relation relation=m.relations.get(new Signature(product.predicate,product.arguments.size()));need(relation!=null,"Whole runtime predicate interpretation required");
        if(relation.exceptions.isEmpty()){work.run();need(relation.otherwise==product.positive,"Uniform indexed runtime product false");return;}
        Map<Integer,Integer> slots=new LinkedHashMap<>();for(Node node:product.nodes){work.run();if(node.kind==ContextDependentProductionInspection.Kind.VARIABLE&&!slots.containsKey(node.variable))slots.put(node.variable,slots.size());}
        int[] digits=new int[slots.size()],values=new int[product.nodes.size()];int level=0;
        // Cartesian class DFS. The OTHER class denotes every admissible unmentioned value.
        while(level>=0){interrupt();work.run();if(level<digits.length){if(digits[level]>m.points.size()){digits[level]=0;level--;if(level>=0)digits[level]++;}else level++;continue;}
            for(int i=0;i<product.nodes.size();i++){work.run();Node node=product.nodes.get(i);switch(node.kind){case VARIABLE:values[i]=digits[slots.get(node.variable)];break;case VALUE:values[i]=fixed(m,node.value,work);break;case APPLICATION:Choice f=m.functions.get(new Function(node.function,node.binder));need(f!=null&&f.arity==node.arguments.size(),"Exact runtime dependency interpretation required");List<Integer> args=new ArrayList<>();for(int arg:node.arguments){work.run();args.add(values[arg]);}Integer v=f.exceptions.get(args);values[i]=v==null?f.otherwise:v;break;default:throw new IllegalStateException("Unknown runtime node");}}
            List<Integer> tuple=new ArrayList<>();for(int root:product.arguments){work.run();tuple.add(values[root]);}Boolean v=relation.exceptions.get(tuple);need((v==null?relation.otherwise:v)==product.positive,"Indexed runtime product false on named/ALL-other assignment");
            level--;if(level>=0)digits[level]++;
        }
    }
    int truth()throws Exception{Runnable work=ContextGroundProductionRounds.budget(truthWork);int obligations=0;for(Map.Entry<String,List<Product>> cut:frame.cuts.entrySet())for(Product product:cut.getValue()){interrupt();work.run();product(product,models.get(cut.getKey()),work);obligations++;}return obligations;}
}
