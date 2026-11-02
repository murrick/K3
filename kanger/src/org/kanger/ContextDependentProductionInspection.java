/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.ContextDependentReplayInspection.Projection;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.ContextProductionCapture.Slot;
import org.kanger.NativeDependentWitnessTerms.*;
import org.kanger.units.Rule;

/** Typed local candidate productions and unit/constraint checks; no legacy Term or write authority. */
public final class ContextDependentProductionInspection {
    private ContextDependentProductionInspection(){}
    public static final class Donor {
        public final boolean original;
        public final long reference;
        private Donor(boolean original,long reference){if(reference<0)throw new IllegalArgumentException("Exact donor reference required");this.original=original;this.reference=reference;}
        public static Donor original(long rule){return new Donor(true,rule);}
        public static Donor product(int index){return new Donor(false,index);}
    }
    public static final class Step {
        public final long rule;
        public final List<Donor> donors;
        public Step(long rule,List<Donor> donors){if(rule<0||donors==null)throw new IllegalArgumentException("Whole Horn source and body donors required");List<Donor> copy=new ArrayList<>(donors);if(copy.contains(null))throw new IllegalArgumentException("Exact body donors required");this.rule=rule;this.donors=Collections.unmodifiableList(copy);}
    }
    public static final class Request {
        public final Policy policy;
        public final int capacity;
        public final List<Step> steps;
        public Request(Policy policy,int capacity,List<Step> steps){if(policy==null||capacity<1||capacity>100000||steps==null)throw new IllegalArgumentException("Bounded production candidate required");List<Step> copy=new ArrayList<>(steps);if(copy.size()>policy.steps||copy.contains(null))throw new IllegalArgumentException("Bounded complete production recipe required");this.policy=policy;this.capacity=capacity;this.steps=Collections.unmodifiableList(copy);}
    }
    public enum Kind {VARIABLE,VALUE,APPLICATION}
    public static final class TermView {
        public final Kind kind;
        public final int variable,binder;
        public final SemanticTermSnapshot value;
        public final Source symbolSource;
        public final List<TermView> arguments;
        private TermView(Node node,int variable,Source source,List<TermView> arguments){kind=Kind.valueOf(node.kind.name());this.variable=variable;value=node.value;symbolSource=source;binder=node.symbol==null?-1:node.symbol.binder;this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));}
    }
    public static final class Row {
        public final Source rule;
        public final String predicate;
        public final List<TermView> arguments;
        public final Set<Source> sources;
        public final int depth;
        private Row(Unit unit,Views views)throws Exception{rule=unit.rule;predicate=unit.atom.predicate;List<TermView> args=new ArrayList<>();for(Node node:unit.atom.arguments)args.add(views.take(node));arguments=Collections.unmodifiableList(args);sources=unit.sources;depth=unit.depth;}
    }
    public static final class Refutation {
        public final Source constraint;
        public final Set<Source> sources;
        public final int depth;
        private Refutation(Conflict conflict){constraint=conflict.constraint;sources=conflict.sources;depth=conflict.depth;}
    }
    public static final class Result {
        public final List<Row> products;
        public final List<Refutation> originalUnitRefutations,finalUnitRefutations;
        public final Set<Source> originals;
        public final String auditDigest;
        private Result(Candidate candidate,String digest)throws Exception {
            Views views=new Views(candidate);List<Row> rows=new ArrayList<>();for(Unit unit:candidate.products)rows.add(new Row(unit,views));products=Collections.unmodifiableList(rows);
            originalUnitRefutations=refutations(candidate.originalConflicts);finalUnitRefutations=refutations(candidate.finalConflicts);originals=Collections.unmodifiableSet(new LinkedHashSet<>(candidate.sources.values()));auditDigest=digest;
        }
        private static List<Refutation> refutations(List<Conflict> conflicts){List<Refutation> out=new ArrayList<>();for(Conflict conflict:conflicts)out.add(new Refutation(conflict));return Collections.unmodifiableList(out);}
    }
    private static Set<Source> frozen(Collection<Source> sources){return Collections.unmodifiableSet(new LinkedHashSet<>(sources));}
    private static final class Unit {
        final Source rule;final Atom atom;final Set<Source> sources;final int depth;final List<Atom> donors;
        Unit(Source rule,Atom atom,Collection<Source> sources,int depth,List<Atom> donors){this.rule=rule;this.atom=atom;this.sources=frozen(sources);this.depth=depth;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));}
    }
    private static final class Entry {
        final Rule rule;final Source source;final Root root;final Clause clause;
        Entry(Rule rule,Source source,Root root,Clause clause){this.rule=rule;this.source=source;this.root=root;this.clause=clause;}
    }
    private static final class Pattern {
        final String predicate;final List<Node> arguments;
        Pattern(String predicate,List<Node> arguments){this.predicate=predicate;this.arguments=arguments;}
    }
    private static final class Instance {
        final Pattern head;final List<Pattern> body;
        Instance(Pattern head,List<Pattern> body){this.head=head;this.body=body;}
    }
    private static final class Conflict {
        final Source constraint;final Set<Source> sources;final int depth;final List<Atom> donors;
        Conflict(Source constraint,Collection<Source> sources,int depth,List<Atom> donors){this.constraint=constraint;this.sources=frozen(sources);this.depth=depth;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));}
    }
    private static final class State {
        final List<Pattern> body;final List<Atom> donors;final Set<Source> sources;final int depth;
        State(List<Pattern> body,List<Atom> donors,Collection<Source> sources,int depth){this.body=body;this.donors=donors;this.sources=frozen(sources);this.depth=depth;}
    }
    private static final class AuditBytes extends ByteArrayOutputStream {
        final int maximum;AuditBytes(int maximum){super(Math.min(1024,maximum));this.maximum=maximum;}
        private void room(int n){if(n<0||n>maximum-count)throw new IllegalStateException("Production audit byte limit");}
        @Override public synchronized void write(int value){room(1);super.write(value);}
        @Override public synchronized void write(byte[] bytes,int offset,int length){room(length);super.write(bytes,offset,length);}
    }
    private static final class Candidate implements AutoCloseable {
        final Projection projection;final ContextProductionInventory inventory,original;final Request request;final Arena arena;final Runnable work;
        final Map<Long,Entry> entries=new LinkedHashMap<>();final Map<Long,Source> sources=new LinkedHashMap<>();final Map<Long,Integer> ordinals=new LinkedHashMap<>();
        final List<Unit> products=new ArrayList<>();List<Conflict> originalConflicts,finalConflicts;
        Candidate(Projection projection,ContextProductionInventory expected,Request request)throws Exception {
            this.projection=projection;this.original=expected;this.request=request;work=ContextGroundProductionRounds.budget(request.policy.work);
            inventory=ContextProductionInventory.inspect(projection.mind,expected.context,expected.revision,true,Collections.emptyList(),request.policy.rows);
            if(inventory.rows.size()!=expected.rows.size()||inventory.primary.size()!=inventory.rows.size())throw new IllegalStateException("Different whole production projection, including auxiliary rows");
            arena=new Arena(projection.mind,request.policy.work,request.policy.nodes);
            try {
                for(ContextProductionInventory.Row row:expected.rows) {
                    work.run();Long id=projection.mapping.get(row.nativeRule);if(id==null)throw new IllegalStateException("Missing original production mapping");Rule rule=(Rule)projection.mind.getRules().get(id);
                    Source source=new Source(expected.context,expected.revision,row.nativeRule,true,"inventory:"+expected.fingerprint);sources.put(id,source);ordinals.put(id,ordinals.size());
                    Root root=null;Clause clause=null;if(NativeDependentProductionRows.unit(rule))root=arena.capture(rule);else clause=NativeDependentProductionRows.ordinary(rule,projection.mind,work);
                    entries.put(row.nativeRule,new Entry(rule,source,root,clause));
                }
                current();
            }catch(Exception failure){arena.close();throw failure;}
        }
        void current()throws Exception{work.run();ContextProductionInventory now=ContextProductionInventory.inspect(projection.mind,inventory.context,inventory.revision,true,Collections.emptyList(),request.policy.rows);if(!inventory.fingerprint.equals(now.fingerprint))throw new IllegalStateException("Changed private production originals");}
        Instance instantiate(Entry entry)throws Exception {
            if(entry.clause==null)throw new IllegalStateException("Ordinary Horn or headless receiver required");Map<Integer,Node> variables=new HashMap<>();Pattern head=null;List<Pattern> body=new ArrayList<>();
            for(ContextProductionCapture.Atom atom:entry.clause.literals){work.run();List<Node> args=new ArrayList<>();for(Slot slot:atom.arguments){work.run();Node value;if(slot.variable<0)value=arena.value(slot.value);else{value=variables.get(slot.variable);if(value==null){value=arena.variable();variables.put(slot.variable,value);}}args.add(value);}Pattern pattern=new Pattern(atom.predicate,args);if(atom.positive){if(head!=null)throw new IllegalStateException("Multiple Horn heads");head=pattern;}else body.add(pattern);}
            if(body.size()>request.policy.steps)throw new IllegalStateException("Constraint/body step limit");return new Instance(head,body);
        }
        Atom fresh(Atom atom)throws Exception {
            Set<Node> seen=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());Deque<Node> pending=new ArrayDeque<>(atom.arguments);List<Node> from=new ArrayList<>(),to=new ArrayList<>();
            while(!pending.isEmpty()){work.run();Node node=pending.pop();if(!seen.add(node))continue;if(node.kind==NativeDependentWitnessTerms.Kind.VARIABLE){from.add(node);to.add(arena.variable());}else for(Node child:node.arguments){work.run();pending.push(child);}}
            Substitution rename=arena.unify(from,to);if(rename==null)throw new IllegalStateException("Unit standardization failed");return arena.apply(atom,rename);
        }
        Unit donor(Donor donor)throws Exception {
            work.run();if(donor.original){Entry entry=entries.get(donor.reference);if(entry==null||entry.root==null)throw new IllegalStateException("Original positive unit donor required");return new Unit(entry.source,arena.project(entry.root),Collections.singleton(entry.source),0,Collections.emptyList());}
            if(donor.reference>=products.size())throw new IllegalStateException("Only earlier products may be donors");Unit old=products.get((int)donor.reference);return new Unit(old.rule,fresh(old.atom),old.sources,old.depth,Collections.emptyList());
        }
        Pattern apply(Pattern pattern,Substitution substitution)throws Exception{return pattern==null?null:new Pattern(pattern.predicate,arena.apply(pattern.arguments,substitution));}
        List<Pattern> apply(List<Pattern> patterns,int start,Substitution substitution)throws Exception{List<Pattern> out=new ArrayList<>();for(int i=start;i<patterns.size();i++){work.run();out.add(apply(patterns.get(i),substitution));}return out;}
        List<Atom> appliedDonors(List<Atom> donors,Atom next,Substitution substitution)throws Exception{List<Atom> out=new ArrayList<>();for(Atom atom:donors){work.run();out.add(arena.apply(atom,substitution));}out.add(arena.apply(next,substitution));return out;}
        Substitution match(Atom donor,Pattern pattern)throws Exception{if(!donor.predicate.equals(pattern.predicate)||donor.arguments.size()!=pattern.arguments.size())return null;return arena.unify(donor.arguments,pattern.arguments);}
        void produce(Step step)throws Exception {
            current();Entry entry=entries.get(step.rule);if(entry==null||entry.clause==null)throw new IllegalStateException("Whole ordinary Horn producer required");Instance instance=instantiate(entry);
            if(instance.head==null||instance.body.isEmpty()||instance.body.size()!=step.donors.size())throw new IllegalStateException("Exact complete Horn body required");
            Pattern head=instance.head;List<Pattern> body=instance.body;List<Atom> used=new ArrayList<>();Set<Source> support=new LinkedHashSet<>();support.add(entry.source);int depth=0;
            for(Donor reference:step.donors){work.run();Unit donor=donor(reference);Substitution substitution=match(donor.atom,body.get(0));if(substitution==null)throw new IllegalStateException("Supplied Horn body donor does not unify");used=appliedDonors(used,donor.atom,substitution);head=apply(head,substitution);body=apply(body,1,substitution);support.addAll(donor.sources);depth=Math.max(depth,donor.depth);}
            if(depth>=request.policy.steps)throw new IllegalStateException("Production depth limit");Unit result=new Unit(entry.source,arena.shape(entry.rule.getId(),head.predicate,head.arguments),support,depth+1,used);current();products.add(result);
        }
        List<Conflict> constraints(boolean finalPool)throws Exception {
            List<Donor> pool=new ArrayList<>();for(Map.Entry<Long,Entry> entry:entries.entrySet())if(entry.getValue().root!=null)pool.add(Donor.original(entry.getKey()));if(finalPool)for(int i=0;i<products.size();i++)pool.add(Donor.product(i));
            List<Conflict> conflicts=new ArrayList<>();int states=0;
            for(Entry constraint:entries.values())if(constraint.clause!=null){Instance instance=instantiate(constraint);if(instance.head!=null)continue;Deque<State> pending=new ArrayDeque<>();pending.push(new State(instance.body,Collections.emptyList(),Collections.singleton(constraint.source),0));
                while(!pending.isEmpty()){
                    work.run();if(++states>request.capacity)throw new IllegalStateException("Complete constraint search state limit");State state=pending.pop();
                    if(state.body.isEmpty()){if(conflicts.size()==request.capacity)throw new IllegalStateException("Constraint proof capacity");if(state.depth>=request.policy.steps)throw new IllegalStateException("Refutation depth limit");conflicts.add(new Conflict(constraint.source,state.sources,state.depth+1,state.donors));continue;}
                    Pattern first=state.body.get(0);
                    for(Donor reference:pool){work.run();String predicate;int arity;if(reference.original){Entry e=entries.get(reference.reference);predicate=e.rule.getDomain().getPredicate(projection.mind).getName(projection.mind);arity=e.rule.getDomain().getRange();}else{Atom a=products.get((int)reference.reference).atom;predicate=a.predicate;arity=a.arguments.size();}if(!predicate.equals(first.predicate)||arity!=first.arguments.size())continue;
                        Unit unit=donor(reference);Substitution substitution=match(unit.atom,first);if(substitution==null)continue;Set<Source> support=new LinkedHashSet<>(state.sources);support.addAll(unit.sources);
                        if(pending.size()==request.capacity)throw new IllegalStateException("Constraint frontier limit");pending.push(new State(apply(state.body,1,substitution),appliedDonors(state.donors,unit.atom,substitution),support,Math.max(state.depth,unit.depth)));
                    }
                }
            }
            current();return conflicts;
        }
        void run()throws Exception{originalConflicts=constraints(false);for(Step step:request.steps)produce(step);finalConflicts=constraints(true);current();}
        private void support(DataOutputStream out,Set<Source> support)throws Exception{List<Long> ids=new ArrayList<>();for(Source source:support){work.run();ids.add(source.rule);}Collections.sort(ids);out.writeInt(ids.size());for(long id:ids)out.writeLong(id);}
        String digest()throws Exception {
            List<Atom> atoms=new ArrayList<>();AuditBytes header=new AuditBytes(request.policy.bytes);DataOutputStream out=new DataOutputStream(header);out.writeInt(1);out.writeLong(original.context.getMostSignificantBits());out.writeLong(original.context.getLeastSignificantBits());out.writeLong(original.revision);out.writeBoolean(original.localX);out.writeUTF(original.fingerprint);out.writeInt(products.size());
            for(Unit product:products){work.run();atoms.add(product.atom);atoms.addAll(product.donors);out.writeLong(product.rule.rule);out.writeInt(product.depth);out.writeInt(product.donors.size());support(out,product.sources);}
            for(List<Conflict> list:Arrays.asList(originalConflicts,finalConflicts)){out.writeInt(list.size());for(Conflict conflict:list){work.run();out.writeLong(conflict.constraint.rule);out.writeInt(conflict.depth);out.writeInt(conflict.donors.size());support(out,conflict.sources);atoms.addAll(conflict.donors);}}
            out.flush();Policy p=request.policy;byte[] graph=NativeDependentReplayCapsule.atomGraph(atoms,ordinals,new NativeDependentReplayCapsule.Limits(p.bytes,p.rows,p.steps,p.work,p.nodes));if(graph.length>p.bytes-header.size())throw new IllegalStateException("Complete production audit byte limit");MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(header.toByteArray());digest.update(graph);StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
        }
        @Override public void close(){arena.close();}
    }
    private static final class Views {
        final Candidate candidate;final IdentityHashMap<Node,TermView> seen=new IdentityHashMap<>();int variables;
        Views(Candidate candidate){this.candidate=candidate;}
        TermView take(Node start)throws Exception {
            Deque<Node> pending=new ArrayDeque<>();pending.push(start);
            while(!pending.isEmpty()){candidate.work.run();Node node=pending.peek();if(seen.containsKey(node)){pending.pop();continue;}boolean ready=true;for(Node child:node.arguments){candidate.work.run();if(!seen.containsKey(child)){ready=false;pending.push(child);}}
                if(ready){List<TermView> args=new ArrayList<>();for(Node child:node.arguments)args.add(seen.get(child));Source source=node.symbol==null?null:candidate.sources.get(node.symbol.nativeRule);if(node.symbol!=null&&source==null)throw new IllegalStateException("Missing function Source");seen.put(node,new TermView(node,node.kind==NativeDependentWitnessTerms.Kind.VARIABLE?variables++:-1,source,args));pending.pop();}}
            return seen.get(start);
        }
    }
    public static Result inspect(Mind actual,ContextProductionInventory expected,Request request)throws Exception {
        if(actual==null||request==null)throw new IllegalArgumentException("Actual candidate and complete supplied recipe required");
        Policy policy=request.policy;ContextDependentReplayInspection.current(actual,expected,policy);
        try {
            Projection first=ContextDependentReplayInspection.project(actual,expected,policy,true);
            try(Candidate candidate=new Candidate(first,expected,request)) {
                candidate.run();String digest=candidate.digest();
                // Re-fetch and recompile ALL actual originals; no generated atom is accepted as a fresh axiom.
                Projection second=ContextDependentReplayInspection.project(actual,expected,policy,true);
                try(Candidate audit=new Candidate(second,expected,request)){audit.run();if(!digest.equals(audit.digest()))throw new IllegalStateException("Different independently reconstructed production/constraint graph");}
                candidate.current();return new Result(candidate,digest);
            }
        }finally{ContextDependentReplayInspection.current(actual,expected,policy);}
    }
}
