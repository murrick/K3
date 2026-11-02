/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.NativeDependentWitnessTerms.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Isolated whole-inventory matching adapter. No Linker callback or publication authority. */
final class NativeDependentMatchFrame implements AutoCloseable {
    static final class Entry {
        final Source source;
        final String origin;
        final boolean producer;
        private final NativeDependentMatchFrame frame;
        private final Rule original;
        private final Root root;
        private final List<Clause> clauses;
        private Entry(NativeDependentMatchFrame frame,Rule original,Source source,Root root,List<Clause> clauses) throws Exception {
            this.frame=frame;this.original=original;this.source=source;this.root=root;producer=root!=null;
            origin=original.getOrigin();this.clauses=Collections.unmodifiableList(new ArrayList<>(clauses));
        }
    }
    static final class Negative {
        final String predicate;
        final int originalLiteral;
        final List<Node> arguments;
        private Negative(String predicate,int originalLiteral,List<Node> arguments) {
            this.predicate=predicate;this.originalLiteral=originalLiteral;this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));
        }
    }
    static final class Instance {
        final Entry receiver;
        final int branch;
        final List<Negative> remaining;
        final Support support;
        private final NativeDependentMatchFrame frame;
        private final Match parent;
        private Instance(NativeDependentMatchFrame frame,Entry receiver,int branch,List<Negative> remaining,Support support,Match parent) {
            this.frame=frame;this.receiver=receiver;this.branch=branch;this.remaining=Collections.unmodifiableList(new ArrayList<>(remaining));this.support=support;this.parent=parent;
        }
    }
    static final class Match {
        final Entry producer;
        final Instance before,after;
        final int pivot;
        final NativeDependentWitnessTerms.Atom application;
        final Substitution substitution;
        private Match(NativeDependentMatchFrame frame,Entry producer,Instance before,int pivot,NativeDependentWitnessTerms.Atom application,Substitution substitution,List<Negative> residual) {
            this.producer=producer;this.before=before;this.pivot=pivot;this.application=application;this.substitution=substitution;
            Set<Source> sources=new LinkedHashSet<>(before.support.sources);sources.add(producer.source);
            after=new Instance(frame,before.receiver,before.branch,residual,new Support(sources,before.support.depth+1),this);
        }
    }
    /** Checked in-memory handoff only. Nodes and symbols remain frame-local, never durable Term IDs. */
    static final class Materialization {
        final List<NativeDependentWitnessTerms.Atom> producers;
        final List<Negative> receiver,remaining;
        final Support support;
        final List<Source> producerSources;
        final Source receiverSource;
        final int receiverBranch;
        private final NativeDependentMatchFrame frame;
        private final Match proof;
        private Materialization(NativeDependentMatchFrame frame,Match proof,List<NativeDependentWitnessTerms.Atom> producers,List<Negative> receiver,List<Source> producerSources) {
            this.frame=frame;this.proof=proof;
            this.producers=Collections.unmodifiableList(new ArrayList<>(producers));
            this.receiver=Collections.unmodifiableList(new ArrayList<>(receiver));
            remaining=proof.after.remaining;support=proof.after.support;
            this.producerSources=Collections.unmodifiableList(new ArrayList<>(producerSources));
            receiverSource=proof.before.receiver.source;receiverBranch=proof.before.branch;
        }
    }
    private Mind mind;
    private final UUID context;
    private final long revision;
    private final boolean localX;
    private final List<String> initialization;
    private final ContextProductionInventory inventory;
    private final int maximumRows,maximumSteps;
    private final Thread owner=Thread.currentThread();
    private int remaining;
    private boolean failed,closed;
    private final Arena arena;
    private final Map<Long,Entry> entries=new LinkedHashMap<>();
    private NativeDependentMatchFrame(Mind mind,UUID context,long revision,boolean localX,List<String> initialization,int maximumRows,int maximumSteps,int maximumWork,int maximumNodes) throws Exception {
        if(maximumSteps<1||maximumWork<1)throw new IllegalArgumentException("Positive matching bounds required");
        this.mind=mind;this.context=context;this.revision=revision;this.localX=localX;
        this.initialization=Collections.unmodifiableList(new ArrayList<>(initialization));this.maximumRows=maximumRows;this.maximumSteps=maximumSteps;remaining=maximumWork;
        inventory=ContextProductionInventory.inspect(mind,context,revision,localX,this.initialization,maximumRows);
        arena=new Arena(mind,maximumWork,maximumNodes);
        try {
            // Account for the ENTIRE original inventory before exposing any entry.
            for(ContextProductionInventory.Row row:inventory.primary) {
                tick();Rule original=(Rule)mind.getRules().get(row.nativeRule);List<Clause> clauses=headless(original,mind);
                Root root=null;if(clauses==null) {root=arena.capture(original);clauses=Collections.emptyList();}
                else {
                    User user=new User();Mind replay=new Mind(user);user.setCurrentMind(replay);
                    Rule copy=(Rule)replay.compileLine(row.origin,false,new LinkedList<ITerm>());
                    List<Clause> check=headless(copy,replay);
                    if(check==null||check.size()!=clauses.size())throw refuse("Different whole receiver replay");
                    for(int i=0;i<clauses.size();i++)if(!clauses.get(i).equivalent(check.get(i),this::tick))throw refuse("Different whole receiver replay clause");
                }
                Source source=new Source(context,revision,row.nativeRule,localX,"inventory:"+inventory.fingerprint);
                entries.put(row.nativeRule,new Entry(this,original,source,root,clauses));
            }
            current();
        }catch(Exception failure){failed=true;arena.close();throw failure;}
    }
    static NativeDependentMatchFrame open(Mind mind,UUID context,long revision,boolean localX,List<String> initialization,int maximumRows,int maximumSteps,int maximumWork,int maximumNodes) throws Exception {
        return new NativeDependentMatchFrame(mind,context,revision,localX,initialization,maximumRows,maximumSteps,maximumWork,maximumNodes);
    }
    private IllegalStateException refuse(String why){failed=true;return new IllegalStateException(why);}
    private void active(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Matching frame requires owning thread");if(failed||closed)throw new IllegalStateException("Matching frame failed or closed");}
    private void tick(){active();if(--remaining<0)throw refuse("Dependent matching work limit");}
    private void current() throws Exception {
        tick();try {
            ContextProductionInventory now=ContextProductionInventory.inspect(mind,context,revision,localX,initialization,maximumRows);
            if(!inventory.fingerprint.equals(now.fingerprint))throw refuse("Changed whole dependent matching inventory");
            for(Entry entry:entries.values()){tick();if(mind.getRules().get(entry.original.getId())!=entry.original)throw refuse("Replaced matching original");}
            // Probe the arena lifecycle without allocating variables or term nodes.
            arena.unify(Collections.emptyList(),Collections.emptyList());
        }catch(Exception failure){failed=true;throw failure;}
    }
    private void owned(Entry entry){tick();if(entry==null||entry.frame!=this||entries.get(entry.source.rule)!=entry)throw refuse("Different matching entry frame");}
    private void owned(Instance instance){tick();if(instance==null||instance.frame!=this)throw refuse("Different matching instance frame");owned(instance.receiver);}
    private List<Clause> headless(Rule rule,Mind in) throws Exception {
        tick();if(rule==null||rule.isQuery()||rule.isGenerated()||rule.isDeleted(in)||rule.getTree().isEmpty())throw refuse("Whole live primary matching row required");
        List<Clause> clauses=new ArrayList<>();
        for(List<Domain> branch:rule.getTree()) {
            tick();if(branch.isEmpty())throw refuse("Empty native receiver branch");
            for(Domain domain:branch) {
                tick();if(domain.isAntc())return null;
                if(domain.isSystem(in)||domain.isCalculated(in))throw refuse("Ordinary receiver required");
                for(IArgument argument:domain.getArguments()) {
                    tick();if(argument.getType()==ArgumentType.TVARIABLE) {
                        if(((TVariable)argument.getObject(in)).getRuleId()!=rule.getId())throw refuse("Different receiver variable owner");
                    }else if(argument.getType()!=ArgumentType.TERM||!SemanticTermSnapshot.isOrdinaryValue(argument.getValue(in)))throw refuse("Ordinary universal receiver slot required");
                }
            }
            Clause clause=Clause.shapeOf(branch,in);if(clause==null)throw refuse("Unsupported whole receiver clause");clauses.add(clause);
        }
        return clauses;
    }
    List<Entry> entries() throws Exception {current();return Collections.unmodifiableList(new ArrayList<>(entries.values()));}
    ContextProductionInventory inventory() throws Exception {current();return inventory;}
    Entry entry(Rule rule) throws Exception {current();Entry entry=entries.get(rule.getId());owned(entry);if(entry.original!=rule)throw refuse("Different matching original object");return entry;}
    Instance instantiate(Entry receiver,int branch) throws Exception {
        current();owned(receiver);if(receiver.producer||branch<0||branch>=receiver.clauses.size())throw refuse("Exact headless receiver branch required");
        Map<Integer,Node> variables=new HashMap<>();List<Negative> literals=new ArrayList<>();Clause clause=receiver.clauses.get(branch);
        for(int i=0;i<clause.literals.size();i++) {
            tick();ContextProductionCapture.Atom atom=clause.literals.get(i);List<Node> arguments=new ArrayList<>();
            for(Slot slot:atom.arguments) {
                tick();Node node;if(slot.variable<0)node=arena.value(slot.value);else {
                    node=variables.get(slot.variable);if(node==null){node=arena.variable();variables.put(slot.variable,node);}
                }
                arguments.add(node);
            }
            literals.add(new Negative(atom.predicate,i,arguments));
        }
        Instance result=new Instance(this,receiver,branch,literals,new Support(Collections.singleton(receiver.source),0),null);current();return result;
    }
    private List<Negative> residual(Instance before,int pivot,Substitution substitution) throws Exception {
        List<Negative> result=new ArrayList<>();for(int i=0;i<before.remaining.size();i++)if(i!=pivot){tick();Negative old=before.remaining.get(i);result.add(new Negative(old.predicate,old.originalLiteral,arena.apply(old.arguments,substitution)));}return result;
    }
    Match match(Entry producer,Instance before,int pivot) throws Exception {
        current();owned(producer);owned(before);
        if(!producer.producer||pivot<0||pivot>=before.remaining.size())throw refuse("Positive unit and exact negative pivot required");
        if(before.support.depth>=maximumSteps)throw refuse("Dependent matching step limit");
        Negative negative=before.remaining.get(pivot);Domain domain=producer.original.getDomain();
        if(!domain.getPredicate(mind).getName(mind).equals(negative.predicate)||domain.getRange()!=negative.arguments.size())return null;
        NativeDependentWitnessTerms.Atom application=arena.project(producer.root);Substitution substitution=arena.unify(application.arguments,negative.arguments);
        if(substitution==null)return null;
        Match result=new Match(this,producer,before,pivot,application,substitution,residual(before,pivot,substitution));current();return result;
    }
    private void initial(Instance instance) throws Exception {
        owned(instance);Entry root=instance.receiver;
        if(root.producer||instance.branch<0||instance.branch>=root.clauses.size()||instance.parent!=null||instance.support.depth!=0||!instance.support.sources.equals(Collections.singleton(root.source)))throw refuse("Different initial receiver support");
        Clause clause=root.clauses.get(instance.branch);if(clause.literals.size()!=instance.remaining.size())throw refuse("Partial initial receiver");
        Map<Integer,Node> variables=new HashMap<>();Set<Node> images=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        for(int i=0;i<clause.literals.size();i++) {
            ContextProductionCapture.Atom atom=clause.literals.get(i);Negative literal=instance.remaining.get(i);tick();
            if(!atom.predicate.equals(literal.predicate)||literal.originalLiteral!=i||atom.arguments.size()!=literal.arguments.size())throw refuse("Different initial receiver literal");
            for(int j=0;j<atom.arguments.size();j++) {
                tick();Slot slot=atom.arguments.get(j);Node node=literal.arguments.get(j);
                if(slot.variable<0){if(node.kind!=Kind.VALUE||!slot.value.semanticallyEquals(node.value))throw refuse("Different receiver fixed value");}
                else {
                    if(node.kind!=Kind.VARIABLE)throw refuse("Receiver variable replaced");Node old=variables.get(slot.variable);
                    if(old==null){if(!images.add(node))throw refuse("Merged receiver variables");variables.put(slot.variable,node);}else if(old!=node)throw refuse("Split receiver variable");
                }
            }
        }
    }
    private void application(Match match) throws Exception {
        owned(match.producer);NativeDependentWitnessTerms.Atom actual=arena.project(match.producer.root),old=match.application;
        if(actual.nativeRule!=old.nativeRule||!actual.predicate.equals(old.predicate)||actual.binders.size()!=old.binders.size())throw refuse("Different producer application");
        List<Node> from=new ArrayList<>(),to=new ArrayList<>();Set<Node> unique=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        for(int i=0;i<actual.binders.size();i++) {
            tick();if(actual.binders.get(i).kind==Kind.VARIABLE){Node value=old.binders.get(i);if(value.kind!=Kind.VARIABLE||!unique.add(value))throw refuse("Different fresh producer variables");from.add(actual.binders.get(i));to.add(value);}
        }
        Substitution rename=arena.unify(from,to);if(rename==null)throw refuse("Producer instance renaming failed");
        if(!arena.apply(actual.arguments,rename).equals(old.arguments)||!arena.apply(actual.binders,rename).equals(old.binders))throw refuse("Different whole producer instance");
    }
    void verify(Match last) throws Exception {
        current();if(last==null)throw refuse("Actual match receipt required");
        List<Match> chain=new ArrayList<>();Match at=last;
        while(at!=null){tick();owned(at.before);owned(at.after);chain.add(at);at=at.before.parent;if(chain.size()>maximumSteps)throw refuse("Matching proof depth limit");}
        Collections.reverse(chain);initial(chain.get(0).before);Instance previous=chain.get(0).before;
        for(Match step:chain) {
            tick();if(step.before!=previous||step.after.parent!=step||step.after.receiver!=step.before.receiver||step.after.branch!=step.before.branch)throw refuse("Different matching chain");application(step);
            if(step.pivot<0||step.pivot>=step.before.remaining.size())throw refuse("Different matching pivot");Negative negative=step.before.remaining.get(step.pivot);
            if(!negative.predicate.equals(step.application.predicate))throw refuse("Different matching predicate");
            Substitution substitution=arena.unify(step.application.arguments,negative.arguments);if(substitution==null)throw refuse("Unverified matching substitution");
            if(!arena.apply(step.application.arguments,substitution).equals(arena.apply(step.application.arguments,step.substitution))
                    ||!arena.apply(step.application.binders,substitution).equals(arena.apply(step.application.binders,step.substitution))
                    ||!arena.apply(negative.arguments,substitution).equals(arena.apply(negative.arguments,step.substitution)))
                throw refuse("Different recorded matching substitution effects");
            List<Negative> expected=residual(step.before,step.pivot,substitution);
            if(expected.size()!=step.after.remaining.size())throw refuse("Dropped receiver residual");
            for(int i=0;i<expected.size();i++){tick();Negative a=expected.get(i),b=step.after.remaining.get(i);if(!a.predicate.equals(b.predicate)||a.originalLiteral!=b.originalLiteral||!a.arguments.equals(b.arguments))throw refuse("Different matching residual");}
            Set<Source> support=new LinkedHashSet<>(step.before.support.sources);support.add(step.producer.source);
            if(step.after.support.depth!=step.before.support.depth+1||!step.after.support.sources.equals(support))throw refuse("Different matching Source union/grade");previous=step.after;
        }
        current();
    }
    private List<Match> chain(Match last) {
        List<Match> chain=new ArrayList<>();
        for(Match step=last;step!=null;step=step.before.parent){tick();chain.add(step);if(chain.size()>maximumSteps)throw refuse("Materialization proof depth limit");}
        Collections.reverse(chain);return chain;
    }
    private Materialization projectMaterialization(Match last) throws Exception {
        List<Match> chain=chain(last);List<NativeDependentWitnessTerms.Atom> producers=new ArrayList<>();
        for(int i=0;i<chain.size();i++) {
            NativeDependentWitnessTerms.Atom atom=chain.get(i).application;
            // Later pivots can refine variables inside an EARLIER dependent application.
            for(int j=i;j<chain.size();j++){tick();atom=arena.apply(atom,chain.get(j).substitution);}
            producers.add(atom);
        }
        List<Negative> receiver=new ArrayList<>();
        for(Negative old:chain.get(0).before.remaining) {
            List<Node> arguments=old.arguments;
            for(Match step:chain){tick();arguments=arena.apply(arguments,step.substitution);}
            receiver.add(new Negative(old.predicate,old.originalLiteral,arguments));
        }
        List<Source> sources=new ArrayList<>();for(Match step:chain){tick();sources.add(step.producer.source);}
        return new Materialization(this,last,producers,receiver,sources);
    }
    Materialization materialize(Match last) throws Exception {
        verify(last);Materialization result=projectMaterialization(last);current();return result;
    }
    void verify(Materialization materialization) throws Exception {
        current();if(materialization==null||materialization.frame!=this)throw refuse("Different materialization frame");
        verify(materialization.proof);Materialization expected=projectMaterialization(materialization.proof);
        if(materialization.producers.size()!=expected.producers.size()||materialization.receiver.size()!=expected.receiver.size()
                ||materialization.remaining!=expected.remaining||materialization.support!=expected.support
                ||!materialization.producerSources.equals(expected.producerSources)||!materialization.receiverSource.equals(expected.receiverSource)||materialization.receiverBranch!=expected.receiverBranch)throw refuse("Different materialization lineage");
        for(int i=0;i<expected.producers.size();i++) {
            tick();NativeDependentWitnessTerms.Atom a=expected.producers.get(i),b=materialization.producers.get(i);
            if(a.nativeRule!=b.nativeRule||!a.arena.equals(b.arena)||!a.predicate.equals(b.predicate)||!a.arguments.equals(b.arguments)||!a.binders.equals(b.binders))throw refuse("Different materialized producer");
        }
        for(int i=0;i<expected.receiver.size();i++) {
            tick();Negative a=expected.receiver.get(i),b=materialization.receiver.get(i);
            if(!a.predicate.equals(b.predicate)||a.originalLiteral!=b.originalLiteral||!a.arguments.equals(b.arguments))throw refuse("Different materialized receiver");
        }
        current();
    }
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Matching close requires owning thread");closed=true;entries.clear();arena.close();mind=null;}
}
