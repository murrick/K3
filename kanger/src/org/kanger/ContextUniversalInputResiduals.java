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
    static Residual concretePremise(Residual clause,int pivot,Runnable work) {
        Literal literal=clause.literals.get(pivot);if(literal.positive)throw new IllegalStateException("Negative premise required");
        for(Term term:literal.arguments){work.run();if(term.variable>=0)throw new IllegalStateException("Concrete premise required");}
        return new Residual(Collections.singletonList(new Literal(literal.predicate,true,literal.arguments)),work);
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
        return resolve(unit,horn,pivot,false,false,work);
    }
    /** Empty is permitted only for the constraint proof kernel; old positive callers retain their guard. */
    static Residual resolveConstraint(Residual unit,Residual horn,int pivot,Runnable work) {
        return resolve(unit,horn,pivot,true,false,work);
    }
    /** Signed unit resolution includes negative units against a positive Horn head. */
    static Residual resolveSignedUnit(Residual unit,Residual horn,int pivot,Runnable work) {
        return resolve(unit,horn,pivot,true,true,work);
    }
    private static Residual resolve(Residual unit,Residual horn,int pivot,boolean allowEmpty,boolean signedUnit,Runnable work) {
        work.run();if(unit.literals.size()!=1||!signedUnit&&!unit.literals.get(0).positive||pivot<0||pivot>=horn.literals.size()||unit.literals.get(0).positive==horn.literals.get(pivot).positive)
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
        if(literals.isEmpty()){if(allowEmpty)return null;throw new IllegalStateException("Unexpected empty positive typed resolvent");}
        return apart(new Residual(literals,work),new int[]{0},work);
    }

    /** Principal positive hyperapplication to fixed units; null means a still-symbolic head, not empty. */
    static Residual applyFixedPositiveUnits(Residual producer,List<Residual> donors,Runnable work) {
        work.run();int[] next={0};Residual rule=apart(producer,next,work);Literal head=null;List<Literal> body=new ArrayList<>();
        for(Literal literal:rule.literals){work.run();if(literal.positive){if(head!=null)throw new IllegalArgumentException("One positive application head required");head=literal;}else body.add(literal);}
        if(head==null||body.size()<2||body.size()!=donors.size())throw new IllegalArgumentException("Complete multi-premise positive application required");
        Map<Integer,Term> substitution=new HashMap<>();
        for(int slot=0;slot<body.size();++slot) {
            work.run();Residual donor=donors.get(slot);if(donor==null||donor.literals.size()!=1||!donor.variables.isEmpty()||!donor.literals.get(0).positive)throw new IllegalArgumentException("Fixed positive application donor required");
            Literal actual=apart(donor,next,work).literals.get(0),formal=body.get(slot);
            if(!formal.predicate.equals(actual.predicate)||formal.arguments.size()!=actual.arguments.size())throw new UnificationFailure();
            for(int i=0;i<formal.arguments.size();++i){work.run();Term a=dereference(formal.arguments.get(i),substitution,work),b=dereference(actual.arguments.get(i),substitution,work);if(a.variable>=0){if(b.variable!=a.variable)substitution.put(a.variable,b);}else if(b.variable>=0)substitution.put(b.variable,a);else if(!rigid(a,b))throw new UnificationFailure();}
        }
        List<Term> arguments=new ArrayList<>();for(Term term:head.arguments){work.run();Term actual=dereference(term,substitution,work);if(actual.variable>=0)return null;arguments.add(actual);}
        return new Residual(Collections.singletonList(new Literal(head.predicate,true,arguments)),work);
    }

    /** Fixed signed donors discharge every literal except the explicitly selected conclusion. */
    static Residual applyFixedSignedUnits(Residual producer,int conclusion,List<Residual> donors,Runnable work) {
        return applySignedFixedDonors(producer,conclusion,donors,false,work);
    }
    /** Declared universal variables may remain in the head; every actual donor is still fixed. */
    static Residual applySymbolicHeadFixedUnits(Residual producer,int conclusion,List<Residual> donors,Runnable work) {
        return applySignedFixedDonors(producer,conclusion,donors,true,work);
    }
    private static Residual applySignedFixedDonors(Residual producer,int conclusion,List<Residual> donors,boolean symbolicHead,Runnable work) {
        work.run();if(conclusion<0||conclusion>=producer.literals.size()||producer.literals.size()<3||donors.size()!=producer.literals.size()-1)throw new IllegalArgumentException("Complete signed multi-premise application required");
        int[] next={0};Residual rule=apart(producer,next,work);Map<Integer,Term> substitution=new HashMap<>();int slot=0;
        for(int pivot=0;pivot<rule.literals.size();++pivot)if(pivot!=conclusion) {
            work.run();Residual donor=donors.get(slot++);Literal formal=rule.literals.get(pivot);
            if(donor==null||donor.literals.size()!=1||!donor.variables.isEmpty()||donor.literals.get(0).positive==formal.positive)throw new IllegalArgumentException("Fixed opposite-sign application donor required");
            Literal actual=apart(donor,next,work).literals.get(0);if(!formal.predicate.equals(actual.predicate)||formal.arguments.size()!=actual.arguments.size())throw new UnificationFailure();
            for(int i=0;i<formal.arguments.size();++i){work.run();Term a=dereference(formal.arguments.get(i),substitution,work),b=dereference(actual.arguments.get(i),substitution,work);if(a.variable>=0){if(b.variable!=a.variable)substitution.put(a.variable,b);}else if(b.variable>=0)substitution.put(b.variable,a);else if(!rigid(a,b))throw new UnificationFailure();}
        }
        Literal head=rule.literals.get(conclusion);List<Term> arguments=new ArrayList<>();for(Term term:head.arguments){work.run();Term actual=dereference(term,substitution,work);if(!symbolicHead&&actual.variable>=0)return null;arguments.add(actual);}
        return apart(new Residual(Collections.singletonList(new Literal(head.predicate,head.positive,arguments)),work),new int[]{0},work);
    }

    static String assertion(Residual clause,Runnable work) {
        StringBuilder text=new StringBuilder("!");for(int variable:clause.variables){work.run();text.append("@ctxfrozenv").append(variable).append(' ');}
        text.append('(');boolean first=true;
        for(Literal literal:clause.literals) {
            work.run();if(!first)text.append(" || ");first=false;if(!literal.positive)text.append('~');text.append(literal.predicate).append('(');
            for(int i=0;i<literal.arguments.size();++i){work.run();if(i>0)text.append(',');Term term=literal.arguments.get(i);if(term.variable>=0)text.append("ctxfrozenv").append(term.variable);else text.append('?');}
            text.append(')');
        }
        return text.append(");").toString();
    }
    static Queue<org.kanger.interfaces.ITerm> project(Residual clause,Mind target,Map<ContextWitnessUnaryChainVerifier.Root,Origin> carried,Runnable work)throws Exception {
        Queue<org.kanger.interfaces.ITerm> values=new LinkedList<>();
        for(Literal literal:clause.literals)for(Term term:literal.arguments) {
            work.run();if(term.variable>=0)continue;
            if(term.origin==null)values.add(target.getTerms().projectSemantic(term.value.materialize()));
            else {
                org.kanger.units.Term actual=null;
                for(Map.Entry<ContextWitnessUnaryChainVerifier.Root,Origin> entry:carried.entrySet()) {
                    work.run();if(!entry.getValue().equals(term.origin))continue;
                    if(actual!=null)throw new IllegalStateException("Ambiguous frozen native parameter");
                    actual=(org.kanger.units.Term)target.getTerms().get(entry.getKey().term);
                    if(actual==null||!actual.isCVariable()||actual.isDomini()||actual.getParent(target)!=null||actual.getRuleId()!=entry.getKey().owner)
                        throw new IllegalStateException("Different canonical frozen native parameter");
                }
                if(actual==null)throw new IllegalStateException("Missing original frozen native parameter");values.add(actual);
            }
        }
        return values;
    }
    static Residual nativeClause(org.kanger.units.Rule rule,Mind mind,Map<ContextWitnessUnaryChainVerifier.Root,Origin> carried,Runnable work)throws Exception {
        if(rule.isQuery()||rule.getTree().size()!=1)throw new IllegalStateException("One native typed assertion branch required");
        List<Literal> literals=new ArrayList<>();Map<Long,Integer> variables=new LinkedHashMap<>();
        for(org.kanger.units.Domain domain:rule.getTree().get(0)) {
            work.run();if(domain.isSystem(mind)||domain.isCalculated(mind)||domain.getRange()>8||"rule(1)".equals(domain.getPredicate(mind).toString(mind)))
                throw new IllegalStateException("Outside native typed literal scope");
            List<Term> arguments=new ArrayList<>();
            for(org.kanger.interfaces.IArgument argument:domain.getArguments()) {
                work.run();if(argument.getType()==org.kanger.enums.ArgumentType.TVARIABLE) {
                    org.kanger.units.TVariable variable=mind.getTVars().get(argument.getId());
                    if(variable==null||variable.getRuleId()!=rule.getId())throw new IllegalStateException("Foreign native universal declaration");
                    Integer id=variables.get(argument.getId());if(id==null){id=variables.size();variables.put(argument.getId(),id);}arguments.add(new Term(id,null,null));
                }else {
                    if(argument.getType()!=org.kanger.enums.ArgumentType.TERM)throw new IllegalStateException("Calculated or implicit typed native argument");
                    org.kanger.interfaces.ITerm value=argument.getValue(mind);
                    if(value!=null&&value.isCVariable()) {
                        org.kanger.units.Term root=(org.kanger.units.Term)value;
                        Origin origin=carried.get(new ContextWitnessUnaryChainVerifier.Root(root.getId(),root.getRuleId()));
                        if(origin==null||root.isDomini()||root.getParent(mind)!=null)throw new IllegalStateException("Noncanonical native frozen input root");
                        arguments.add(new Term(-1,origin,null));
                    }else {
                        if(!SemanticTermSnapshot.isOrdinaryValue(value))throw new IllegalStateException("Nonordinary typed native value");
                        arguments.add(new Term(-1,null,SemanticTermSnapshot.capture(value)));
                    }
                }
            }
            literals.add(new Literal(domain.getPredicate(mind).getName(mind),domain.isAntc(),arguments));
        }
        return new Residual(literals,work);
    }
    private static Literal eventLiteral(ContextWitnessProjection.Literal literal,boolean opposite,Map<ContextWitnessUnaryChainVerifier.Root,Origin> carried,Runnable work) {
        List<Term> arguments=new ArrayList<>();
        for(ContextWitnessProjection.Slot slot:literal.arguments) {
            work.run();if(slot.lineage.isEmpty())arguments.add(new Term(-1,null,slot.value));
            else {
                for(ContextWitnessProjection.Node node:slot.lineage){work.run();if(node.domini)throw new IllegalStateException("Dominating frozen producer event root");}
                ContextWitnessProjection.Node node=slot.lineage.get(slot.lineage.size()-1);
                Origin origin=carried.get(new ContextWitnessUnaryChainVerifier.Root(node.term,node.ownerRule));
                if(origin==null)throw new IllegalStateException("Unbound frozen producer event origin");arguments.add(new Term(-1,origin,null));
            }
        }
        return new Literal(literal.predicate,opposite?!literal.positive:literal.positive,arguments);
    }
    static Residual event(ContextWitnessProjection event,Map<ContextWitnessUnaryChainVerifier.Root,Origin> carried,Runnable work) {
        List<Literal> literals=new ArrayList<>();literals.add(eventLiteral(event.conclusion,false,carried,work));
        for(ContextWitnessProjection.Literal premise:event.premises){work.run();literals.add(eventLiteral(premise,true,carried,work));}return new Residual(literals,work);
    }
    static Residual event(Application event,Runnable work) {
        List<Literal> literals=new ArrayList<>();List<Fact> facts=new ArrayList<>();facts.add(event.conclusion);for(Fact premise:event.premises){work.run();facts.add(premise.opposite());}
        for(Fact fact:facts){work.run();List<Term> arguments=new ArrayList<>();for(SemanticTermSnapshot value:fact.arguments){work.run();arguments.add(new Term(-1,null,value));}literals.add(new Literal(fact.predicate,fact.positive,arguments));}
        return new Residual(literals,work);
    }
    private static boolean instanceLiteral(Literal formal,Literal actual,Map<Integer,Term> assignment,Runnable work) {
        if(formal.positive!=actual.positive||!formal.predicate.equals(actual.predicate)||formal.arguments.size()!=actual.arguments.size())return false;
        for(int i=0;i<formal.arguments.size();++i) {
            work.run();Term a=formal.arguments.get(i),b=actual.arguments.get(i);
            if(a.variable>=0){Term prior=assignment.putIfAbsent(a.variable,b);if(prior!=null&&!rigid(prior,b))return false;}
            else if(!rigid(a,b))return false;
        }
        return true;
    }
    private static boolean instance(List<Literal> formal,List<Literal> actual,int index,boolean[] used,Map<Integer,Term> assignment,Runnable work) {
        work.run();if(index==formal.size()){for(boolean covered:used)if(!covered)return false;return true;}
        for(int i=0;i<actual.size();++i) {
            work.run();Map<Integer,Term> next=new HashMap<>(assignment);if(!instanceLiteral(formal.get(index),actual.get(i),next,work))continue;
            boolean prior=used[i];used[i]=true;if(instance(formal,actual,index+1,used,next,work))return true;used[i]=prior;
        }
        return false;
    }
    static boolean instance(Residual formal,Residual actual,Runnable work) {
        if(!actual.variables.isEmpty())throw new IllegalStateException("Concrete native producer event required");
        return formal.literals.size()>=actual.literals.size()&&instance(formal.literals,actual.literals,0,new boolean[actual.literals.size()],new HashMap<>(),work);
    }

    static Residual fact(ContextWitnessProjection.Literal literal,Map<ContextWitnessUnaryChainVerifier.Root,Origin> carried,Runnable work) {
        return new Residual(Collections.singletonList(eventLiteral(literal,false,carried,work)),work);
    }
    static Residual fact(Fact fact,Runnable work) {
        List<Term> terms=new ArrayList<>();for(SemanticTermSnapshot value:fact.arguments){work.run();terms.add(new Term(-1,null,value));}
        return new Residual(Collections.singletonList(new Literal(fact.predicate,fact.positive,terms)),work);
    }
    private static void assignments(List<Literal> formal,List<Literal> actual,int index,boolean[] used,Map<Integer,Term> assignment,
            List<Map<Integer,Term>> output,Runnable work) {
        work.run();if(index==formal.size()) {
            for(boolean covered:used)if(!covered)return;
            output.add(Collections.unmodifiableMap(new LinkedHashMap<>(assignment)));return;
        }
        for(int i=0;i<actual.size();++i) {
            work.run();Map<Integer,Term> next=new LinkedHashMap<>(assignment);
            if(!instanceLiteral(formal.get(index),actual.get(i),next,work))continue;
            boolean prior=used[i];used[i]=true;assignments(formal,actual,index+1,used,next,output,work);used[i]=prior;
        }
    }
    /** All consistent surjective instance bindings, including coincident formal premises. */
    static List<Map<Integer,Term>> assignments(Residual formal,Residual actual,Runnable work) {
        if(!actual.variables.isEmpty())throw new IllegalStateException("Concrete typed instance required");
        List<Map<Integer,Term>> out=new ArrayList<>();
        if(formal.literals.size()>=actual.literals.size())assignments(formal.literals,actual.literals,0,new boolean[actual.literals.size()],new LinkedHashMap<>(),out,work);
        return Collections.unmodifiableList(out);
    }

}
