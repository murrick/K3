package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class TValueMetadataSafetyRunner {
    public static void main(String[] args)throws Exception{
        TValueDirtyJournal.begin();System.setProperty("metadata.shadow","true");
        TValueMetadataNativeRunner.main(args);
        List<String> rows=TValueDirtyJournal.finish();
        long changes=rows.stream().filter(r->r.startsWith("CHANGE ")&&r.contains("reason=setValue-")).count();
        if(changes!=2)throw new AssertionError("ancestor setter must update both native views");
        if(rows.stream().noneMatch(r->r.startsWith("CHANGE ")&&r.contains("reason=persistent-term-change")))throw new AssertionError("persistent payload delta missing");
        if(rows.stream().anyMatch(r->r.startsWith("TOUCH ")&&r.contains("value=-1")))throw new AssertionError("unregistered construction observed");
        // The last three setter calls before the probe boundary belong only to
        // the unregistered same-ID probe and must emit no invalidation rows.
        int net=0,probe=0;for(int i=0;i<rows.size();i++){if(rows.get(i).startsWith("VIEW ")&&rows.get(i).contains("reason=transient-net-no-change"))net=i;if(rows.get(i).startsWith("VIEW ")&&rows.get(i).contains("reason=unregistered-same-ID-probe"))probe=i;}
        if(net==0||probe<=net||rows.subList(net+1,probe).stream().anyMatch(r->r.startsWith("TOUCH ")))throw new AssertionError("same-ID probe selected canonical bucket");
        Files.write(Paths.get(System.getProperty("journal.path")),rows,StandardCharsets.UTF_8);
        System.out.println(rows.get(rows.size()-1));System.out.println("TVALUE_METADATA_SAFETY_OK parent_and_child=true persistent=true probe_identity=true no_hydration=true");
    }
}
