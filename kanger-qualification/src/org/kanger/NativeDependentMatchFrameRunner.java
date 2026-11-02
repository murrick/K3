/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.NativeDependentMatchFrame.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.interfaces.*;
import org.kanger.units.Rule;

/** Actual native-row matching adapter qualification; default inference remains unchanged. */
public final class NativeDependentMatchFrameRunner {
    private static int checks;
    private static final UUID CONTEXT=new UUID(0,505);
    private static void require(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule compile(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static NativeDependentMatchFrame frame(Mind m,int steps)throws Exception{return NativeDependentMatchFrame.open(m,CONTEXT,7,true,Collections.emptyList(),100,steps,10000000,50000);}
    private static String fingerprint(Mind m)throws Exception{return ContextProductionInventory.inspect(m,CONTEXT,7,true,Collections.emptyList(),100).fingerprint;}
    private static void cases()throws Exception{
        String[] units={"!@a $u @b rel(a,u,b);","!@x $w @y rel(x,w,y);","!$u @a @b rel(a,u,b);","!@a @b $u rel(a,u,b);"};
        for(int i=0;i<units.length;i++)for(boolean reverse:new boolean[]{false,true})for(boolean bodyReverse:new boolean[]{false,true})for(boolean cross:new boolean[]{false,true}){
            String prohibition=cross?"!@u ~("+(bodyReverse?"rel(2,u,1)&&rel(1,u,1)":"rel(1,u,1)&&rel(2,u,1)")+");":"!@a @u ~("+(bodyReverse?"rel(a,u,2)&&rel(a,u,1)":"rel(a,u,1)&&rel(a,u,2)")+");";
            Mind m=memory();Rule p,n;if(reverse){n=compile(m,prohibition);p=compile(m,units[i]);}else{p=compile(m,units[i]);n=compile(m,prohibition);}
            String before=fingerprint(m);int terms=m.getTerms().size();
            try(NativeDependentMatchFrame f=frame(m,16)){
                require(f.entries().size()==2&&f.inventory().primary.size()==2,"all actual originals accounted");
                Entry producer=f.entry(p),receiver=f.entry(n);Instance initial=f.instantiate(receiver,0);
                require(initial.remaining.size()==2&&initial.support.sources.equals(Collections.singleton(receiver.source)),"whole receiver starts with exact Source0");
                Match first=f.match(producer,initial,0);require(first!=null,"first typed native match succeeds");f.verify(first);
                require(first.after.remaining.size()==1&&first.after.support.depth==1,"whole residual and grade preserved");
                Match second=f.match(producer,first.after,0);boolean expected=cross?i==2:i!=3;
                require((second!=null)==expected,"adapter distinguishes dependent/global/later witness in both orders");
                if(second!=null){f.verify(second);require(second.after.remaining.isEmpty()&&second.after.support.depth==2,"checked empty branch at cumulative grade2");require(second.after.support.sources.equals(new HashSet<>(Arrays.asList(producer.source,receiver.source))),"actual whole Source union, not per-clause labels");}
                else f.verify(first);
                require(producer.source.context.equals(CONTEXT)&&producer.source.revision==7&&producer.source.localX&&producer.source.view.equals("inventory:"+before),"inventory-local labels exact, no inferred provider authority");
                require(fingerprint(m).equals(before)&&m.getTerms().size()==terms,"matching/verification never writes native rules, bindings or dictionary");
            }
        }
    }
    private static void multipleWitnesses()throws Exception{
        for(boolean independent:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){
            Mind m=memory();String positive="!@a $u @b $v rel(v,a,u,b,u,v);";
            String negative=independent?"!@a @u @v @w ~(rel(v,a,u,1,u,v)&&rel(w,a,u,2,u,w));":"!@a @u @v ~(rel(v,a,u,1,u,v)&&rel(v,a,u,2,u,v));";
            Rule p,n;if(reverse){n=compile(m,negative);p=compile(m,positive);}else{p=compile(m,positive);n=compile(m,negative);}
            try(NativeDependentMatchFrame f=frame(m,16)){
                Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);require(a!=null,"two existential symbols retain full actual unit shape");
                require(a.application.arguments.get(0)==a.application.binders.get(3)&&a.application.arguments.get(2)==a.application.binders.get(1),"first-use order differs from declaration order without aliasing");
                require(a.application.binders.get(1).arguments.size()==1&&a.application.binders.get(3).arguments.size()==2,"u(a) and v(a,b) have different scopes");
                Match b=f.match(f.entry(p),a.after,0);require((b!=null)==independent,"receiver sharing versus independent later witnesses is exact");
                if(b!=null)f.verify(b);else f.verify(a);
            }
        }
    }
    private static void alternativesAndPredicates()throws Exception{
        Mind m=memory();Rule universal=compile(m,"!@x p(x);"),constant=compile(m,"!p(1);"),constraint=compile(m,"!@a @b ~(p(a)&&p(b));"),other=compile(m,"!q(1);"),arity=compile(m,"!p(1,2);"),anchor=compile(m,"!anchor(1);");
        Rule cache=compile(m,"!cached(1);");cache.setGenerated(true);
        try(NativeDependentMatchFrame f=frame(m,16)){
            require(f.inventory().rows.size()==7&&f.inventory().generated.size()==1&&f.entries().size()==6,"generated cache fully inventoried but excluded as an axiom");
            Entry u=f.entry(universal),c=f.entry(constant),n=f.entry(constraint);Instance initial=f.instantiate(n,0);
            require(f.match(f.entry(other),initial,0)==null,"different predicate is no match");require(f.match(f.entry(arity),initial,0)==null,"different arity is no match");
            Match first=f.match(u,initial,1);f.verify(first);require(first.after.remaining.get(0).originalLiteral==0,"non-first pivot preserves original literal positions");
            Match small=f.match(u,first.after,0),large=f.match(c,first.after,0);f.verify(small);f.verify(large);
            require(large.after.support.sources.containsAll(small.after.support.sources)&&large.after.support.sources.size()==3&&small.after.support.sources.size()==2,"Source superset alternative is not collapsed into smaller footprint");
            require(small.after.support.depth==2&&large.after.support.depth==2,"alternative resolution grades exact");
            for(Source source:large.after.support.sources)require(source.rule!=anchor.getId()&&source.rule!=cache.getId()&&source.rule!=other.getId(),"unrelated/native-cache sources absent from proof support");
            try{first.after.remaining.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
            try{large.after.support.sources.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
            try{f.entries().clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
        }
    }
    private static void wholeRowBranches()throws Exception{
        Mind m=memory();Rule p=compile(m,"!p(1);"),q=compile(m,"!q(2);"),n=compile(m,"!~p(1)&&~q(2);");
        try(NativeDependentMatchFrame f=frame(m,16)){
            Entry receiver=f.entry(n);Instance branch=f.instantiate(receiver,1);
            require(branch.remaining.size()==1&&branch.remaining.get(0).predicate.equals("q"),"exact second clause selected from one whole original row");
            require(f.match(f.entry(p),branch,0)==null,"other clause's predicate cannot close the selected branch");
            Match second=f.match(f.entry(q),branch,0);f.verify(second);
            require(second.after.remaining.isEmpty()&&second.after.support.sources.equals(new HashSet<>(Arrays.asList(receiver.source,f.entry(q).source))),"second clause carries whole original Source, no synthetic clause Source");
            Match first=f.match(f.entry(p),f.instantiate(receiver,0),0);f.verify(first);
            require(first.after.support.sources.contains(receiver.source)&&second.after.support.sources.contains(receiver.source),"both clauses retain the same original receiver attribution");
        }
    }
    private static void wideAndFreshness()throws Exception{
        Mind m=memory();List<Rule> producers=new ArrayList<>();StringBuilder body=new StringBuilder();
        for(int i=0;i<17;i++){producers.add(compile(m,"!@x p"+i+"(x);"));if(i>0)body.append("&&");body.append("p").append(i).append("(a)");}
        Rule receiver=compile(m,"!@a ~("+body+");");String before=fingerprint(m);
        try(NativeDependentMatchFrame f=frame(m,17)){
            Instance instance=f.instantiate(f.entry(receiver),0);Match last=null;
            for(Rule p:producers){last=f.match(f.entry(p),instance,0);require(last!=null,"wide matching keeps every premise");instance=last.after;}
            f.verify(last);require(instance.remaining.isEmpty()&&instance.support.depth==17&&instance.support.sources.size()==18,"17-step proof carries all18 originals");
            require(fingerprint(m).equals(before),"wide verification preserves complete inventory");
        }
        Mind a=memory();Rule p=compile(a,"!@a $u @b rel(a,u,b);"),n=compile(a,"!@a @u ~(rel(a,u,1)&&rel(a,u,2));");
        try(NativeDependentMatchFrame f=frame(a,16)){
            Match one=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);f.verify(one);Rule extra=compile(a,"!newrow(1);");
            boolean stale=false;try{f.verify(one);}catch(IllegalStateException expected){stale=true;}require(stale,"unrelated inventory mutation invalidates the frozen frame too");
            extra.setDeleted(true,a);boolean retry=false;try{f.verify(one);}catch(IllegalStateException expected){retry=true;}require(retry,"failed frame cannot revive after source repair");
        }
        Mind unsupported=memory();compile(unsupported,"!@a $u @b rel(a,u,b);");compile(unsupported,"!@a @u ~(rel(a,u,1)&&rel(a,u,2));");compile(unsupported,"!@x p(x)->q(x);");
        boolean refused=false;try(NativeDependentMatchFrame ignored=frame(unsupported,16)){}catch(Exception expected){refused=true;}require(refused,"later unsupported whole original refuses construction, no successful prefix exposed");
    }
    private static void boundsAndOwnership()throws Exception{
        Mind m=memory();Rule p=compile(m,"!@a $u @b rel(a,u,b);"),n=compile(m,"!@a @u ~(rel(a,u,1)&&rel(a,u,2));");String before=fingerprint(m);
        try(NativeDependentWitnessTerms.Arena empty=new NativeDependentWitnessTerms.Arena(m,1,100)) {
            require(empty.unify(Collections.emptyList(),Collections.emptyList())!=null,"first empty unification spends its one work unit");
            boolean exhausted=false;try{empty.unify(Collections.emptyList(),Collections.emptyList());}catch(IllegalStateException expected){exhausted=true;}
            require(exhausted,"zero-argument operations cannot evade arena work budget");
        }
        boolean work=false;try(NativeDependentMatchFrame ignored=NativeDependentMatchFrame.open(m,CONTEXT,7,true,Collections.emptyList(),100,16,1,100)){}catch(IllegalStateException expected){work=true;}require(work,"construction budget yields no partial frame");
        try(NativeDependentMatchFrame f=frame(m,1)){
            Match first=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);boolean cap=false;try{f.match(f.entry(p),first.after,0);}catch(IllegalStateException expected){cap=true;}require(cap,"step limit refuses instead of false closure");
            boolean retry=false;try{f.verify(first);}catch(IllegalStateException expected){retry=true;}require(retry,"step failure poisons frame");
        }
        try(NativeDependentMatchFrame f=frame(m,16);NativeDependentMatchFrame g=frame(m,16)){
            Instance foreign=g.instantiate(g.entry(n),0);boolean no=false;try{f.match(f.entry(p),foreign,0);}catch(IllegalStateException expected){no=true;}require(no,"same inventory labels do not merge two frame instances");
            require(g.match(g.entry(p),foreign,0)!=null,"foreign frame misuse leaves original frame usable");
        }
        NativeDependentMatchFrame f=frame(m,16);Entry producer=f.entry(p);Instance initial=f.instantiate(f.entry(n),0);AtomicBoolean no=new AtomicBoolean();Thread t=new Thread(()->{try{f.entries();}catch(Exception expected){no.set(true);}});t.start();t.join(5000);
        require(!t.isAlive()&&no.get(),"off-thread frame access refuses");Match first=f.match(producer,initial,0);f.verify(first);f.close();boolean closed=false;try{f.verify(first);}catch(IllegalStateException expected){closed=true;}require(closed,"closed frame receipts are not reusable authority");
        require(fingerprint(m).equals(before),"all budget/lifecycle paths preserve owning native state");
        Mind tampered=memory();Rule bad=compile(tampered,"!@x ~p(x,1);");bad.setOrigin(tampered.getTerms().add("!@x ~p(x,2);"));boolean altered=false;
        try(NativeDependentMatchFrame ignored=frame(tampered,16)){}catch(IllegalStateException expected){altered=true;}require(altered,"receiver whole replay detects changed fixed source text");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-match-").toString());
        cases();multipleWitnesses();alternativesAndPredicates();wholeRowBranches();wideAndFreshness();boundsAndOwnership();
        System.out.println("NATIVE_DEPENDENT_MATCH_FRAME_PASS checks="+checks);
    }
}
