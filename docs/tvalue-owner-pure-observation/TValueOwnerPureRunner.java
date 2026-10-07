package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.kanger.units.*;
import org.kanger.storage.ByteBuffer;
import org.kanger.factory.TValueFactory;
/** Resident observer purity and independent native enumeration controls. */
public final class TValueOwnerPureRunner {
    private static int checks;
    private static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
    private static int flag(TValue v)throws Exception{ByteBuffer b=new ByteBuffer(v.pack().getBuffer());b.mark();b.getLong();b.getLong();return b.getByte();}
    private static TVariable variable(Mind m,String name)throws Exception{Rule owner=new Rule(m);m.getRules().register(owner);return m.getTVars().createTVar(owner,m.getTerms().add(name));}
    private static void stable(Mind mind,String reason)throws Exception{
        String before=ResidentTValueRead.fingerprint(mind);if(Boolean.getBoolean("persistence.shadow"))TValueDirtyJournal.observe(mind,reason);else ResidentTValueRead.authority(mind);require(before.equals(ResidentTValueRead.fingerprint(mind)),"owner/resident/index fingerprint changed: "+reason);
    }
    private static SortedMap<Long,String> nativeView(Mind m)throws Exception{
        SortedSet<Long> keys=new TreeSet<>();for(TValue v:m.getTValues())keys.add(v.getTVarId());SortedMap<Long,String> result=new TreeMap<>();
        for(long key:keys){TVariable shell=new TVariable(m);shell.setId(key);List<TValue> values=new ArrayList<>();m.getTValues().forEach(shell,o->{values.add((TValue)o);return true;});if(!values.isEmpty())result.put(key,ResidentTValueRead.encode(m,values));}return result;
    }
    public static void main(String[] args)throws Exception{
        boolean shadow=Boolean.getBoolean("persistence.shadow");if(shadow)TValueDirtyJournal.begin();Mind m=new Mind(new User());TVariable x=variable(m,"x"),y=variable(m,"y");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b"),c=(Term)m.getTerms().add("c");
        TValue first=m.getTValues().add(x,a),second=m.getTValues().add(x,b),other=m.getTValues().add(y,c);m.getTValues().dropAction();
        require(!(Boolean)ResidentTValueRead.field(m.getTValues(),"indexInitialized"),"root native variable index starts lazy");
        String before=ResidentTValueRead.fingerprint(m);SortedMap<Long,String> rootPure=ResidentTValueRead.authority(m);
        require(before.equals(ResidentTValueRead.fingerprint(m)),"full authority initializes no index");stable(m,"pure-lazy-baseline");
        require(!(Boolean)ResidentTValueRead.field(m.getTValues(),"indexInitialized"),"journal preserves lazy variable index");
        require(ResidentTValueRead.bucket(m,x.getId()).equals(Arrays.asList(first,second)),"virtual lazy root enumeration preserves oldest-first order");
        Mind child=new Mind(m);TValue third=child.getTValues().add(x,c);first.setDeleted(true,child);first.setMind(child);
        require(first.getMind()==child&&flag(first)==1&&!first.isDeleted(m)&&first.isDeleted(child),"owner-sensitive native fixture established");
        stable(m,"owner-pure-root-read");stable(child,"owner-pure-child-read");
        require(first.getMind()==child&&flag(first)==1,"observer preserves owner and packed deletion");
        require(ResidentTValueRead.authority(m).equals(rootPure),"root ID projection unchanged");
        require(ResidentTValueRead.bucket(child,x.getId()).equals(Arrays.asList(first,second,third)),"layered native ID dedup and order");
        require(ResidentTValueRead.bucket(child,y.getId()).equals(Collections.singletonList(other)),"other variable inherited order");
        SortedMap<Long,String> childPure=ResidentTValueRead.authority(child);require(childPure.get(x.getId()).startsWith("["+first.getId()+":"+a.getId()+":1,"),"child deletion in resident authority");
        // Native enumeration is an explicit test control AFTER purity checks;
        // it is never called inside the observer or its independent full oracle.
        require(nativeView(child).equals(childPure),"child resident projection matches actual native forEach");
        first.setMind(child);int packedBefore=flag(first);require(nativeView(m).equals(rootPure),"root resident projection matches actual native forEach");
        require(packedBefore==1&&first.getMind()==m&&flag(first)==0,"native control still rebinds owner and changes pack");
        require((Boolean)ResidentTValueRead.field(m.getTValues(),"indexInitialized"),"native control really initializes index");stable(m,"pure-initialized-index");
        m.getTValues().mark();TValue transientValue=m.getTValues().add(y,b);stable(m,"pure-deferred-checkpoint");m.getTValues().release();
        stable(m,"pure-native-rollback");require(!ResidentTValueRead.bucket(m,y.getId()).contains(transientValue),"native rollback remains authoritative");
        first.setValue(b);stable(m,"pure-existing-term-write");stable(child,"pure-shared-term-write");require(first.getValueId()==b.getId(),"registered payload write observed without repair");first.setValue(a);stable(m,"pure-term-restored");stable(child,"pure-child-term-restored");
        m.release(child);require(m.pendingTransactionCount()==0,"native child cleanup settled");
        if(shadow){List<String> rows=TValueDirtyJournal.finish();Files.write(Paths.get(System.getProperty("journal.path")),rows,StandardCharsets.UTF_8);System.out.println(rows.get(rows.size()-1));}
        System.out.println("OWNER_PURE_NATIVE root_order="+first.getId()+","+second.getId()+" child_extra="+third.getId()+" pure_owner=child pure_pack=1 native_owner=root native_pack=0 lazy_index=preserved initialized_index=preserved rollback=preserved reservations=0");
        System.out.println("TVALUE_OWNER_PURE_OK checks="+checks);
    }
}
