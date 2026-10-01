package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.primitives.TVariableSet;
import org.kanger.units.*;
public final class SmallVariableSetHashRunner {
    static int checks;
    static void check(boolean ok) { if(!ok) throw new AssertionError("check "+checks); checks++; }
    static int oracle(List<TValue> values,Mind mind) throws Exception {
        SortedSet<TVariable> set=new TreeSet<>();
        for(TValue v:values) set.add(v.getTVar(mind));
        int hash=3;
        for(TVariable v:set) { long id=v.getId();hash=47*hash+(int)(id^(id>>>32)); }
        return hash;
    }
    static final class Observed extends TVariable {
        static final List<Integer> reads=new ArrayList<>();
        Runnable callback; RuntimeException failure;
        @Override public long getId() {
            reads.add(getIndex());
            if(callback!=null) callback.run();
            if(failure!=null) throw failure;
            return super.getId();
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("variable-set-hash-").toString());
        Mind mind=new Mind(new User()); Term term=new Term();
        for(int size:new int[]{0,1,2,3,7,30}) {
            List<TValue> values=new ArrayList<>();
            for(int i=size-1;i>=0;i--) {
                TVariable v=new TVariable();v.setIndex(i);v.setId((1L<<40)+i);
                TValue value=new TValue(v,term);values.add(value);values.add(value);
            }
            TVariableSet actual=new TVariableSet(values,mind);
            check(actual.hashCode()==oracle(values,mind));
            if(size>0) values.get(0).getTVar(mind).setId(Long.MIN_VALUE);
            check(actual.hashCode()==oracle(values,mind));
        }
        Observed a=new Observed(),b=new Observed();a.setIndex(1);b.setIndex(2);a.setId(7);b.setId(13);
        List<TValue> values=Arrays.asList(new TValue(b,term),new TValue(a,term));
        TVariableSet actual=new TVariableSet(values,mind);
        a.callback=()->b.setId(Long.MAX_VALUE);
        Observed.reads.clear();int hash=actual.hashCode();
        check(Observed.reads.equals(Arrays.asList(1,2)));
        a.callback=null;check(hash==oracle(values,mind));
        RuntimeException failure=new IllegalStateException("ID failure");a.failure=failure;
        Observed.reads.clear();
        try { actual.hashCode();throw new AssertionError("missing failure"); }
        catch(RuntimeException e) {check(e==failure);}
        check(Observed.reads.equals(Arrays.asList(1)));
        a.failure=null;b.failure=failure;Observed.reads.clear();
        try { actual.hashCode();throw new AssertionError("missing second failure"); }
        catch(RuntimeException e) {check(e==failure);}
        check(Observed.reads.equals(Arrays.asList(1,2)));
        System.out.println("SMALL_VARIABLE_SET_HASH_OK checks="+checks);
    }
}
