/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.NativeDependentWitnessTerms.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.interfaces.*;
import org.kanger.units.Rule;

/** Component qualification only: production Linker and mixed admission are not redirected. */
public final class NativeDependentWitnessTermsRunner {
    private static int checks;
    private static void require(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    private static Mind memory() throws Exception { User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m; }
    private static Rule compile(Mind m,String source) throws Exception { return (Rule)m.compileLine(source,false,new LinkedList<ITerm>()); }
    private static SemanticTermSnapshot value(Mind m,String text) throws Exception { return SemanticTermSnapshot.capture(m.getTerms().add(text)); }
    private static Arena arena(Mind m) { return new Arena(m,5000000,20000); }
    private static Set<Long> ids(Mind m) { Set<Long> ids=new HashSet<>();for(IRule r:m.getRules())if(!r.isDeleted(m))ids.add(r.getId());return ids; }
    private static boolean resolves(String positive,String negative,boolean reverse) throws Exception {
        Mind m=memory();Rule unit,constraint;
        if(reverse){constraint=compile(m,negative);unit=compile(m,positive);}else{unit=compile(m,positive);constraint=compile(m,negative);}
        Set<Long> before=ids(m);int terms=m.getTerms().size();
        try(Arena arena=arena(m)) {
            Root root=arena.capture(unit);Clause clause=Clause.shapeOf(constraint.getTree().get(0),m);
            Map<Integer,Node> variables=new HashMap<>();List<List<Node>> residual=new ArrayList<>();
            for(ContextProductionCapture.Atom literal:clause.literals) {
                require(!literal.positive,"actual native headless input retained");
                require(literal.predicate.equals(unit.getDomain().getPredicate(m).getName(m)),"term comparison uses the actual matching predicate");List<Node> arguments=new ArrayList<>();
                for(Slot slot:literal.arguments) {
                    Node node;
                    if(slot.variable<0)node=arena.value(slot.value);
                    else {node=variables.get(slot.variable);if(node==null){node=arena.variable();variables.put(slot.variable,node);}}
                    arguments.add(node);
                }
                residual.add(arguments);
            }
            while(!residual.isEmpty()) {
                NativeDependentWitnessTerms.Atom instance=arena.project(root);
                Substitution substitution=arena.unify(instance.arguments,residual.get(0));
                if(substitution==null) {
                    require(ids(m).equals(before)&&m.getTerms().size()==terms,"failed symbolic comparison never stores facts/terms");return false;
                }
                List<List<Node>> next=new ArrayList<>();for(int i=1;i<residual.size();i++)next.add(arena.apply(residual.get(i),substitution));residual=next;
            }
            require(ids(m).equals(before)&&m.getTerms().size()==terms,"successful symbolic resolution never stores facts/terms");return true;
        }
    }
    private static void counterexamples() throws Exception {
        String[] prefixes={"!@a $u @b rel(a,u,b);","!@x $w @y rel(x,w,y);","!$u @a @b rel(a,u,b);","!@a @b $u rel(a,u,b);"};
        for(int i=0;i<prefixes.length;i++)for(boolean reverse:new boolean[]{false,true})for(boolean bodyReverse:new boolean[]{false,true}) {
            String cross="!@u ~("+(bodyReverse?"rel(2,u,1)&&rel(1,u,1)":"rel(1,u,1)&&rel(2,u,1)")+");";
            String shared="!@a @u ~("+(bodyReverse?"rel(a,u,2)&&rel(a,u,1)":"rel(a,u,1)&&rel(a,u,2)")+");";
            require(resolves(prefixes[i],cross,reverse)==(i==2),"component preserves a-dependent witness versus global witness, both orders");
            require(resolves(prefixes[i],shared,reverse)==(i!=3),"component preserves shared witness across later b and b-dependent control");
        }
    }
    private static void applicationsAndUnification() throws Exception {
        Mind m=memory();Rule rule=compile(m,"!@a $u @b rel(a,u,u,b);");Rule other=compile(m,"!@a $u @b other(a,u,u,b);");
        try(Arena arena=arena(m)) {
            Root root=arena.capture(rule);NativeDependentWitnessTerms.Atom x=arena.project(root);
            Node one=arena.value(value(m,"1")),two=arena.value(value(m,"2"));
            require(x.arguments.get(1)==x.arguments.get(2),"repeated witness slots share the same application");
            require(x.binders.get(1).kind==Kind.APPLICATION&&x.binders.get(1).arguments.get(0)==x.binders.get(0),"unknown a stays a symbolic argument, not a global witness");
            Substitution first=arena.unify(Arrays.asList(x.binders.get(0),x.binders.get(2)),Arrays.asList(one,one));
            NativeDependentWitnessTerms.Atom firstAtom=arena.apply(x,first);Node f1=firstAtom.binders.get(1);
            NativeDependentWitnessTerms.Atom y=arena.project(root);
            Substitution again=arena.unify(Arrays.asList(y.binders.get(0),y.binders.get(2)),Arrays.asList(one,two));
            require(arena.apply(y,again).binders.get(1)==f1,"same earlier a and different later b reuse one witness application");
            NativeDependentWitnessTerms.Atom z=arena.project(root);Substitution changed=arena.unify(Arrays.asList(z.binders.get(0)),Arrays.asList(two));
            Node f2=arena.apply(z,changed).binders.get(1);require(f1!=f2,"different a produces a different symbolic application key");
            Map<Node,SemanticTermSnapshot> interpretation=new IdentityHashMap<>();interpretation.put(f1,value(m,"7"));interpretation.put(f2,value(m,"7"));
            require(interpretation.get(f1).semanticallyEquals(interpretation.get(f2)),"distinct application keys do not impose interpreted inequality");
            NativeDependentWitnessTerms.Atom independent=arena.project(arena.capture(other));
            Node g1=arena.apply(independent,arena.unify(Arrays.asList(independent.binders.get(0)),Arrays.asList(one))).binders.get(1);
            require(g1.symbol!=f1.symbol&&arena.unify(Arrays.asList(g1),Arrays.asList(f1))==null,"different declarations keep distinct symbols; lack of syntactic unifier is not an inequality proof");
            require(arena.unify(Arrays.asList(x.binders.get(0)),Arrays.asList(x.binders.get(1)))==null,"direct occurs check rejects a=f(a)");
            require(arena.unify(Arrays.asList(x.binders.get(0),y.binders.get(0)),Arrays.asList(y.binders.get(0),x.binders.get(1)))==null,"indirect cycle a=c,c=f(a) is rejected");
            require(arena.unify(Arrays.asList(x.binders.get(0)),Arrays.asList(one))!=null,"failed local unification leaks no bindings");
            require(arena.unify(Arrays.asList(one),Arrays.asList(arena.value(value(m,"\"1\""))))==null,"numeric/string value mismatch is exact");
            require(arena.value(value(m,"1"))==one,"semantic value interning ignores operational value IDs");
            try{x.arguments.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
            try{f1.arguments.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
            try{first.bindings.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
            NativeDependentWitnessTerms.Atom deep=arena.project(root);Node term=one;
            for(int i=0;i<257;i++) {
                NativeDependentWitnessTerms.Atom fresh=arena.project(root);
                term=arena.apply(fresh.binders.get(1),arena.unify(Arrays.asList(fresh.binders.get(0)),Arrays.asList(term)));
            }
            int depth=0;Node leaf=term;while(leaf.kind==Kind.APPLICATION){depth++;leaf=leaf.arguments.get(0);}
            require(depth==257&&leaf==one,"deep symbolic rewriting uses iterative traversal");
            require(arena.unify(Arrays.asList(deep.binders.get(0)),Arrays.asList(term))!=null,"unrelated variable can bind a deep acyclic term");
        }
        Mind global=memory();Rule globalRule=compile(global,"!$u @a @b rel(a,u,b);");
        try(Arena arena=arena(global)) {
            Root root=arena.capture(globalRule);NativeDependentWitnessTerms.Atom a=arena.project(root),b=arena.project(root);
            require(a.binders.get(0)==b.binders.get(0)&&a.binders.get(0).arguments.isEmpty(),"leading existential is one zero-parameter application across instances");
        }
    }
    private static void wide() throws Exception {
        for(int count:new int[]{7,17,65}) {
            Mind m=memory();StringBuilder prefix=new StringBuilder("!");List<String> args=new ArrayList<>();
            for(int i=0;i<count;i++){prefix.append("@v").append(i).append(' ');args.add("v"+i);}prefix.append("$u ");args.add("u");args.add("u");
            Rule r=compile(m,prefix+"wide("+String.join(",",args)+");");
            try(Arena arena=arena(m)) {
                Root root=arena.capture(r);NativeDependentWitnessTerms.Atom a=arena.project(root);List<Node> actual=new ArrayList<>();
                for(int i=0;i<count;i++)actual.add(arena.value(value(m,i%2==0?"1":"2")));
                Node first=arena.apply(a.binders.get(count),arena.unify(a.binders.subList(0,count),actual));
                require(first.arguments.size()==count,"every allowed dependency remains at rank "+count);
                for(int i=0;i<count;i++)require(first.arguments.get(i)==actual.get(i),"typed dependency coordinate preserved: "+i);
                NativeDependentWitnessTerms.Atom b=arena.project(root);
                Node second=arena.apply(b.binders.get(count),arena.unify(b.binders.subList(0,count),actual));
                require(first==second,"same complete tuple is canonical across fresh universal variables");
                actual.set(count-1,arena.value(value(m,"\"1\"")));NativeDependentWitnessTerms.Atom c=arena.project(root);
                require(arena.apply(c.binders.get(count),arena.unify(c.binders.subList(0,count),actual))!=first,"late typed coordinate is part of application key");
            }
        }
    }
    private static void lifecycle() throws Exception {
        Mind m=memory();Rule r=compile(m,"!@a $u @b rel(a,u,b);");Set<Long> before=ids(m);int terms=m.getTerms().size();
        Arena a=arena(m);Root root=a.capture(r);a.project(root);
        AtomicBoolean refused=new AtomicBoolean();Thread thread=new Thread(()->{try{a.variable();}catch(Exception expected){refused.set(true);}});thread.start();thread.join(5000);
        require(!thread.isAlive()&&refused.get(),"off-thread use refuses");require(a.project(root)!=null,"off-thread misuse cannot mutate owning arena");
        NativeDependentWitnessTerms.Atom historical=a.project(root);a.close();boolean closed=false;try{a.project(root);}catch(IllegalStateException expected){closed=true;}
        require(closed&&historical.binders.get(1).kind==Kind.APPLICATION,"closed arena leaves historical immutable nodes, no reusable authority");
        try(Arena limited=new Arena(m,1,100)) { boolean cap=false;try{limited.capture(r);}catch(IllegalStateException expected){cap=true;}require(cap,"capture work cap refuses");boolean retry=false;try{limited.variable();}catch(IllegalStateException expected){retry=true;}require(retry,"work failure poisons the arena, fresh attempt required"); }
        try(Arena limited=new Arena(m,1000000,1)) {Root x=limited.capture(r);boolean cap=false;try{limited.project(x);}catch(IllegalStateException expected){cap=true;}require(cap,"node cap refuses without partial atom");boolean retry=false;try{limited.project(x);}catch(IllegalStateException expected){retry=true;}require(retry,"node failure cannot be retried in the same arena");}
        Mind other=memory();Rule foreign=compile(other,"!@a $u @b rel(a,u,b);");
        try(Arena x=arena(m);Arena y=arena(other)) {Node foreignNode=y.project(y.capture(foreign)).binders.get(1);boolean refusedArena=false;try{x.unify(Arrays.asList(x.variable()),Arrays.asList(foreignNode));}catch(IllegalStateException expected){refusedArena=true;}require(refusedArena,"same numeric native IDs from another arena cannot alias");}
        try(Arena stale=arena(m)) {Root x=stale.capture(r);r.setDeleted(true,m);boolean no=false;try{stale.project(x);}catch(Exception expected){no=true;}require(no,"deleted source invalidates projection");r.setDeleted(false,m);boolean retry=false;try{stale.project(x);}catch(IllegalStateException expected){retry=true;}require(retry,"restoring a source does not revive a failed arena");}
        require(ids(m).equals(before)&&m.getTerms().size()==terms,"all symbolic lifecycle paths preserve original native rows and dictionary");
        Mind emptyMind=memory();Rule emptyRule=compile(emptyMind,"!plain();");
        try(Arena x=arena(emptyMind);Arena y=arena(other)) {
            NativeDependentWitnessTerms.Atom empty=x.project(x.capture(emptyRule));
            require(empty.arguments.isEmpty()&&empty.binders.isEmpty(),"zero-arity projection retains empty typed inventories");
            require(x.apply(empty,x.unify(Collections.emptyList(),Collections.emptyList())).arguments.isEmpty(),"owned empty substitution is valid");
            boolean no=false;try{x.apply(Collections.emptyList(),y.unify(Collections.emptyList(),Collections.emptyList()));}catch(IllegalStateException expected){no=true;}
            require(no,"empty argument list cannot bypass substitution arena ownership");
        }
        try(Arena x=arena(m);Arena y=arena(other)) {
            Root foreignRoot=y.capture(foreign);boolean no=false;try{x.project(foreignRoot);}catch(IllegalStateException expected){no=true;}
            require(no,"foreign root cannot alias a same-number local source");
        }
        Mind tampered=memory();Rule bad=compile(tampered,"!@a $u @b rel(a,u,b,1);");bad.setOrigin(tampered.getTerms().add("!@a $u @b rel(a,u,b,2);"));
        try(Arena x=arena(tampered)){boolean no=false;try{x.capture(bad);}catch(IllegalStateException expected){no=true;}require(no,"paired whole replay rejects altered fixed source text");}
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-term-").toString());
        counterexamples();applicationsAndUnification();wide();lifecycle();
        System.out.println("NATIVE_DEPENDENT_WITNESS_TERMS_PASS checks="+checks);
    }
}
