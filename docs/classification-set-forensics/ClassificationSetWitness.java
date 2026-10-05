import java.util.*;
import org.kanger.units.Domain;
/** Diagnostic examples, not a replacement implementation or Linker qualification. */
public final class ClassificationSetWitness {
    static int checks;
    static void check(boolean condition){if(!condition)throw new AssertionError();checks++;}
    static List<Long> order(Iterable<Domain> domains){List<Long> ids=new ArrayList<>();for(Domain d:domains)ids.add(d.getId());return ids;}
    static final class ObservedDomain extends Domain {
        int hashes;final RuntimeException failure=new IllegalStateException("original hash callback");boolean fail;
        @Override public int hashCode(){hashes++;if(fail)throw failure;return super.hashCode();}
    }
    public static void main(String[] ignored){
        Domain a=new Domain(),b=new Domain();a.setId(0);b.setId(4);
        Set<Domain> reference=new HashSet<>(),small=new HashSet<>(2);
        reference.add(a);reference.add(b);small.add(a);small.add(b);
        List<Long> x=order(reference),y=order(small);
        check(reference.size()==2&&small.size()==2);check(reference.contains(a)&&reference.contains(b)&&small.contains(a)&&small.contains(b));
        check(!x.equals(y));check(!x.get(0).equals(y.get(0)));
        System.out.println("ORDER reference="+x+" capacity2="+y);
        ObservedDomain normal=new ObservedDomain(),linear=new ObservedDomain();normal.setId(1);linear.setId(1);
        Set<Domain> hashed=new HashSet<>();List<Domain> list=new ArrayList<>();
        hashed.add(normal);list.add(linear);check(normal.hashes==1&&linear.hashes==0);
        normal.fail=true;linear.fail=true;boolean threw=false;
        try{hashed.add(normal);}catch(RuntimeException e){check(e==normal.failure);threw=true;}
        list.add(linear);check(threw&&linear.hashes==0);
        System.out.println("HASH_CALLBACK reference_calls="+normal.hashes+" list_calls="+linear.hashes+" original_exception_preserved=true");
        System.out.println("CLASSIFICATION_SET_WITNESS_OK scenarios=2 checks="+checks);
    }
}
