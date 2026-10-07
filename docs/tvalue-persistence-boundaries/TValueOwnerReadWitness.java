package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.kanger.units.*;
import org.kanger.storage.ByteBuffer;
/** Measures an externally visible native read side effect; no repair. */
public final class TValueOwnerReadWitness {
    private static int deletionFlag(TValue value)throws Exception{ByteBuffer p=new ByteBuffer(value.pack().getBuffer());p.mark();p.getLong();p.getLong();return p.getByte();}
    public static void main(String[] args)throws Exception{
        String read=args[0];boolean shadow=TValuePersistenceRunner.shadow(),session=shadow||read.equals("journal");if(session)TValueDirtyJournal.begin();
        Mind root=new Mind(new User());root.compileLine("!keep(a);",false,null);TVariable v=TValueMetadataNativeRunner.variable(root,"owner_probe");TValue t=root.getTValues().add(v,root.getTerms().add("a"));
        if(read.equals("journal"))TValueDirtyJournal.observe(root,"owner-root-baseline");
        Mind child=new Mind(root);t.setDeleted(true,child);t.setMind(child);
        long id=t.getId(),term=t.getValueId(),variable=t.getTVarId(),ownerID=t.getMindId();
        boolean before=t.getMind()==child;int beforeFlag=deletionFlag(t);int changes=read.equals("journal")?TValueDirtyJournal.contextChangeCount(root):0;
        if(read.equals("foreach"))root.getTValues().forEach(v,o->true);
        else if(read.equals("journal"))TValueDirtyJournal.observe(root,"owner-root-read");
        else if(!read.equals("none"))throw new AssertionError(read);
        boolean after=t.getMind()==child;int afterFlag=deletionFlag(t);boolean expectChild=read.equals("none");
        if(!before||beforeFlag!=1||after!=expectChild||afterFlag!=(expectChild?1:0)||t.getId()!=id||t.getValueId()!=term||t.getTVarId()!=variable||t.getMindId()!=ownerID||t.isDeleted(root)||!t.isDeleted(child))throw new AssertionError("native owner/serialization witness");
        if(read.equals("journal")&&TValueDirtyJournal.contextChangeCount(root)!=changes)throw new AssertionError("root canonical projection must stay unchanged");
        System.out.println("OWNER_READ_NATIVE read="+read+" before_child=true after_child="+after+" pack_deleted="+beforeFlag+"->"+afterFlag+" canonical_tuple="+id+":"+variable+":"+term+" native_owner_ID="+ownerID+" root_deleted=false child_deleted=true");
        root.release(child);if(root.pendingTransactionCount()!=0)throw new AssertionError("witness cleanup reservations");
        if(session){List<String> rows=TValueDirtyJournal.finish();if(shadow){Files.write(Paths.get(System.getProperty("journal.path")),rows,StandardCharsets.UTF_8);System.out.println(rows.get(rows.size()-1));}}
        System.out.println("TVALUE_OWNER_READ_WITNESS_OK read="+read+" reservations=0");
    }
}
