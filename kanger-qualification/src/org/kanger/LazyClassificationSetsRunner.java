package org.kanger;
import java.util.*;
import java.lang.reflect.*;
import org.kanger.units.Domain;
/** Independent HashSet oracle for the internal lazy-add helper. */
public final class LazyClassificationSetsRunner {
    static int checks;
    static void check(boolean c){if(!c)throw new AssertionError();checks++;}
    static Method add,empty;
    static Set<Domain> add(Set<Domain> set,Domain domain)throws Throwable {try{return (Set<Domain>)add.invoke(null,set,domain);}catch(InvocationTargetException e){throw e.getCause();}}
    static boolean empty(Set<Domain> set)throws Exception{return (Boolean)empty.invoke(null,set);}
    static final class Key extends Domain {
        final List<String> trace;final int n;boolean failHash,failEquals;final RuntimeException failure=new IllegalStateException("original");
        Key(List<String> trace,int n,long id){this.trace=trace;this.n=n;setId(id);}
        public int hashCode(){trace.add("hash"+n);if(failHash)throw failure;return super.hashCode();}
        public boolean equals(Object o){trace.add("equals"+n);if(failEquals)throw failure;return super.equals(o);}
    }
    static List<Long> ids(Set<Domain> set){List<Long> result=new ArrayList<>();if(set!=null)for(Domain d:set)result.add(d==null?null:d.getId());return result;}
    public static void main(String[] ignored)throws Throwable {
        add=Linker.class.getDeclaredMethod("addClassificationDomain",Set.class,Domain.class);add.setAccessible(true);
        empty=Linker.class.getDeclaredMethod("classificationEmpty",Set.class);empty.setAccessible(true);
        check(empty(null));check(empty(new HashSet<Domain>()));
        for(int count:new int[]{0,1,2,3,4,16,80}){
            List<String> trace=new ArrayList<>();List<Domain> keys=new ArrayList<>();
            for(int n=0;n<count;n++)keys.add(new Key(trace,n,((long)n<<32)|n));
            if(count>0){keys.add(keys.get(0));keys.add(null);}
            Set<Domain> reference=new HashSet<>();for(Domain d:keys)reference.add(d);
            List<Long> expected=ids(reference);List<String> callbacks=new ArrayList<>(trace);trace.clear();
            Set<Domain> candidate=null;for(Domain d:keys)candidate=add(candidate,d);
            check(expected.equals(ids(candidate)));check(callbacks.equals(trace));
        }
        List<String> trace=new ArrayList<>();Key bad=new Key(trace,1,1);bad.failHash=true;
        try{add(null,bad);throw new AssertionError();}catch(RuntimeException e){check(e==bad.failure);}
        check(trace.equals(Arrays.asList("hash1")));
        trace.clear();Key first=new Key(trace,1,0),second=new Key(trace,2,(1L<<32)|1);second.failEquals=true;
        Set<Domain> retained=add(null,first);trace.clear();
        try{add(retained,second);throw new AssertionError();}catch(RuntimeException e){check(e==second.failure);}
        check(trace.equals(Arrays.asList("hash2","equals2")));check(retained.size()==1);
        Set<Domain> original=retained;retained.clear();retained=add(retained,first);check(retained==original);
        Domain a=new Domain(),b=new Domain();a.setId(0);b.setId(4);
        Set<Domain> reference=new HashSet<>();reference.add(a);reference.add(b);
        Set<Domain> lazy=add(add(null,a),b);check(ids(reference).equals(ids(lazy)));
        a.setId(32);reference.add(a);lazy=add(lazy,a);check(ids(reference).equals(ids(lazy)));
        check(!empty(lazy));check(!empty(reference));
        System.out.println("LAZY_CLASSIFICATION_SETS_OK checks="+checks);
    }
}
