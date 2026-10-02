import java.nio.file.Paths;
import java.util.*;
import jdk.jfr.consumer.*;
/** Standalone Java17 JFR analysis; excluded from the Java8 reactor. */
public class ReadExecutionProfile {
    public static void main(String[] args) throws Exception {
        Map<String,Long> leaf=new HashMap<>(),site=new HashMap<>(),inclusive=new HashMap<>();
        long all=0,main=0,optimize=0;
        try(RecordingFile recording=new RecordingFile(Paths.get(args[0]))) {
            while(recording.hasMoreEvents()) {
                RecordedEvent event=recording.readEvent();
                if(!event.getEventType().getName().equals("jdk.ExecutionSample")) continue;
                all++;
                RecordedThread thread=event.getThread("sampledThread");
                if(thread==null || !thread.getJavaName().equals("main")) continue;
                main++;
                RecordedStackTrace trace=event.getStackTrace();
                if(trace==null || trace.getFrames().isEmpty()) continue;
                boolean found=false;
                for(RecordedFrame f:trace.getFrames())
                    if(method(f).equals("org.kanger.Mind.optimizeHypothesis")) found=true;
                if(!found) continue;
                optimize++;
                leaf.merge(method(trace.getFrames().get(0)),1L,Long::sum);
                Set<String> seen=new HashSet<>(); boolean first=true;
                for(RecordedFrame f:trace.getFrames()) {
                    String m=method(f);
                    if(m.startsWith("org.kanger.")) {
                        if(first) { site.merge(m+":"+f.getLineNumber(),1L,Long::sum);first=false; }
                        if(seen.add(m)) inclusive.merge(m,1L,Long::sum);
                    }
                }
            }
        }
        System.out.println("EXECUTION_SAMPLES all="+all+" main="+main+" optimize="+optimize);
        print("LEAF",leaf);print("FIRST_KANGER",site);print("INCLUSIVE",inclusive);
    }
    static String method(RecordedFrame f) { return f.getMethod().getType().getName()+"."+f.getMethod().getName(); }
    static void print(String kind,Map<String,Long> rows) {
        rows.entrySet().stream().sorted((a,b)->Long.compare(b.getValue(),a.getValue()))
            .forEach(e->System.out.println(kind+"\t"+e.getValue()+"\t"+e.getKey()));
    }
}
