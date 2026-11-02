/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Isolated symbolic dependent projections. Not a Term factory, model or admission receipt. */
final class NativeDependentWitnessTerms {
    enum Kind { VARIABLE, VALUE, APPLICATION }
    static final class Symbol {
        final UUID arena;
        final long nativeRule;
        final int binder;
        private Symbol(UUID arena,long rule,int binder) { this.arena=arena; nativeRule=rule; this.binder=binder; }
    }
    static final class Node {
        final UUID arena;
        final Kind kind;
        final int variable;
        final SemanticTermSnapshot value;
        final Symbol symbol;
        final List<Node> arguments;
        private Node(UUID arena,Kind kind,int variable,SemanticTermSnapshot value,Symbol symbol,List<Node> arguments) {
            this.arena=arena; this.kind=kind; this.variable=variable; this.value=value; this.symbol=symbol;
            this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));
        }
    }
    static final class Atom {
        final UUID arena;
        final long nativeRule;
        final String predicate;
        final List<Node> arguments,binders;
        private Atom(UUID arena,long nativeRule,String predicate,List<Node> arguments,List<Node> binders) {
            this.arena=arena; this.nativeRule=nativeRule; this.predicate=predicate;
            this.arguments=Collections.unmodifiableList(new ArrayList<>(arguments));
            this.binders=Collections.unmodifiableList(new ArrayList<>(binders));
        }
    }
    static final class Substitution {
        final UUID arena;
        final Map<Integer,Node> bindings;
        private Substitution(UUID arena,Map<Integer,Node> bindings) {
            this.arena=arena; this.bindings=Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        }
    }
    static final class Root {
        private final Arena arena;
        private final Rule original;
        private final String origin;
        private final NativePrenexUnitScope scope;
        private final Symbol[] symbols;
        private Root(Arena arena,Rule original,String origin,NativePrenexUnitScope scope) {
            this.arena=arena; this.original=original; this.origin=origin; this.scope=scope;
            symbols=new Symbol[scope.binders.size()];
            for (int i=0;i<symbols.length;i++) {
                arena.tick(); if (!scope.binders.get(i).universal) symbols[i]=new Symbol(arena.id,original.getId(),i);
            }
        }
        private void current() throws Exception {
            arena.tick();
            if (arena.mind.getRules().get(original.getId())!=original||!Objects.equals(origin,original.getOrigin()))
                throw arena.refuse("Changed dependent projection original");
            NativePrenexUnitScope now=NativePrenexUnitScope.observe(original,arena.mind,arena::tick);
            if (!NativePrenexUnitScope.same(scope,now,arena::tick)) throw arena.refuse("Changed dependent projection native shape");
            for(int i=0;i<scope.binders.size();i++) {
                arena.tick(); if(scope.binders.get(i).nativeId!=now.binders.get(i).nativeId)
                    throw arena.refuse("Changed dependent projection native binder");
            }
        }
    }
    private static final class Call {
        final Symbol symbol; final List<Node> arguments;
        Call(Symbol symbol,List<Node> arguments) { this.symbol=symbol; this.arguments=new ArrayList<>(arguments); }
        @Override public int hashCode() { return 31*System.identityHashCode(symbol)+arguments.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof Call&&symbol==((Call)other).symbol&&arguments.equals(((Call)other).arguments);
        }
    }
    private static final class Equation {
        final Node a,b; Equation(Node a,Node b) { this.a=a; this.b=b; }
    }
    static final class Arena implements AutoCloseable {
        private final UUID id=UUID.randomUUID();
        private final Thread owner=Thread.currentThread();
        private Mind mind;
        private int remaining,nodes,variables;
        private final int maximumNodes;
        private boolean failed,closed;
        private final Map<Long,Root> roots=new LinkedHashMap<>();
        private final List<Node> values=new ArrayList<>();
        private final Map<Call,Node> calls=new HashMap<>();
        Arena(Mind mind,int maximumWork,int maximumNodes) {
            if(mind==null||maximumWork<1||maximumNodes<1)throw new IllegalArgumentException("Bounded projection arena required");
            this.mind=mind; remaining=maximumWork; this.maximumNodes=maximumNodes;
        }
        private void active() {
            if(Thread.currentThread()!=owner)throw new IllegalStateException("Dependent projection arena requires its owning thread");
            if(failed||closed)throw new IllegalStateException("Dependent projection arena is failed or closed");
        }
        private IllegalStateException refuse(String why) { failed=true; return new IllegalStateException(why); }
        private void tick() { active(); if(--remaining<0)throw refuse("Dependent projection work limit"); }
        private void owned(Node node) { tick(); if(node==null||!id.equals(node.arena))throw refuse("Different dependent term arena"); }
        private void current() throws Exception {
            tick();
            try { for(Root root:roots.values())root.current(); }
            catch(Exception changed) { failed=true;throw changed; }
        }
        private Node node(Kind kind,int variable,SemanticTermSnapshot value,Symbol symbol,List<Node> arguments) {
            tick(); if(nodes==maximumNodes)throw refuse("Dependent projection node limit");nodes++;
            return new Node(id,kind,variable,value,symbol,arguments);
        }
        Root capture(Rule original) throws Exception {
            current(); Root existing=roots.get(original.getId());
            if(existing!=null) { if(existing.original!=original)throw refuse("Different projection original object");return existing; }
            try {
                NativePrenexUnitScope nativeScope=NativePrenexUnitScope.observe(original,mind,this::tick);
                String origin=original.getOrigin();User user=new User();Mind replay=new Mind(user);user.setCurrentMind(replay);
                Rule rule=(Rule)replay.compileLine(origin,false,new LinkedList<ITerm>());
                if(rule==null||!NativePrenexUnitScope.same(nativeScope,NativePrenexUnitScope.observe(rule,replay,this::tick),this::tick))
                    throw refuse("Dependent projection whole origin/native replay mismatch");
                Root root=new Root(this,original,origin,nativeScope);root.current();roots.put(original.getId(),root);return root;
            } catch(Exception failure) { failed=true;throw failure; }
        }
        private Node variableNode() { return node(Kind.VARIABLE,variables++,null,null,Collections.emptyList()); }
        Node variable() throws Exception { current();return variableNode(); }
        private Node valueNode(SemanticTermSnapshot value) {
            if(value==null)throw refuse("Ordinary dependent term value required");
            for(Node old:values) { tick();if(old.value.semanticallyEquals(value))return old; }
            Node result=node(Kind.VALUE,-1,value,null,Collections.emptyList());values.add(result);return result;
        }
        Node value(SemanticTermSnapshot value) throws Exception { current();return valueNode(value); }
        private Node call(Symbol symbol,List<Node> arguments) {
            tick();if(symbol==null||!id.equals(symbol.arena))throw refuse("Different dependent symbol arena");
            for(Node argument:arguments)owned(argument);
            Call key=new Call(symbol,arguments);Node result=calls.get(key);
            if(result==null) { result=node(Kind.APPLICATION,-1,null,symbol,arguments);calls.put(key,result); }
            return result;
        }
        Atom project(Root root) throws Exception {
            current();if(root==null||root.arena!=this||roots.get(root.original.getId())!=root)throw refuse("Different dependent projection root");
            List<Node> binders=new ArrayList<>();
            for(int i=0;i<root.scope.binders.size();i++) {
                tick();NativePrenexUnitScope.Binder binder=root.scope.binders.get(i);
                if(binder.universal)binders.add(variableNode());
                else {
                    List<Node> parameters=new ArrayList<>();
                    for(int dependency:binder.precedingUniversals) { tick();parameters.add(binders.get(dependency)); }
                    binders.add(call(root.symbols[i],parameters));
                }
            }
            List<Node> arguments=new ArrayList<>();
            for(NativePrenexUnitScope.Slot slot:root.scope.slots) { tick();arguments.add(slot.binder<0?valueNode(slot.value):binders.get(slot.binder)); }
            return new Atom(id,root.original.getId(),root.scope.predicate,arguments,binders);
        }
        /** Owned symbolic atom shape only; this method grants no inference or Source authority. */
        Atom shape(long nativeRule,String predicate,List<Node> arguments) throws Exception {
            current();if(mind.getRules().get(nativeRule)==null||predicate==null)throw refuse("Actual symbolic atom owner required");
            for(Node argument:arguments)owned(argument);
            return new Atom(id,nativeRule,predicate,arguments,Collections.emptyList());
        }
        private Node dereference(Node node,Map<Integer,Node> bindings) {
            int depth=0;
            while(node.kind==Kind.VARIABLE&&bindings.containsKey(node.variable)) {
                tick();if(++depth>bindings.size())throw refuse("Cyclic dependent substitution");node=bindings.get(node.variable);
            }
            return node;
        }
        private boolean occurs(int variable,Node root,Map<Integer,Node> bindings) {
            Deque<Node> pending=new ArrayDeque<>();pending.push(root);Set<Node> seen=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
            while(!pending.isEmpty()) {
                tick();Node node=dereference(pending.pop(),bindings);if(!seen.add(node))continue;
                if(node.kind==Kind.VARIABLE&&node.variable==variable)return true;
                for(Node argument:node.arguments) { tick();pending.push(argument); }
            }
            return false;
        }
        Substitution unify(List<Node> left,List<Node> right) throws Exception {
            current();if(left.size()!=right.size())return null;
            Deque<Equation> pending=new ArrayDeque<>();Map<Integer,Node> bindings=new LinkedHashMap<>();
            for(int i=left.size()-1;i>=0;i--) { owned(left.get(i));owned(right.get(i));pending.push(new Equation(left.get(i),right.get(i))); }
            while(!pending.isEmpty()) {
                tick();Equation equation=pending.pop();Node a=dereference(equation.a,bindings),b=dereference(equation.b,bindings);
                if(a==b)continue;
                if(a.kind==Kind.VARIABLE) { if(occurs(a.variable,b,bindings))return null;bindings.put(a.variable,b); }
                else if(b.kind==Kind.VARIABLE) { if(occurs(b.variable,a,bindings))return null;bindings.put(b.variable,a); }
                else if(a.kind==Kind.VALUE&&b.kind==Kind.VALUE) { if(!a.value.semanticallyEquals(b.value))return null; }
                else if(a.kind==Kind.APPLICATION&&b.kind==Kind.APPLICATION&&a.symbol==b.symbol&&a.arguments.size()==b.arguments.size()) {
                    for(int i=a.arguments.size()-1;i>=0;i--) { tick();pending.push(new Equation(a.arguments.get(i),b.arguments.get(i))); }
                } else return null;
            }
            return new Substitution(id,bindings);
        }
        private Node rewrite(Node root,Substitution substitution) {
            owned(root);if(!id.equals(substitution.arena))throw refuse("Different dependent substitution arena");
            Map<Node,Node> done=new IdentityHashMap<>();Deque<Node> pending=new ArrayDeque<>();Node start=dereference(root,substitution.bindings);pending.push(start);
            while(!pending.isEmpty()) {
                tick();Node node=dereference(pending.peek(),substitution.bindings);
                if(done.containsKey(node)) { pending.pop();continue; }
                if(node.kind!=Kind.APPLICATION) { done.put(node,node);pending.pop();continue; }
                boolean ready=true;List<Node> arguments=new ArrayList<>();
                for(Node original:node.arguments) {
                    tick();Node argument=dereference(original,substitution.bindings);
                    if(!done.containsKey(argument)) { pending.push(argument);ready=false;break; }
                    arguments.add(done.get(argument));
                }
                if(ready) { done.put(node,call(node.symbol,arguments));pending.pop(); }
            }
            return done.get(start);
        }
        Node apply(Node node,Substitution substitution) throws Exception { current();return rewrite(node,substitution); }
        List<Node> apply(List<Node> nodes,Substitution substitution) throws Exception {
            current();if(substitution==null||!id.equals(substitution.arena))throw refuse("Different dependent substitution arena");
            List<Node> result=new ArrayList<>();for(Node node:nodes)result.add(rewrite(node,substitution));return Collections.unmodifiableList(result);
        }
        Atom apply(Atom atom,Substitution substitution) throws Exception {
            current();if(!id.equals(atom.arena))throw refuse("Different dependent atom arena");
            return new Atom(id,atom.nativeRule,atom.predicate,apply(atom.arguments,substitution),apply(atom.binders,substitution));
        }
        @Override public void close() {
            if(Thread.currentThread()!=owner)throw new IllegalStateException("Dependent projection close requires owning thread");
            closed=true;roots.clear();values.clear();calls.clear();mind=null;
        }
    }
}
