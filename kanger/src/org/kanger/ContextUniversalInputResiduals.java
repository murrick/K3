/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextWitnessResultTransport.Cut;
import org.kanger.ContextWitnessResultTransport.Item;
import org.kanger.ContextWitnessResultTransport.Origin;
import org.kanger.ContextWitnessResultTransport.Packet;
import org.kanger.ContextWitnessResultTransport.PrivatePool;

/** Exhaustive one-pivot input/schema residuals; frozen origins are rigid, never universal variables. */
final class ContextUniversalInputResiduals {
    static final class Term {
        final int variable;
        final Origin origin;
        final SemanticTermSnapshot value;
        private Term(int variable,Origin origin,SemanticTermSnapshot value){this.variable=variable;this.origin=origin;this.value=value;}
    }
    static final class Literal {
        final String predicate;
        final boolean positive;
        final List<Term> arguments;
        private Literal(String predicate,boolean positive,List<Term> arguments) {
            this.predicate=predicate;this.positive=positive;this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));
        }
    }
    static final class Residual {
        final List<Literal> literals;
        final Set<Integer> variables;
        final Set<Origin> origins;
        private Residual(List<Literal> input,Runnable work) {
            List<Literal> literals=new ArrayList<>();Set<Integer> variables=new LinkedHashSet<>();Set<Origin> origins=new LinkedHashSet<>();
            for(Literal literal:input) {
                work.run();boolean duplicate=false;
                for(Literal prior:literals){work.run();if(exact(prior,literal,work)){duplicate=true;break;}}
                if(duplicate)continue;literals.add(literal);
                for(Term term:literal.arguments){work.run();if(term.variable>=0)variables.add(term.variable);if(term.origin!=null)origins.add(term.origin);}
            }
            this.literals=Collections.unmodifiableList(literals);this.variables=Collections.unmodifiableSet(variables);this.origins=Collections.unmodifiableSet(origins);
        }
        boolean equivalent(Residual other,Runnable work) {
            work.run();return other!=null&&literals.size()==other.literals.size()&&variables.size()==other.variables.size()
                &&origins.equals(other.origins)&&permutation(literals,other.literals,0,new boolean[other.literals.size()],new HashMap<>(),new HashMap<>(),work);
        }
    }
    static final class Step {
        final ContextUniversalUnitClosure.Proof schema;
        final Item donor;
        final Support donorProof,support;
        final int pivot;
        final Map<Integer,ContextWitnessResultTransport.Slot> assignment;
        final Residual residual;
        private Step(ContextUniversalUnitClosure.Proof schema,Item donor,Support donorProof,int pivot,
                Map<Integer,ContextWitnessResultTransport.Slot> assignment,Residual residual,Support support) {
            this.schema=schema;this.donor=donor;this.donorProof=donorProof;this.pivot=pivot;this.residual=residual;this.support=support;
            this.assignment=Collections.unmodifiableMap(new LinkedHashMap<>(assignment));
        }
    }
    static final class Result {
        final boolean enumerated,privateInput;
        final String reason;
        final UUID basis,localBasis;
        final Cut parent,target;
        final int attempts;
        final List<Step> steps;
        private Result(boolean enumerated,String reason,ContextUniversalUnitClosure.Result theory,Packet common,Packet local,boolean privateInput,int attempts,List<Step> steps) {
            this.enumerated=enumerated;this.reason=reason;this.privateInput=privateInput;this.attempts=attempts;
            basis=common==null?null:common.identity;localBasis=local==null?null:local.identity;
            parent=theory==null?null:theory.parent;target=theory==null?null:theory.target;
            this.steps=Collections.unmodifiableList(new ArrayList<>(steps));
        }
    }
    private static boolean rigid(Term a,Term b) {
        return a.origin!=null||b.origin!=null?Objects.equals(a.origin,b.origin):a.value.semanticallyEquals(b.value);
    }
    private static boolean exact(Literal a,Literal b,Runnable work) {
        if(a.positive!=b.positive||!a.predicate.equals(b.predicate)||a.arguments.size()!=b.arguments.size())return false;
        for(int i=0;i<a.arguments.size();++i) {
            work.run();Term x=a.arguments.get(i),y=b.arguments.get(i);
            if(x.variable>=0||y.variable>=0){if(x.variable!=y.variable)return false;}else if(!rigid(x,y))return false;
        }
        return true;
    }
    private static boolean match(Literal a,Literal b,Map<Integer,Integer> forward,Map<Integer,Integer> reverse,Runnable work) {
        if(a.positive!=b.positive||!a.predicate.equals(b.predicate)||a.arguments.size()!=b.arguments.size())return false;
        for(int i=0;i<a.arguments.size();++i) {
            work.run();Term x=a.arguments.get(i),y=b.arguments.get(i);
            if(x.variable>=0||y.variable>=0) {
                if(x.variable<0||y.variable<0)return false;
                Integer mapped=forward.putIfAbsent(x.variable,y.variable),back=reverse.putIfAbsent(y.variable,x.variable);
                if(mapped!=null&&mapped.intValue()!=y.variable||back!=null&&back.intValue()!=x.variable)return false;
            }else if(!rigid(x,y))return false;
        }
        return true;
    }
    private static boolean permutation(List<Literal> a,List<Literal> b,int index,boolean[] used,Map<Integer,Integer> forward,Map<Integer,Integer> reverse,Runnable work) {
        work.run();if(index==a.size())return true;
        for(int i=0;i<b.size();++i)if(!used[i]) {
            work.run();Map<Integer,Integer> next=new HashMap<>(forward),back=new HashMap<>(reverse);
            if(!match(a.get(index),b.get(i),next,back,work))continue;
            used[i]=true;if(permutation(a,b,index+1,used,next,back,work))return true;used[i]=false;
        }
        return false;
    }
    private static Residual residual(Clause schema,int pivot,Map<Integer,ContextWitnessResultTransport.Slot> assignment,Runnable work) {
        List<Literal> literals=new ArrayList<>();
        for(int i=0;i<schema.literals.size();++i) {
            work.run();if(i==pivot)continue;Atom atom=schema.literals.get(i);List<Term> arguments=new ArrayList<>();
            for(Slot term:atom.arguments) {
                work.run();ContextWitnessResultTransport.Slot actual=term.variable<0?null:assignment.get(term.variable);
                arguments.add(actual!=null?new Term(-1,actual.origin,actual.value):new Term(term.variable,null,term.value));
            }
            literals.add(new Literal(atom.predicate,atom.positive,arguments));
        }
        if(literals.isEmpty())throw new IllegalStateException("Unexpected empty positive input residual");
        return new Residual(literals,work);
    }
    private static Support support(ContextUniversalUnitClosure.Proof schema,Support donor,int maximumDepth,Runnable work) {
        int depth=Math.max(schema.support.depth,donor.depth);
        if(depth==Integer.MAX_VALUE||depth+1>maximumDepth)throw new IllegalStateException("Input residual step exceeds depth limit");
        Set<Source> sources=new LinkedHashSet<>();for(Source source:schema.support.sources){work.run();sources.add(source);}
        for(Source source:donor.sources){work.run();sources.add(source);}return new Support(sources,depth+1);
    }
    private static void verify(Step step,int maximumDepth,Runnable work) {
        work.run();if(step.pivot<0||step.pivot>=step.schema.clause.literals.size()||!step.donor.proofs.contains(step.donorProof))
            throw new IllegalStateException("Different input residual pivot/donor proof");
        Map<Integer,ContextWitnessResultTransport.Slot> assignment=new LinkedHashMap<>();
        if(!ContextWitnessClauseCollision.match(step.schema.clause.literals.get(step.pivot),step.donor,assignment,work)
                ||!assignment.equals(step.assignment)||!step.residual.equivalent(residual(step.schema.clause,step.pivot,assignment,work),work)
                ||!step.support.equals(support(step.schema,step.donorProof,maximumDepth,work)))
            throw new IllegalStateException("Different input residual derivation");
    }
    static Result scan(ContextUniversalUnitClosure.Result theory,Packet input,int maximumDepth,int maximumSteps,int maximumWork) {
        return scan(theory,input,input,null,maximumDepth,maximumSteps,maximumWork);
    }
    static Result scanPrivate(ContextUniversalUnitClosure.Result theory,PrivatePool pool,int maximumDepth,int maximumSteps,int maximumWork) {
        if(pool==null)throw new IllegalArgumentException("Sealed private input required");
        return scan(theory,pool.common,pool.local,pool,maximumDepth,maximumSteps,maximumWork);
    }
    private static Result scan(ContextUniversalUnitClosure.Result theory,Packet common,Packet local,PrivatePool pool,int maximumDepth,int maximumSteps,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumSteps<1||maximumSteps>65536||maximumWork<1)throw new IllegalArgumentException("Bounded input residual scan required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            frame(theory,common,local,pool,maximumDepth,maximumWork,work);
            List<Step> steps=new ArrayList<>();int attempts=0;
            for(ContextUniversalUnitClosure.Proof schema:theory.proofs)for(int pivot=0;pivot<schema.clause.literals.size();++pivot) {
                work.run();Atom literal=schema.clause.literals.get(pivot);if(literal.positive)continue;
                for(Item item:local.items) {
                    work.run();++attempts;Map<Integer,ContextWitnessResultTransport.Slot> assignment=new LinkedHashMap<>();
                    if(!ContextWitnessClauseCollision.match(literal,item,assignment,work))continue;
                    Residual residual=residual(schema.clause,pivot,assignment,work);
                    for(Support donor:item.proofs) {
                        work.run();if(steps.size()==maximumSteps)throw new IllegalStateException("Input residual step count limit");
                        Step step=new Step(schema,item,donor,pivot,assignment,residual,support(schema,donor,maximumDepth,work));
                        verify(step,maximumDepth,work);steps.add(step);
                    }
                }
            }
            return new Result(true,"Every input fact/schema body pivot and donor alternative enumerated; residual closure, native replay and family qualification remain open",theory,common,local,pool!=null,attempts,steps);
        }catch(Exception failure){return new Result(false,failure.getMessage(),theory,common,local,pool!=null,0,Collections.emptyList());}
    }
    /** Exact validated historical input frame, not live connection or publication authority. */
    static final class Frame {
        final ContextUniversalUnitClosure.Result theory;
        final Packet common,local;
        final PrivatePool pool;
        private Frame(ContextUniversalUnitClosure.Result theory,Packet common,Packet local,PrivatePool pool) {
            this.theory=theory;this.common=common;this.local=local;this.pool=pool;
        }
    }
    static Frame frame(ContextUniversalUnitClosure.Result theory,Packet common,Packet local,PrivatePool pool,int maximumDepth,int maximumWork,Runnable work) {
        if(common==null||local==null)throw new IllegalStateException("Actual sealed input required");
        if(pool==null?common!=local:pool.common!=common||pool.local!=local)throw new IllegalStateException("Different owning input frame");
        ContextUniversalUnitClosure.check(theory,maximumDepth,work);
        ContextWitnessResultTransport.family(common,theory.parent.equals(theory.target)?Collections.singletonList(theory.parent):Arrays.asList(theory.parent,theory.target),maximumWork);
        if(pool!=null&&(!pool.cut.equals(theory.target)||!Cut.of(pool.x).equals(theory.parent)))throw new IllegalStateException("Input residual scan changed private frame");
        for(Item item:local.items) {
            work.run();if(!item.atom.positive)throw new IllegalStateException("Positive input residual facts required");
            for(Support support:item.proofs){work.run();if(support.depth>maximumDepth)throw new IllegalStateException("Input residual carried depth limit");}
        }
        return new Frame(theory,common,local,pool);
    }
    static Residual schema(Clause clause,Runnable work) {return residual(clause,-1,Collections.emptyMap(),work);}
    static Residual fact(Item item,Runnable work) {
        List<Term> arguments=new ArrayList<>();
        for(ContextWitnessResultTransport.Slot slot:item.atom.arguments){work.run();arguments.add(new Term(-1,slot.origin,slot.value));}
        return new Residual(Collections.singletonList(new Literal(item.atom.predicate,item.atom.positive,arguments)),work);
    }
    static final class UnificationFailure extends IllegalStateException {
        private UnificationFailure(){super("Incompatible typed unit/body pivot");}
    }
    private static Residual apart(Residual clause,int[] next,Runnable work) {
        Map<Integer,Integer> variables=new HashMap<>();List<Literal> literals=new ArrayList<>();
        for(Literal literal:clause.literals) {
            work.run();List<Term> arguments=new ArrayList<>();
            for(Term term:literal.arguments) {
                work.run();if(term.variable<0)arguments.add(term);
                else {Integer id=variables.get(term.variable);if(id==null){id=next[0]++;variables.put(term.variable,id);}arguments.add(new Term(id,null,null));}
            }
            literals.add(new Literal(literal.predicate,literal.positive,arguments));
        }
        return new Residual(literals,work);
    }
    private static Term dereference(Term term,Map<Integer,Term> substitution,Runnable work) {
        while(term.variable>=0) {work.run();Term next=substitution.get(term.variable);if(next==null)break;term=next;}
        return term;
    }
    /** Principal flat unifier. Different origins do not become equal by a variable renaming. */
    static Residual resolve(Residual unit,Residual horn,int pivot,Runnable work) {
        work.run();if(unit.literals.size()!=1||!unit.literals.get(0).positive||pivot<0||pivot>=horn.literals.size()||horn.literals.get(pivot).positive)
            throw new IllegalArgumentException("Typed positive unit/negative body pivot required");
        int[] next={0};Residual left=apart(unit,next,work),right=apart(horn,next,work);
        Literal a=left.literals.get(0),b=right.literals.get(pivot);
        if(!a.predicate.equals(b.predicate)||a.arguments.size()!=b.arguments.size())throw new UnificationFailure();
        Map<Integer,Term> substitution=new HashMap<>();
        for(int i=0;i<a.arguments.size();++i) {
            work.run();Term x=dereference(a.arguments.get(i),substitution,work),y=dereference(b.arguments.get(i),substitution,work);
            if(x.variable>=0) {if(y.variable!=x.variable)substitution.put(x.variable,y);}
            else if(y.variable>=0)substitution.put(y.variable,x);
            else if(!rigid(x,y))throw new UnificationFailure();
        }
        List<Literal> literals=new ArrayList<>();
        for(int i=0;i<right.literals.size();++i)if(i!=pivot) {
            work.run();Literal literal=right.literals.get(i);List<Term> arguments=new ArrayList<>();
            for(Term term:literal.arguments){work.run();arguments.add(dereference(term,substitution,work));}
            literals.add(new Literal(literal.predicate,literal.positive,arguments));
        }
        if(literals.isEmpty())throw new IllegalStateException("Unexpected empty positive typed resolvent");
        return apart(new Residual(literals,work),new int[]{0},work);
    }

}
