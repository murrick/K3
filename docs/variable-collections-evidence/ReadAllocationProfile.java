import java.nio.file.Paths;
import java.util.*;
import jdk.jfr.consumer.*;
/** Standalone Java 17 diagnostic; not part of the Java 8 reactor. */
public class ReadAllocationProfile {
    public static void main(String[] args) throws Exception {
        Map<String,long[]> classes=new HashMap<>(), sites=new HashMap<>();
        long total=0, samples=0;
        try(RecordingFile recording=new RecordingFile(Paths.get(args[0]))) {
            while(recording.hasMoreEvents()) {
                RecordedEvent event=recording.readEvent();
                if(!event.getEventType().getName().equals("jdk.ObjectAllocationSample")) continue;
                RecordedThread thread=event.getThread();
                if(thread==null || !thread.getJavaName().equals("main")) continue;
                long weight=event.getLong("weight");
                String name=event.getClass("objectClass").getName();
                long[] c=classes.computeIfAbsent(name,k->new long[2]); c[0]++; c[1]+=weight;
                String site="<outside-kanger>";
                RecordedStackTrace trace=event.getStackTrace();
                if(trace!=null) for(RecordedFrame frame:trace.getFrames()) {
                    RecordedMethod method=frame.getMethod();
                    if(method.getType().getName().startsWith("org.kanger.")) {
                        site=method.getType().getName()+"."+method.getName()+":"+frame.getLineNumber(); break;
                    }
                }
                long[] s=sites.computeIfAbsent(site,k->new long[2]); s[0]++; s[1]+=weight;
                total+=weight; samples++;
            }
        }
        System.out.println("MAIN_ALLOCATION_SAMPLES\t"+samples+"\tWEIGHT_BYTES\t"+total);
        print("CLASS",classes); print("SITE",sites);
    }
    private static void print(String kind,Map<String,long[]> rows) {
        rows.entrySet().stream().sorted((a,b)->Long.compare(b.getValue()[1],a.getValue()[1]))
            .forEach(e->System.out.println(kind+"\t"+e.getKey()+"\t"+e.getValue()[0]+"\t"+e.getValue()[1]));
    }
}
