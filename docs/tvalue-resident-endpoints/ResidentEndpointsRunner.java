package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.TValue;
import org.kanger.interfaces.internal.IStep;
/** Negative qualification only; the candidate is NOT a journal reader. */
public final class ResidentEndpointsRunner {
    static int checks;
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    static Object raw(Object o,String n)throws Exception{return ResidentPersistentRead.raw(o,n);}
    @SuppressWarnings("unchecked") static Map<Long,IStep> cache(Base b)throws Exception{return (Map<Long,IStep>)raw(b,"cache");}
    static String manifest(Base b)throws Exception{
        Map<?,?> entries=(Map<?,?>)raw(raw(b,"integrity"),"entries");TreeMap<Long,String> copy=new TreeMap<>();
        for(Map.Entry<?,?> e:entries.entrySet())copy.put((Long)e.getKey(),raw(e.getValue(),"length")+":"+raw(e.getValue(),"crc32"));return copy.toString();
    }
    /** The proposed universe + topology proof: exact native objects, no callbacks/I/O. */
    @SuppressWarnings("unchecked") static List<Long> candidate(Base b)throws Exception{
        Map<Long,?> entries=(Map<Long,?>)raw(raw(b,"integrity"),"entries");Set<Long> expected=new HashSet<>(entries.keySet());
        Map<Long,IStep> resident=new HashMap<>();for(Map.Entry<Long,IStep> e:cache(b).entrySet())resident.put(e.getKey(),e.getValue());
        Set<Long> referenced=new HashSet<>();Long tail=null;
        for(Long id:expected){IStep step=resident.get(id);require(step!=null&&step.getClass()==Sapato.class,"exact resident step");require(raw(step,"base")==b&&(Long)raw(step,"id")==id.longValue(),"resident binding");
            Object unit=raw(step,"data");require(unit!=null&&unit.getClass()==TValue.class&&(Long)raw(unit,"id")==id.longValue(),"exact unit identity");
            long next=(Long)raw(step,"next");if(next==-1){require(tail==null,"one tail");tail=id;}else{require(expected.contains(next),"no dangling link");referenced.add(next);}}
        Set<Long> roots=new HashSet<>(expected);roots.removeAll(referenced);require(roots.size()==1&&tail!=null,"one root/tail");
        List<Long> order=new ArrayList<>();Set<Long> seen=new HashSet<>();long id=roots.iterator().next();
        for(;;){require(seen.add(id),"no cycle");order.add(id);long next=(Long)raw(resident.get(id),"next");if(next==-1){require(id==tail.longValue(),"matching tail");break;}id=next;}
        require(seen.equals(expected),"complete manifest universe");return order;
    }
    static void unresolved(Base b)throws Exception{require(raw(b,"rootId")==null&&raw(b,"topId")==null,"unresolved native endpoints");}
    static void strictRefusal(Base b)throws Exception{String before=ResidentPersistentRunner.fingerprint(b);boolean refused=false;try{ResidentPersistentRead.snapshot(b);}catch(AssertionError e){refused=e.getMessage().equals("unsupported persistent boundary: unresolved endpoints");}require(refused,"existing reader refuses");require(before.equals(ResidentPersistentRunner.fingerprint(b)),"refusal pure");}
    public static void main(String[] args)throws Exception{
        User u=(User)UserFactory.createUser("resident-endpoints","resident-endpoints");Mind owner=new Mind(u);u.setProperty("cache.enable","true");u.setProperty("cache.size","4194304");u.setProperty("cache.data.size","0");
        String name=Files.createTempDirectory("resident-endpoints-").resolve("values").toString();Object locker=new Object();Base b=new Base(name,9,locker,false,u);IStep previous=null;
        for(long id:new long[]{40,7,91}){TValue v=new TValue(owner);v.setId(id);v.setPersistentReferences(300+id,100+id);Step s=new Step();s.setId(id);s.setHash(v.getHash());s.setData(v);s.setNext(previous);previous=new Sapato(b,s);b.add(previous);}b.flush();
        b.get(7);b.get(91);b.get(40);unresolved(b);String originalManifest=manifest(b),before=ResidentPersistentRunner.fingerprint(b);
        List<Long> pristine=candidate(b);require(pristine.equals(Arrays.asList(91L,7L,40L)),"pristine order");require(before.equals(ResidentPersistentRunner.fingerprint(b)),"candidate pure");strictRefusal(b);
        // Public native mutation only; deliberately no update()/append().
        Map<Long,IStep> nodes=new HashMap<>();for(Map.Entry<Long,IStep> e:cache(b).entrySet())nodes.put(e.getKey(),e.getValue());
        nodes.get(7L).setNext(nodes.get(40L));nodes.get(40L).setNext(nodes.get(91L));nodes.get(91L).setNext(null);
        unresolved(b);require(originalManifest.equals(manifest(b)),"manifest unchanged by native setters");before=ResidentPersistentRunner.fingerprint(b);
        List<Long> rewired=candidate(b);require(rewired.equals(Arrays.asList(7L,40L,91L)),"rewired valid complete chain");require(before.equals(ResidentPersistentRunner.fingerprint(b)),"rewired candidate pure");strictRefusal(b);
        // Explicit native disk-based endpoint resolution, outside candidate.
        b.getRoot();b.getTop();require((Long)raw(b,"rootId")==91L&&(Long)raw(b,"topId")==40L,"native disk endpoints unchanged");
        require(rewired.get(0).longValue()!=(Long)raw(b,"rootId")&&rewired.get(2).longValue()!=(Long)raw(b,"topId"),"candidate endpoints disagree");
        require(originalManifest.equals(manifest(b)),"native control preserves integrity entries");b.close();
        b=new Base(name,9,locker,false,u);List<Long> disk=new ArrayList<>();for(IStep s=b.getRoot();s!=null;s=s.getNext())disk.add(s.getId());
        require(disk.equals(pristine),"reopened persisted chain unchanged");b.close();
        System.out.println("RESIDENT_ENDPOINTS_NATIVE pristine="+pristine+" rewired="+rewired+" disk="+disk+" manifest=unchanged candidate=pure strict_reader=refused promotion=rejected");
        System.out.println("RESIDENT_ENDPOINTS_OK checks="+checks);
    }
}
