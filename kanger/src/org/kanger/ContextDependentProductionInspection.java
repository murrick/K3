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
        public final long rule;public final int retainedLiteral;
        public final List<Donor> donors;
        public Step(long rule,List<Donor> donors){this(rule,-1,donors);}
        public Step(long rule,int retainedLiteral,List<Donor> donors){if(rule<0||retainedLiteral< -1||donors==null)throw new IllegalArgumentException("Whole clause and exact retained literal required");List<Donor> copy=new ArrayList<>(donors);if(copy.contains(null))throw new IllegalArgumentException("Exact body donors required");this.rule=rule;this.retainedLiteral=retainedLiteral;this.donors=Collections.unmodifiableList(copy);}
    }
    public static final class Request {
        public final Policy policy;
        public final int capacity;
        public final List<Step> steps;public final boolean signed;
        public Request(Policy policy,int capacity,List<Step> steps){this(policy,capacity,steps,false);}
        public Request(Policy policy,int capacity,List<Step> steps,boolean signed){if(policy==null||capacity<1||capacity>100000||steps==null)throw new IllegalArgumentException("Bounded production candidate required");List<Step> copy=new ArrayList<>(steps);if(copy.size()>policy.steps||copy.contains(null))throw new IllegalArgumentException("Bounded complete production recipe required");this.policy=policy;this.capacity=capacity;this.steps=Collections.unmodifiableList(copy);boolean mode=signed;for(Step step:copy)mode|=step.retainedLiteral>=0;this.signed=mode;}
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
        public final int depth,retainedLiteral;public final boolean positive;
        private Row(Unit unit,Views views)throws Exception{positive=unit.positive;retainedLiteral=unit.retained;rule=unit.rule;predicate=unit.atom.predicate;List<TermView> args=new ArrayList<>();for(Node node:unit.atom.arguments)args.add(views.take(node));arguments=Collections.unmodifiableList(args);sources=unit.sources;depth=unit.depth;}
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
        public final String auditDigest;public final boolean signed;
        private Result(Candidate candidate,String digest)throws Exception {
            signed=candidate.request.signed;
            Views views=new Views(candidate);List<Row> rows=new ArrayList<>();for(int i=0;i<candidate.products.size();i++)if(candidate.visibleProducts==null||candidate.visibleProducts.contains(i))rows.add(new Row(candidate.products.get(i),views));products=Collections.unmodifiableList(rows);
            originalUnitRefutations=refutations(candidate.originalConflicts);finalUnitRefutations=refutations(candidate.finalConflicts);originals=Collections.unmodifiableSet(new LinkedHashSet<>(candidate.sources.values()));auditDigest=digest;
        }
        private static List<Refutation> refutations(List<Conflict> conflicts){List<Refutation> out=new ArrayList<>();for(Conflict conflict:conflicts)out.add(new Refutation(conflict));return Collections.unmodifiableList(out);}
    }
    private static Set<Source> frozen(Collection<Source> sources){return Collections.unmodifiableSet(new LinkedHashSet<>(sources));}
    private static final class Unit {
        final Source rule;final Atom atom;final Set<Source> sources;final int depth,retained;final List<Atom> donors;final boolean positive;final List<Boolean> signs;
        Unit(Source rule,Atom atom,Collection<Source> sources,int depth,List<Atom> donors){this(rule,atom,sources,depth,donors,true,-1,Collections.nCopies(donors.size(),true));}
        Unit(Source rule,Atom atom,Collection<Source> sources,int depth,List<Atom> donors,boolean positive,int retained,List<Boolean> signs){this.rule=rule;this.atom=atom;this.sources=frozen(sources);this.depth=depth;this.positive=positive;this.retained=retained;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));this.signs=Collections.unmodifiableList(new ArrayList<>(signs));}
    }
    private static final class Entry {
        final Rule rule;final Mind owner;final long atomKey;final Source source;final Root root;final Clause clause;
        Entry(Rule rule,Mind owner,long atomKey,Source source,Root root,Clause clause){this.rule=rule;this.owner=owner;this.atomKey=atomKey;this.source=source;this.root=root;this.clause=clause;}
    }
    private static final class Pattern {
        final String predicate;final List<Node> arguments;final boolean positive;
        Pattern(String predicate,List<Node> arguments){this(predicate,arguments,false);}
        Pattern(String predicate,List<Node> arguments,boolean positive){this.predicate=predicate;this.arguments=arguments;this.positive=positive;}
    }
    private static final class DomainDescriptor {final String predicate;final int arity;DomainDescriptor(String predicate,int arity){this.predicate=predicate;this.arity=arity;}}
    private static final class Instance {
        final Pattern head;final List<Pattern> body;
        Instance(Pattern head,List<Pattern> body){this.head=head;this.body=body;}
    }
    private static final class Conflict {
        final Source constraint;final Set<Source> sources;final int depth;final List<Atom> donors;final List<Boolean> signs;
        Conflict(Source constraint,Collection<Source> sources,int depth,List<Atom> donors){this(constraint,sources,depth,donors,Collections.nCopies(donors.size(),true));}
        Conflict(Source constraint,Collection<Source> sources,int depth,List<Atom> donors,List<Boolean> signs){this.constraint=constraint;this.sources=frozen(sources);this.depth=depth;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));this.signs=Collections.unmodifiableList(new ArrayList<>(signs));}
    }
    private static final class State {
        final List<Pattern> body;final List<Atom> donors;final Set<Source> sources;final int depth;final List<Boolean> signs;
        State(List<Pattern> body,List<Atom> donors,Collection<Source> sources,int depth){this(body,donors,sources,depth,Collections.nCopies(donors.size(),true));}
        State(List<Pattern> body,List<Atom> donors,Collection<Source> sources,int depth,List<Boolean> signs){this.body=body;this.donors=donors;this.sources=frozen(sources);this.depth=depth;this.signs=signs;}
    }
    private static final class AuditBytes extends ByteArrayOutputStream {
        final int maximum;AuditBytes(int maximum){super(Math.min(1024,maximum));this.maximum=maximum;}
        private void room(int n){if(n<0||n>maximum-count)throw new IllegalStateException("Production audit byte limit");}
        @Override public synchronized void write(int value){room(1);super.write(value);}
        @Override public synchronized void write(byte[] bytes,int offset,int length){room(length);super.write(bytes,offset,length);}
    }
    /** Capture-time original occurrence manifest. It is historical data, not provider authority. */
    static final class Originals {
        final UUID context;final long revision;final boolean localX=true;final String fingerprint;final Map<Long,String> rows;
        Originals(UUID context,long revision,String fingerprint,Map<Long,String> rows) {
            if(context==null||revision<0||fingerprint==null||!fingerprint.matches("[0-9a-f]{64}")||rows==null)throw new IllegalArgumentException("Exact historical original manifest required");
            long previous=-1;for(Map.Entry<Long,String> row:rows.entrySet()){if(row.getKey()==null||row.getKey()<=previous||row.getValue()==null||!row.getValue().startsWith("!"))throw new IllegalArgumentException("Ordered whole assertion occurrences required");previous=row.getKey();}
            this.context=context;this.revision=revision;this.fingerprint=fingerprint;this.rows=Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        }
        static Originals of(ContextProductionInventory inventory) {
            if(inventory==null||!inventory.localX||!inventory.initialization.isEmpty()||!inventory.dependentPrimaryView())throw new IllegalArgumentException("Whole primary-only original inventory required");
            Map<Long,String> rows=new LinkedHashMap<>();for(ContextProductionInventory.Row row:inventory.primary)rows.put(row.nativeRule,row.origin);return new Originals(inventory.context,inventory.revision,inventory.fingerprint,rows);
        }
    }
    private static Projection rebuild(Originals originals,Policy policy)throws Exception {
        if(originals.rows.size()>policy.rows)throw new IllegalStateException("Original replay row limit");Runnable work=ContextGroundProductionRounds.budget(policy.work);User user=new User();Mind mind=new Mind(user);user.setCurrentMind(mind);Map<Long,Long> mapping=new LinkedHashMap<>();
        for(Map.Entry<Long,String> row:originals.rows.entrySet()){work.run();if(row.getValue().length()>policy.bytes)throw new IllegalStateException("Original replay text limit");Rule copy=(Rule)mind.compileLine(row.getValue(),false,new LinkedList<org.kanger.interfaces.ITerm>());if(copy==null||!row.getValue().equals(copy.getOrigin()))throw new IllegalStateException("Missing exact whole rebuilt original");mapping.put(row.getKey(),copy.getId());}
        return new Projection(mind,mapping);
    }
    static Result reconstruct(Originals originals,Request request)throws Exception {
        if(originals==null||request==null)throw new IllegalArgumentException("Bounded historical production replay required");
        try(Candidate candidate=new Candidate(rebuild(originals,request.policy),originals,request)) {
            candidate.run();String digest=candidate.digest();
            try(Candidate audit=new Candidate(rebuild(originals,request.policy),originals,request)){audit.run();if(!digest.equals(audit.digest()))throw new IllegalStateException("Different historical original-only reconstruction");}
            candidate.current();return new Result(candidate,digest);
        }
    }
    static final class CutPart {
        final Projection projection;final ContextProductionInventory original;
        CutPart(Projection projection,ContextProductionInventory original){this.projection=projection;this.original=original;}
    }
    static Result inspectCut(List<CutPart> first,List<CutPart> second,String cut,Request request)throws Exception {
        if(first==null||second==null||first.isEmpty()||first.size()!=second.size()||cut==null)throw new IllegalArgumentException("Complete cut projections required");
        try(Candidate candidate=new Candidate(first,cut,request)) {
            candidate.run();String digest=candidate.digest();
            try(Candidate audit=new Candidate(second,cut,request)){audit.run();if(!digest.equals(audit.digest()))throw new IllegalStateException("Different independently reconstructed physical cut");}
            candidate.current();return new Result(candidate,digest);
        }
    }
    /** Rebuild all proof ancestors, but qualify only exact receiving originals and visible units. */
    static Result inspectTransport(List<CutPart> first,List<CutPart> second,String cut,Request history,Set<Long> active,Set<Integer> visible)throws Exception {
        return inspectTransport(first,second,cut,history,active,visible,()->{});
    }
    static Result inspectTransport(List<CutPart> first,List<CutPart> second,String cut,Request history,Set<Long> active,Set<Integer> visible,Runnable chargeWork)throws Exception {
        try(Candidate candidate=new Candidate(first,cut,history,chargeWork)) {
            candidate.transport(active,visible);String digest=candidate.digest();
            try(Candidate audit=new Candidate(second,cut,history,chargeWork)){audit.transport(active,visible);if(!digest.equals(audit.digest()))throw new IllegalStateException("Different independently rebuilt round transport");}
            candidate.current();return new Result(candidate,digest);
        }
    }
    static List<Step> discoverTransport(List<CutPart> first,List<CutPart> second,String cut,Request history,Set<Long> active,Set<Integer> visible)throws Exception {
        try(Candidate candidate=new Candidate(first,cut,history)) {
            List<Step> found=candidate.discover(active,visible);String digest=candidate.digest();
            try(Candidate audit=new Candidate(second,cut,history)){List<Step> other=audit.discover(active,visible);if(!sameSteps(found,other)||!digest.equals(audit.digest()))throw new IllegalStateException("Different independent finite-pool coverage");}
            candidate.current();return Collections.unmodifiableList(found);
        }
    }
    private static boolean sameSteps(List<Step> a,List<Step> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++){Step x=a.get(i),y=b.get(i);if(x.rule!=y.rule||x.retainedLiteral!=y.retainedLiteral||x.donors.size()!=y.donors.size())return false;for(int j=0;j<x.donors.size();j++){Donor p=x.donors.get(j),q=y.donors.get(j);if(p.original!=q.original||p.reference!=q.reference)return false;}}return true;}
    private static final class Search {
        final List<Pattern> body;final List<Donor> selected;
        Search(List<Pattern> body,List<Donor> selected){this.body=body;this.selected=selected;}
    }
    private static final class Candidate implements AutoCloseable {
        final Projection projection;final ContextProductionInventory inventory;final Originals original;final Request request;final Arena arena;final Runnable work;
        final List<CutPart> parts=new ArrayList<>();final List<ContextProductionInventory> guards=new ArrayList<>();String cutIdentity;Set<Long> activeOriginals;Set<Integer> visibleProducts;
        final Map<Long,Entry> entries=new LinkedHashMap<>();final Map<Long,Source> sources=new LinkedHashMap<>();final Map<Long,Integer> ordinals=new LinkedHashMap<>();
        final List<Unit> products=new ArrayList<>();List<Conflict> originalConflicts,finalConflicts;
        Candidate(Projection projection,Originals expected,Request request)throws Exception {
            this.projection=projection;this.original=expected;this.request=request;work=ContextGroundProductionRounds.budget(request.policy.work);
            inventory=ContextProductionInventory.inspect(projection.mind,expected.context,expected.revision,true,Collections.emptyList(),request.policy.rows);
            if(inventory.rows.size()!=expected.rows.size()||inventory.primary.size()!=inventory.rows.size())throw new IllegalStateException("Different whole production projection, including auxiliary rows");
            arena=new Arena(projection.mind,request.policy.work,request.policy.nodes);
            try {
                for(Map.Entry<Long,String> row:expected.rows.entrySet()) {
                    work.run();Long id=projection.mapping.get(row.getKey());if(id==null)throw new IllegalStateException("Missing original production mapping");Rule rule=(Rule)projection.mind.getRules().get(id);
                    Source source=new Source(expected.context,expected.revision,row.getKey(),true,"inventory:"+expected.fingerprint);sources.put(id,source);ordinals.put(id,ordinals.size());
                    Root root=null;Clause clause=null;if(NativeDependentProductionRows.unit(rule))root=arena.capture(rule);else clause=NativeDependentProductionRows.ordinary(rule,projection.mind,work);
                    entries.put(row.getKey(),new Entry(rule,projection.mind,rule.getId(),source,root,clause));
                }
                current();
            }catch(Exception failure){arena.close();throw failure;}
        }
        Candidate(List<CutPart> parts,String cut,Request request)throws Exception {
            this(parts,cut,request,()->{});
        }
        Candidate(List<CutPart> parts,String cut,Request request,Runnable chargeWork)throws Exception {
            this.projection=parts.get(0).projection;this.original=Originals.of(parts.get(0).original);this.request=request;Runnable localWork=ContextGroundProductionRounds.budget(request.policy.work);
            work=()->{localWork.run();chargeWork.run();};cutIdentity=cut;
            this.parts.addAll(parts);inventory=ContextProductionInventory.inspect(projection.mind,original.context,original.revision,true,Collections.emptyList(),request.policy.rows);arena=new Arena(projection.mind,request.policy.work,request.policy.nodes,chargeWork);
            try {
                long key=0;Set<Source> distinct=new HashSet<>();
                for(CutPart part:parts) {
                    work.run();ContextProductionInventory nativeView=ContextProductionInventory.inspect(part.projection.mind,part.original.context,part.original.revision,part.original.localX,Collections.emptyList(),request.policy.rows);
                    if(nativeView.rows.size()!=part.original.primary.size()||nativeView.primary.size()!=nativeView.rows.size())throw new IllegalStateException("Different complete physical cut projection");guards.add(nativeView);
                    for(ContextProductionInventory.Row row:part.original.primary){work.run();Long id=part.projection.mapping.get(row.nativeRule);if(id==null)throw new IllegalStateException("Missing physical original mapping");Rule rule=(Rule)part.projection.mind.getRules().get(id);
                        Source source=new Source(part.original.context,part.original.revision,row.nativeRule,part.original.localX,"inventory:"+part.original.fingerprint);if(!distinct.add(source))throw new IllegalStateException("Repeated physical original");
                        sources.put(key,source);ordinals.put(key,ordinals.size());Root root=null;Clause clause=null;if(NativeDependentProductionRows.unit(rule))root=arena.captureFrom(part.projection.mind,rule,key);else clause=NativeDependentProductionRows.ordinary(rule,part.projection.mind,work);
                        entries.put(key,new Entry(rule,part.projection.mind,key,source,root,clause));key++;
                    }
                }
                current();
            }catch(Exception failure){arena.close();throw failure;}
        }
        void current()throws Exception {
            work.run();if(cutIdentity==null){ContextProductionInventory now=ContextProductionInventory.inspect(projection.mind,inventory.context,inventory.revision,true,Collections.emptyList(),request.policy.rows);if(!inventory.fingerprint.equals(now.fingerprint))throw new IllegalStateException("Changed private production originals");}
            else for(int i=0;i<parts.size();i++){work.run();ContextProductionInventory old=guards.get(i),now=ContextProductionInventory.inspect(parts.get(i).projection.mind,old.context,old.revision,old.localX,Collections.emptyList(),request.policy.rows);if(!old.fingerprint.equals(now.fingerprint))throw new IllegalStateException("Changed private physical cut originals");}
        }
        Instance instantiate(Entry entry)throws Exception {
            if(entry.clause==null)throw new IllegalStateException("Ordinary Horn or headless receiver required");Map<Integer,Node> variables=new HashMap<>();Pattern head=null;List<Pattern> body=new ArrayList<>();
            for(ContextProductionCapture.Atom atom:entry.clause.literals){work.run();List<Node> args=new ArrayList<>();for(Slot slot:atom.arguments){work.run();Node value;if(slot.variable<0)value=arena.value(slot.value);else{value=variables.get(slot.variable);if(value==null){value=arena.variable();variables.put(slot.variable,value);}}args.add(value);}Pattern pattern=new Pattern(atom.predicate,args,atom.positive);if(atom.positive){if(head!=null)throw new IllegalStateException("Multiple Horn heads");head=pattern;}else body.add(pattern);}
            if(body.size()>request.policy.steps)throw new IllegalStateException("Constraint/body step limit");return new Instance(head,body);
        }
        Atom fresh(Atom atom)throws Exception {
            Set<Node> seen=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());Deque<Node> pending=new ArrayDeque<>(atom.arguments);List<Node> from=new ArrayList<>(),to=new ArrayList<>();
            while(!pending.isEmpty()){work.run();Node node=pending.pop();if(!seen.add(node))continue;if(node.kind==NativeDependentWitnessTerms.Kind.VARIABLE){from.add(node);to.add(arena.variable());}else for(Node child:node.arguments){work.run();pending.push(child);}}
            Substitution rename=arena.unify(from,to);if(rename==null)throw new IllegalStateException("Unit standardization failed");return arena.apply(atom,rename);
        }
        Unit donor(Donor donor)throws Exception {
            work.run();if(donor.original){Entry entry=entries.get(donor.reference);if(entry==null)throw new IllegalStateException("Original unit donor required");if(entry.root!=null)return new Unit(entry.source,arena.project(entry.root),Collections.singleton(entry.source),0,Collections.emptyList());
                if(!request.signed||entry.clause.literals.size()!=1)throw new IllegalStateException("Original positive unit donor required");Pattern pattern=all(entry).get(0);return new Unit(entry.source,arena.shapeFrom(entry.owner,entry.rule,entry.atomKey,pattern.predicate,pattern.arguments),Collections.singleton(entry.source),0,Collections.emptyList(),pattern.positive,0,Collections.emptyList());}
            if(donor.reference>=products.size())throw new IllegalStateException("Only earlier products may be donors");Unit old=products.get((int)donor.reference);return new Unit(old.rule,fresh(old.atom),old.sources,old.depth,Collections.emptyList(),old.positive,old.retained,Collections.emptyList());
        }
        Pattern apply(Pattern pattern,Substitution substitution)throws Exception{return pattern==null?null:new Pattern(pattern.predicate,arena.apply(pattern.arguments,substitution),pattern.positive);}
        List<Pattern> apply(List<Pattern> patterns,int start,Substitution substitution)throws Exception{List<Pattern> out=new ArrayList<>();for(int i=start;i<patterns.size();i++){work.run();out.add(apply(patterns.get(i),substitution));}return out;}
        List<Atom> appliedDonors(List<Atom> donors,Atom next,Substitution substitution)throws Exception{List<Atom> out=new ArrayList<>();for(Atom atom:donors){work.run();out.add(arena.apply(atom,substitution));}out.add(arena.apply(next,substitution));return out;}
        Substitution match(Atom donor,Pattern pattern)throws Exception{if(!donor.predicate.equals(pattern.predicate)||donor.arguments.size()!=pattern.arguments.size())return null;return arena.unify(donor.arguments,pattern.arguments);}
        void produce(Step step)throws Exception {
            if(step.retainedLiteral>=0){produceResidual(step);return;}
            current();Entry entry=entries.get(step.rule);if(entry==null||entry.clause==null)throw new IllegalStateException("Whole ordinary Horn producer required");Instance instance=instantiate(entry);
            if(instance.head==null||instance.body.isEmpty()||instance.body.size()!=step.donors.size())throw new IllegalStateException("Exact complete Horn body required");
            Pattern head=instance.head;List<Pattern> body=instance.body;List<Atom> used=new ArrayList<>();Set<Source> support=new LinkedHashSet<>();support.add(entry.source);int depth=0;
            for(Donor reference:step.donors){work.run();Unit donor=donor(reference);if(!donor.positive)throw new IllegalStateException("Positive Horn body donor required");Substitution substitution=match(donor.atom,body.get(0));if(substitution==null)throw new IllegalStateException("Supplied Horn body donor does not unify");used=appliedDonors(used,donor.atom,substitution);head=apply(head,substitution);body=apply(body,1,substitution);support.addAll(donor.sources);depth=Math.max(depth,donor.depth);}
            if(depth>=request.policy.steps)throw new IllegalStateException("Production depth limit");Unit result=new Unit(entry.source,arena.shapeFrom(entry.owner,entry.rule,entry.atomKey,head.predicate,head.arguments),support,depth+1,used);current();products.add(result);
        }
        List<Conflict> constraints(boolean finalPool)throws Exception {
            if(request.signed)return signedConstraints(finalPool);
            List<Donor> pool=new ArrayList<>();for(Map.Entry<Long,Entry> entry:entries.entrySet())if(entry.getValue().root!=null)pool.add(Donor.original(entry.getKey()));if(finalPool)for(int i=0;i<products.size();i++)pool.add(Donor.product(i));
            List<Conflict> conflicts=new ArrayList<>();int states=0;
            for(Entry constraint:entries.values())if(constraint.clause!=null){Instance instance=instantiate(constraint);if(instance.head!=null)continue;Deque<State> pending=new ArrayDeque<>();pending.push(new State(instance.body,Collections.emptyList(),Collections.singleton(constraint.source),0));
                while(!pending.isEmpty()){
                    work.run();if(++states>request.capacity)throw new IllegalStateException("Complete constraint search state limit");State state=pending.pop();
                    if(state.body.isEmpty()){if(conflicts.size()==request.capacity)throw new IllegalStateException("Constraint proof capacity");if(state.depth>=request.policy.steps)throw new IllegalStateException("Refutation depth limit");conflicts.add(new Conflict(constraint.source,state.sources,state.depth+1,state.donors));continue;}
                    Pattern first=state.body.get(0);
                    for(Donor reference:pool){work.run();String predicate;int arity;if(reference.original){Entry e=entries.get(reference.reference);predicate=e.rule.getDomain().getPredicate(e.owner).getName(e.owner);arity=e.rule.getDomain().getRange();}else{Atom a=products.get((int)reference.reference).atom;predicate=a.predicate;arity=a.arguments.size();}if(!predicate.equals(first.predicate)||arity!=first.arguments.size())continue;
                        Unit unit=donor(reference);Substitution substitution=match(unit.atom,first);if(substitution==null)continue;Set<Source> support=new LinkedHashSet<>(state.sources);support.addAll(unit.sources);
                        if(pending.size()==request.capacity)throw new IllegalStateException("Constraint frontier limit");pending.push(new State(apply(state.body,1,substitution),appliedDonors(state.donors,unit.atom,substitution),support,Math.max(state.depth,unit.depth)));
                    }
                }
            }
            current();return conflicts;
        }
        List<Pattern> all(Entry entry)throws Exception {
            if(entry.root!=null){Atom atom=arena.project(entry.root);return Collections.singletonList(new Pattern(atom.predicate,atom.arguments,true));}
            Map<Integer,Node> variables=new HashMap<>();List<Pattern> out=new ArrayList<>();
            for(ContextProductionCapture.Atom atom:entry.clause.literals){work.run();List<Node> args=new ArrayList<>();for(Slot slot:atom.arguments){work.run();Node node;if(slot.variable<0)node=arena.value(slot.value);else{node=variables.get(slot.variable);if(node==null){node=arena.variable();variables.put(slot.variable,node);}}args.add(node);}out.add(new Pattern(atom.predicate,args,atom.positive));}
            if(out.size()>request.policy.steps)throw new IllegalStateException("Signed clause width limit");return out;
        }
        List<Boolean> signs(List<Boolean> previous,boolean next){List<Boolean> out=new ArrayList<>(previous);out.add(next);return out;}
        Substitution opposite(Unit unit,Pattern literal)throws Exception{return unit.positive==literal.positive?null:match(unit.atom,literal);}
        void produceResidual(Step step)throws Exception {
            current();Entry entry=entries.get(step.rule);if(entry==null||entry.clause==null)throw new IllegalStateException("Whole ordinary clause required for residual production");List<Pattern> clause=all(entry);
            if(step.retainedLiteral>=clause.size()||step.donors.size()!=clause.size()-1)throw new IllegalStateException("Exact retained literal and all other pivots required");Pattern head=clause.get(step.retainedLiteral);List<Pattern> body=new ArrayList<>(clause);body.remove(step.retainedLiteral);
            Set<Source> support=new LinkedHashSet<>();support.add(entry.source);List<Atom> used=new ArrayList<>();List<Boolean> signs=new ArrayList<>();int depth=0;
            for(Donor reference:step.donors){work.run();Unit unit=donor(reference);Substitution substitution=opposite(unit,body.get(0));if(substitution==null)throw new IllegalStateException("Residual donor must match opposite polarity");head=apply(head,substitution);body=apply(body,1,substitution);used=appliedDonors(used,unit.atom,substitution);signs.add(unit.positive);support.addAll(unit.sources);depth=Math.max(depth,unit.depth);}
            if(depth>=request.policy.steps)throw new IllegalStateException("Signed production depth limit");Unit result=new Unit(entry.source,arena.shapeFrom(entry.owner,entry.rule,entry.atomKey,head.predicate,head.arguments),support,depth+1,used,head.positive,step.retainedLiteral,signs);current();products.add(result);
        }
        boolean positive(Donor ref){if(!ref.original)return products.get((int)ref.reference).positive;Entry entry=entries.get(ref.reference);return entry.root!=null||entry.clause.literals.get(0).positive;}
        List<Conflict> signedConstraints(boolean finalPool)throws Exception {
            List<Donor> pool=new ArrayList<>();for(Map.Entry<Long,Entry> e:entries.entrySet())if((activeOriginals==null||activeOriginals.contains(e.getKey()))&&(e.getValue().root!=null||e.getValue().clause.literals.size()==1))pool.add(Donor.original(e.getKey()));if(finalPool)for(int i=0;i<products.size();i++)if(visibleProducts==null||visibleProducts.contains(i))pool.add(Donor.product(i));List<Conflict> conflicts=new ArrayList<>();int states=0;
            for(Entry entry:entries.values()) {
                if(activeOriginals!=null&&!activeOriginals.contains(entry.atomKey))continue;
                Deque<State> pending=new ArrayDeque<>();pending.push(new State(all(entry),Collections.emptyList(),Collections.singleton(entry.source),0));
                while(!pending.isEmpty()) {
                    work.run();if(++states>request.capacity)throw new IllegalStateException("Signed qualification state limit");State state=pending.pop();
                    if(state.body.isEmpty()){if(conflicts.size()==request.capacity||state.depth>=request.policy.steps)throw new IllegalStateException("Signed qualification proof/depth limit");conflicts.add(new Conflict(entry.source,state.sources,state.depth+1,state.donors,state.signs));continue;}
                    Pattern first=state.body.get(0);
                    for(Donor ref:pool){work.run();if(positive(ref)==first.positive)continue;String predicate;int arity;if(ref.original){Entry e=entries.get(ref.reference);DomainDescriptor descriptor=descriptor(e);predicate=descriptor.predicate;arity=descriptor.arity;}else{Atom atom=products.get((int)ref.reference).atom;predicate=atom.predicate;arity=atom.arguments.size();}if(!predicate.equals(first.predicate)||arity!=first.arguments.size())continue;
                        Unit unit=donor(ref);Substitution substitution=opposite(unit,first);if(substitution==null)continue;Set<Source> support=new LinkedHashSet<>(state.sources);support.addAll(unit.sources);if(pending.size()==request.capacity)throw new IllegalStateException("Signed qualification frontier limit");pending.push(new State(apply(state.body,1,substitution),appliedDonors(state.donors,unit.atom,substitution),support,Math.max(state.depth,unit.depth),signs(state.signs,unit.positive)));
                    }
                }
            }
            // Two imported consequences can oppose without either producer clause being in this cut.
            if(finalPool&&visibleProducts!=null)for(int i:visibleProducts)for(int j:visibleProducts)if(i<j){work.run();Unit a=products.get(i),b=donor(Donor.product(j));if(a.positive==b.positive)continue;if(!a.atom.predicate.equals(b.atom.predicate)||a.atom.arguments.size()!=b.atom.arguments.size())continue;Substitution match=arena.unify(a.atom.arguments,b.atom.arguments);if(match==null)continue;int depth=Math.max(a.depth,b.depth);if(conflicts.size()==request.capacity||depth>=request.policy.steps)throw new IllegalStateException("Transport opposition proof/depth limit");Set<Source> support=new LinkedHashSet<>(a.sources);support.addAll(b.sources);conflicts.add(new Conflict(a.rule,support,depth+1,Arrays.asList(arena.apply(a.atom,match),arena.apply(b.atom,match)),Arrays.asList(a.positive,b.positive)));}
            current();return conflicts;
        }
        List<Step> discover(Set<Long> active,Set<Integer> visible)throws Exception {
            if(!request.signed||active==null||visible==null||!entries.keySet().containsAll(active))throw new IllegalArgumentException("Exact signed discovery scope required");activeOriginals=new LinkedHashSet<>(active);visibleProducts=new LinkedHashSet<>(visible);
            for(int i:visibleProducts)if(i<0||i>=request.steps.size())throw new IllegalArgumentException("Missing input proof");for(Step step:request.steps)produce(step);
            List<Donor> pool=new ArrayList<>();for(long key:activeOriginals){Entry e=entries.get(key);if(e.root!=null||e.clause.literals.size()==1)pool.add(Donor.original(key));}for(int i:visibleProducts)pool.add(Donor.product(i));
            List<Step> found=new ArrayList<>();int states=0;
            for(long key:activeOriginals){Entry e=entries.get(key);if(e.clause==null||e.clause.literals.size()<2)continue;
                for(int retained=0;retained<e.clause.literals.size();retained++){
                    List<Pattern> body=new ArrayList<>(all(e));body.remove(retained);Deque<Search> pending=new ArrayDeque<>();pending.push(new Search(body,Collections.emptyList()));
                    while(!pending.isEmpty()){
                        work.run();if(++states>request.capacity)throw new IllegalStateException("Discovery state limit");Search state=pending.pop();
                        if(state.body.isEmpty()){if(found.size()==request.capacity||request.steps.size()+found.size()>=request.policy.steps)throw new IllegalStateException("Complete discovery recipe limit");found.add(new Step(key,retained,state.selected));continue;}
                        Pattern first=state.body.get(0);
                        for(Donor reference:pool){work.run();if(positive(reference)==first.positive)continue;String predicate;int arity;if(reference.original){DomainDescriptor d=descriptor(entries.get(reference.reference));predicate=d.predicate;arity=d.arity;}else{Atom atom=products.get((int)reference.reference).atom;predicate=atom.predicate;arity=atom.arguments.size();}if(!predicate.equals(first.predicate)||arity!=first.arguments.size())continue;
                            Unit unit=donor(reference);Substitution substitution=opposite(unit,first);if(substitution==null)continue;List<Donor> selected=new ArrayList<>(state.selected);selected.add(reference);if(pending.size()==request.capacity)throw new IllegalStateException("Discovery frontier limit");pending.push(new Search(apply(state.body,1,substitution),selected));
                        }
                    }
                }
            }
            // Replay every discovered whole recipe, independently of the search's partial substitutions.
            for(Step step:found)produce(step);originalConflicts=Collections.emptyList();finalConflicts=Collections.emptyList();current();return found;
        }
        void transport(Set<Long> active,Set<Integer> visible)throws Exception {
            if(!request.signed||active==null||visible==null||!entries.keySet().containsAll(active))throw new IllegalArgumentException("Exact signed receiving scope required");activeOriginals=new LinkedHashSet<>(active);visibleProducts=new LinkedHashSet<>(visible);
            for(int index:visibleProducts)if(index<0||index>=request.steps.size())throw new IllegalArgumentException("Missing transported proof");
            originalConflicts=constraints(false);for(Step step:request.steps)produce(step);finalConflicts=constraints(true);current();
        }
        DomainDescriptor descriptor(Entry entry)throws Exception{return new DomainDescriptor(entry.rule.getDomain().getPredicate(entry.owner).getName(entry.owner),entry.rule.getDomain().getRange());}
        void run()throws Exception{originalConflicts=constraints(false);for(Step step:request.steps)produce(step);finalConflicts=constraints(true);current();}
        private long sourceKey(Source source){if(cutIdentity==null)return source.rule;for(Map.Entry<Long,Source> entry:sources.entrySet())if(entry.getValue().equals(source))return entry.getKey();throw new IllegalStateException("Missing qualified Source");}
        private void support(DataOutputStream out,Set<Source> support)throws Exception{List<Long> ids=new ArrayList<>();for(Source source:support){work.run();ids.add(sourceKey(source));}Collections.sort(ids);out.writeInt(ids.size());for(long id:ids)out.writeLong(id);}
        String digest()throws Exception {
            List<Atom> atoms=new ArrayList<>();AuditBytes header=new AuditBytes(request.policy.bytes);DataOutputStream out=new DataOutputStream(header);out.writeInt(request.signed?3:1);out.writeLong(original.context.getMostSignificantBits());out.writeLong(original.context.getLeastSignificantBits());out.writeLong(original.revision);out.writeBoolean(original.localX);out.writeUTF(original.fingerprint);if(cutIdentity!=null){out.writeInt(2);out.writeUTF(cutIdentity);out.writeInt(parts.size());for(CutPart part:parts){ContextProductionInventory member=part.original;out.writeUTF(member.context.toString());out.writeLong(member.revision);out.writeBoolean(member.localX);out.writeUTF(member.fingerprint);}}if(activeOriginals!=null){out.writeInt(4);out.writeInt(activeOriginals.size());for(long key:activeOriginals)out.writeLong(key);out.writeInt(visibleProducts.size());for(int index:visibleProducts)out.writeInt(index);}out.writeInt(products.size());
            for(Unit product:products){work.run();atoms.add(product.atom);atoms.addAll(product.donors);out.writeLong(sourceKey(product.rule));out.writeInt(product.depth);out.writeInt(product.donors.size());if(request.signed){out.writeBoolean(product.positive);out.writeInt(product.retained);for(boolean sign:product.signs)out.writeBoolean(sign);}support(out,product.sources);}
            for(List<Conflict> list:Arrays.asList(originalConflicts,finalConflicts)){out.writeInt(list.size());for(Conflict conflict:list){work.run();out.writeLong(sourceKey(conflict.constraint));out.writeInt(conflict.depth);out.writeInt(conflict.donors.size());if(request.signed)for(boolean sign:conflict.signs)out.writeBoolean(sign);support(out,conflict.sources);atoms.addAll(conflict.donors);}}
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
        if(expected!=null&&expected.rows.size()!=expected.primary.size())throw new IllegalStateException("Portable production replay still requires a whole primary-only inventory");
        if(actual==null||request==null)throw new IllegalArgumentException("Actual candidate and complete supplied recipe required");
        Policy policy=request.policy;ContextDependentReplayInspection.current(actual,expected,policy);
        try {
            Projection first=ContextDependentReplayInspection.project(actual,expected,policy,true);
            try(Candidate candidate=new Candidate(first,Originals.of(expected),request)) {
                candidate.run();String digest=candidate.digest();
                // Re-fetch and recompile ALL actual originals; no generated atom is accepted as a fresh axiom.
                Projection second=ContextDependentReplayInspection.project(actual,expected,policy,true);
                try(Candidate audit=new Candidate(second,Originals.of(expected),request)){audit.run();if(!digest.equals(audit.digest()))throw new IllegalStateException("Different independently reconstructed production/constraint graph");}
                candidate.current();return new Result(candidate,digest);
            }
        }finally{ContextDependentReplayInspection.current(actual,expected,policy);}
    }
}
