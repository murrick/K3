package org.kanger;
import java.lang.reflect.*;
import java.util.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.*;
import org.kanger.storage.*;
import org.kanger.units.*;
/** Unsupported resident boundaries reject without callbacks or hydration. */
public final class TValueResidentBoundaryRunner {
    private static int calls;
    private static void put(Object object,Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static class TrapStep extends Step {public Object getData(){calls++;throw new AssertionError("custom data callback");}public Object getData(Mind m){calls++;throw new AssertionError("custom owner callback");}public IStep getNext(){calls++;throw new AssertionError("custom next callback");}}
    private static class TrapSapato extends Sapato {TrapSapato(){super(null);}public Object getData(){calls++;throw new AssertionError("persistent data callback");}public Object getData(Mind m){calls++;throw new AssertionError("persistent hydration callback");}public IStep getNext(){calls++;throw new AssertionError("persistent next callback");}}
    private static class TrapValue extends TValue {public long getValueId(){calls++;throw new AssertionError("custom TValue callback");}}
    public static void main(String[] args)throws Exception{
        String mode=args[0];TValueDirtyJournal.begin();Mind m=new Mind(new User());TValueFactory f=m.getTValues();Escalera cache=(Escalera)ResidentTValueRead.field(f,"cache");IStep node;long changedTerm=-1;
        TValue value=new TValue(m);value.setId(7);Step s=new Step();s.setId(7);s.setData(value);node=s;
        if(mode.equals("sapato"))node=new Sapato(null);
        else if(mode.equals("trap-sapato"))node=new TrapSapato();
        else if(mode.equals("trap-step"))node=new TrapStep();
        else if(mode.equals("unresolved"))s.setData(null);
        else if(mode.equals("trap-value")){TrapValue v=new TrapValue();v.setId(7);s.setData(v);}
        else if(mode.equals("cycle"))s.setNext(s);
        else if(mode.equals("connection")){IBase proxy=(IBase)Proxy.newProxyInstance(IBase.class.getClassLoader(),new Class<?>[]{IBase.class},(p,method,a)->{calls++;throw new AssertionError("storage callback");});put(f,TValueFactory.class,"connection",proxy);}
        else if(!mode.equals("missing-dirty")&&!mode.equals("lookup-alias"))throw new AssertionError(mode);
        if(mode.equals("missing-dirty")||mode.equals("lookup-alias")){
            Rule owner=new Rule(m);m.getRules().register(owner);TVariable v=m.getTVars().createTVar(owner,m.getTerms().add("x"));value=f.add(v,m.getTerms().add("a"));f.forEach(v,o->true);TValueDirtyJournal.observe(m,"raw-write-baseline");
            if(mode.equals("missing-dirty")){long changed=m.getTerms().add("b").getId();changedTerm=changed;put(value,TValue.class,"valueId",changed);TValueDirtyJournal.observe(m,"raw-ID-write");}
            else{
                @SuppressWarnings("unchecked") Map<Long,IStep> lookup=(Map<Long,IStep>)ResidentTValueRead.field(cache,"memoryById");lookup.remove(value.getId());
                String fingerprint=ResidentTValueRead.fingerprint(m);
                if(!ResidentTValueRead.bucket(m,v.getId()).isEmpty()||!ResidentTValueRead.authority(m).isEmpty()||!fingerprint.equals(ResidentTValueRead.fingerprint(m)))throw new AssertionError("actual fast lookup repaired by resident read");
                int[] seen={0};f.forEach(v,o->{seen[0]++;return true;});if(seen[0]!=0)throw new AssertionError("native lookup control differs");
                TValueDirtyJournal.observe(m,"raw-lookup-alias");
            }
        }else{cache.setRoot(node);TValueDirtyJournal.observe(m,"unsupported-"+mode);}
        boolean rejected=false;try{TValueDirtyJournal.finish();}catch(AssertionError e){rejected=e.getMessage().contains((mode.equals("missing-dirty")||mode.equals("lookup-alias"))?"dirty projection mismatch":"unsupported resident boundary");}
        if(mode.equals("missing-dirty")&&value.getValueId()!=changedTerm)throw new AssertionError("raw native write repaired");
        if(!rejected||calls!=0)throw new AssertionError("closed boundary without callbacks "+mode+" calls="+calls);
        if(!mode.equals("missing-dirty")&&!mode.equals("lookup-alias")&&cache.getRoot()!=node)throw new AssertionError("unsupported root silently repaired");
        if(mode.equals("unresolved")&&s.getData()!=null)throw new AssertionError("unresolved unit hydrated");
        if(mode.equals("cycle")&&s.getNext()!=s)throw new AssertionError("cycle repaired");
        if(mode.equals("lookup-alias")&&f.get(value.getId())!=null)throw new AssertionError("raw native lookup repaired");
        TValueDirtyJournal.begin();TValueDirtyJournal.finish();
        System.out.println("TVALUE_RESIDENT_BOUNDARY_OK mode="+mode+" rejected=true callbacks=0 session=closed native_root=preserved");
    }
}
