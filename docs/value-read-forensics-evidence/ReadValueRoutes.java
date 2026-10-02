import java.nio.file.Paths;
import java.util.*;
import jdk.jfr.consumer.*;
/** Standalone Java17 sampled call-route analysis outside Java8 reactor. */
public class ReadValueRoutes {
    static String method(RecordedFrame f) {
        return f.getMethod().getType().getName()+"."+f.getMethod().getName();
    }
    public static void main(String[] args) throws Exception {
        for(String path:args) {
            Map<String,Long> routes=new HashMap<>(); long total=0;
            try(RecordingFile file=new RecordingFile(Paths.get(path))) {
                while(file.hasMoreEvents()) {
                    RecordedEvent event=file.readEvent();
                    if(!event.getEventType().getName().equals("jdk.ExecutionSample")) continue;
                    RecordedThread thread=event.getThread("sampledThread");
                    if(thread==null || !thread.getJavaName().equals("main")) continue;
                    RecordedStackTrace trace=event.getStackTrace(); if(trace==null) continue;
                    List<RecordedFrame> frames=trace.getFrames();
                    if(frames.stream().noneMatch(f->method(f).equals("org.kanger.Mind.optimizeHypothesis"))) continue;
                    total++;
                    Set<String> seen=new HashSet<>();
                    for(int i=0;i<frames.size();i++) {
                        RecordedFrame f=frames.get(i); String m=method(f);
                        String target=null;
                        if(m.equals("org.kanger.factory.TValueFactory.get") && f.getMethod().getDescriptor().equals("(Lorg/kanger/units/TVariable;)Lorg/kanger/units/TValue;")) target="CURRENT_LOOKUP";
                        else if(m.equals("org.kanger.units.TVariable.activeMind")) target="ACTIVE_MIND";
                        else if(m.equals("org.kanger.units.TVariable.setMind")) target="SET_MIND";
                        if(target==null || !seen.add(target)) continue;
                        List<String> route=new ArrayList<>();
                        for(int j=i+1;j<frames.size() && route.size()<5;j++) {
                            RecordedFrame caller=frames.get(j); String name=method(caller);
                            if(name.startsWith("org.kanger.")) route.add(name+":"+caller.getLineNumber());
                        }
                        routes.merge(target+"\t"+String.join(" <- ",route),1L,Long::sum);
                    }
                }
            }
            System.out.println("PROFILE "+path+" optimize_samples="+total);
            routes.entrySet().stream().sorted((a,b)->Long.compare(b.getValue(),a.getValue()))
                .forEach(e->System.out.println(e.getValue()+"\t"+e.getKey()));
        }
    }
}
