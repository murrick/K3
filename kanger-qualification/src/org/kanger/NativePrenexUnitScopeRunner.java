/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Prefix/native metadata and a whole-domain class-interpretation oracle; no mixed admission. */
public final class NativePrenexUnitScopeRunner {
    private static int checks;
    private static void require(boolean value,String why) { checks++; if(!value)throw new AssertionError(why); }
    private static Mind memory() throws Exception { User u=new User(); Mind m=new Mind(u);u.setCurrentMind(m);return m; }
    private static Rule compile(Mind mind,String text) throws Exception { return (Rule)mind.compileLine(text,false,new LinkedList<ITerm>()); }
    private static NativePrenexUnitScope capture(String text,boolean warm) throws Exception {
        Mind m=memory();if(warm)compile(m,"!@a @b aux(a,b);");
        Rule rule=compile(m,text);return NativePrenexUnitScope.observe(rule,m,ContextGroundProductionRounds.budget(1000000));
    }
    private static void metadata() throws Exception {
        NativePrenexUnitScope a=capture("!@a $u @b @c $v rel(a,u,b,c,v);",false);
        NativePrenexUnitScope b=capture("!@a @b $u @c $v rel(a,u,b,c,v);",true);
        require(a.binders.get(1).precedingUniversals.equals(Arrays.asList(0)),"first witness depends exactly on earlier a");
        require(b.binders.get(2).precedingUniversals.equals(Arrays.asList(0,1)),"moving b before witness adds b dependency");
        require(a.binders.get(1).domini&&b.binders.get(2).domini&&!a.binders.get(4).domini&&!b.binders.get(4).domini,
                "identical existential domini flags do not encode the dependency vectors");
        require(!NativePrenexUnitScope.same(a,b,()->{}),"distinct alternating dependencies remain distinct");
        NativePrenexUnitScope renamed=capture("!@x $y @z @t $w rel(x,y,z,t,w);",true);
        require(NativePrenexUnitScope.same(a,renamed,ContextGroundProductionRounds.budget(1000000)),"alpha renaming and global allocation offset preserve descriptor");
        require(!a.observation.equals(renamed.observation),"operational IDs remain scoped to distinct observation UUIDs");
        NativePrenexUnitScope shared=capture("!$u @a @b rel(a,u,b);",false),dependent=capture("!@a $u @b rel(a,u,b);",false);
        require(shared.binders.get(0).precedingUniversals.isEmpty(),"leading existential has no preceding universal dependency");
        require(dependent.binders.get(1).precedingUniversals.equals(Arrays.asList(0)),"forall-exists-forall has an a-dependent witness");
        require(!NativePrenexUnitScope.same(shared,dependent,()->{}),"global and a-dependent witnesses cannot share metadata shape");
        NativePrenexUnitScope repeated=capture("!@a $u @b rel(a,u,a,b,u,1);",true);
        require(repeated.slots.get(0).binder==repeated.slots.get(2).binder&&repeated.slots.get(1).binder==repeated.slots.get(4).binder,"all repeated uses retain one global declaration position");
        require(!NativePrenexUnitScope.same(repeated,capture("!@a $u @b rel(a,u,a,b,u,\"1\");",false),()->{}),"typed fixed values remain distinct");
        for (int n:new int[]{7,17,65}) {
            StringBuilder prefix=new StringBuilder("!"); List<String> slots=new ArrayList<>();
            for (int i=0;i<n;i++) { prefix.append(i%2==0?'@':'$').append("v").append(i).append(' ');slots.add("v"+i); }
            NativePrenexUnitScope wide=capture(prefix+"wide("+String.join(",",slots)+");",true);
            require(wide.binders.size()==n&&wide.slots.size()==n,"complete alternating prefix and arity "+n);
            for(int i=0;i<n;i++) {
                List<Integer> expected=new ArrayList<>();if(i%2==1)for(int j=0;j<i;j+=2)expected.add(j);
                require(wide.binders.get(i).precedingUniversals.equals(expected),"exact preceding-universal vector at "+i);
            }
        }
        try { a.binders.clear();throw new AssertionError(); }catch(UnsupportedOperationException expected){checks++;}
        try { a.binders.get(1).precedingUniversals.clear();throw new AssertionError(); }catch(UnsupportedOperationException expected){checks++;}
        try { a.slots.clear();throw new AssertionError(); }catch(UnsupportedOperationException expected){checks++;}
    }
    private static void boundaries() throws Exception {
        for(String source:new String[]{"!@a $u rel(a,u)&&q(u);","!@a $u rel(a,u)->q(a);","!@a $u ~rel(a,u);","!@a $u @b rel(a,u);","!@a $u rel(a,u+1);"}) {
            Mind m=memory();Rule r=compile(m,source);boolean refused=false;
            try {NativePrenexUnitScope.observe(r,m,ContextGroundProductionRounds.budget(1000000));}
            catch(UnsupportedOperationException expected){refused=true;}
            require(refused,"wider/nested/unused/function forms have no prefix metadata certificate");
        }
        Mind m=memory();Rule actual=compile(m,"!@a $u @b rel(a,u,b,1);");
        NativePrenexUnitScope before=NativePrenexUnitScope.observe(actual,m,ContextGroundProductionRounds.budget(1000000));
        actual.setOrigin(m.getTerms().add("!@a $u @b rel(a,u,b,2);"));
        NativePrenexUnitScope changedText=NativePrenexUnitScope.observe(actual,m,ContextGroundProductionRounds.budget(1000000));
        NativePrenexUnitScope fresh=capture(actual.getOrigin(),false);
        require(NativePrenexUnitScope.same(before,changedText,()->{}),"single native observation does not falsely claim fixed-leaf text authentication");
        require(!NativePrenexUnitScope.same(changedText,fresh,()->{}),"mandatory whole fresh replay detects altered fixed source text");
        ContextProductionInventory badInventory=ContextProductionInventory.inspect(m,UUID.randomUUID(),1,true,Collections.emptyList(),100);
        ContextProductionNativeBinding.Result badBinding=ContextProductionNativeBinding.bind(m,badInventory,1000000,10000);
        require(!badBinding.bound&&badBinding.snapshot==null&&badBinding.reason.contains("shape or dependencies differ"),"actual binding checks whole native/replay equality before its mixed refusal");
        boolean wrongOwner=false;try {NativePrenexUnitScope.observe(actual,memory(),ContextGroundProductionRounds.budget(1000000));}catch(IllegalStateException expected){wrongOwner=true;}
        require(wrongOwner,"another Mind cannot authenticate the native operational IDs");
        boolean compareBudget=false;try {NativePrenexUnitScope.same(before,changedText,ContextGroundProductionRounds.budget(1));}catch(IllegalStateException expected){compareBudget=true;}
        require(compareBudget,"prefix comparison work is charged, not silently truncated");
        boolean exhausted=false;try {NativePrenexUnitScope.observe(actual,m,ContextGroundProductionRounds.budget(1));}catch(IllegalStateException expected){exhausted=true;}
        require(exhausted,"prefix work cap cannot return partial metadata");
        ((org.kanger.units.Term)actual.getDomain().get(1).getValue(m)).setDomini(false);
        boolean badFlag=false;try {NativePrenexUnitScope.observe(actual,m,ContextGroundProductionRounds.budget(1000000));}catch(IllegalStateException expected){badFlag=true;}
        require(badFlag,"source/native domini disagreement cannot become authenticated metadata");
        for(String source:new String[]{"!@a $u @b rel(a,u,b);","!$u @a @b rel(a,u,b);","!@a @b $u rel(a,u,b);"}) {
            Mind raw=memory();compile(raw,source);
            ContextProductionInventory inventory=ContextProductionInventory.inspect(raw,UUID.randomUUID(),1,true,Collections.emptyList(),100);
            ContextProductionNativeBinding.Result result=ContextProductionNativeBinding.bind(raw,inventory,1000000,10000);
            require(!result.bound&&result.snapshot==null&&result.reason.contains("dependency-aware qualification"),"actual paired native binding retains mixed refusal with explicit reason");
        }
    }
    private static void oracle() {
        int forallExists=0,existsForall=0,safeDependent=0,unsafeGlobal=0;
        // Each table lifts to R(a,u,b) by three nonempty value classes; b is arbitrary.
        for(int table=0;table<512;table++) {
            boolean ae=true,ea=false,cross=true;
            for(int a=0;a<3;a++){boolean any=false;for(int u=0;u<3;u++)any|=(table&(1<<(a*3+u)))!=0;ae&=any;}
            for(int u=0;u<3;u++){boolean all=true;for(int a=0;a<3;a++)all&=(table&(1<<(a*3+u)))!=0;ea|=all;cross&=!((table&(1<<u))!=0&&(table&(1<<(3+u)))!=0);}
            if(ae)forallExists++;if(ea)existsForall++;if(ae&&cross)safeDependent++;if(ea&&cross)unsafeGlobal++;
            require(!(ea&&cross),"one global witness cannot satisfy the cross-a prohibition");
        }
        require(forallExists==343&&existsForall==169,"all 512 class interpretations enumerate both quantifier orders independently");
        require(safeDependent==84&&unsafeGlobal==0,"84 whole-domain dependent models and zero shared-witness models meet prohibition");
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("prefix-metadata-").toString());
        metadata();boundaries();oracle();
        System.out.println("NATIVE_PRENEX_UNIT_SCOPE_PASS checks="+checks);
    }
}
