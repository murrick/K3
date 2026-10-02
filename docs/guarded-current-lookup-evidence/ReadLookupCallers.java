import java.nio.file.Paths;
import java.util.*;
import jdk.jfr.consumer.*;
/** Standalone Java17 JFR tool, outside the Java8 reactor. */
public class ReadLookupCallers {
    static String method(RecordedFrame frame) {
        return frame.getMethod().getType().getName()+"."+frame.getMethod().getName();
    }
    public static void main(String[] args) throws Exception {
        for(String path:args) {
            long samples=0,lookup=0,empty=0,duplicate=0;
            Map<String,Long> callers=new TreeMap<>();
            try(RecordingFile file=new RecordingFile(Paths.get(path))) {
                while(file.hasMoreEvents()) {
                    RecordedEvent event=file.readEvent();
                    if(!event.getEventType().getName().equals("jdk.ExecutionSample")) continue;
                    RecordedThread thread=event.getThread("sampledThread");
                    if(thread==null || !thread.getJavaName().equals("main")) continue;
                    RecordedStackTrace trace=event.getStackTrace();
                    if(trace==null) continue;
                    List<RecordedFrame> frames=trace.getFrames();
                    if(frames.stream().noneMatch(f->method(f).equals("org.kanger.Mind.optimizeHypothesis"))) continue;
                    samples++; boolean get=false,isEmpty=false;
                    for(int i=0;i<frames.size();i++) {
                        String name=method(frames.get(i));
                        if(name.equals("org.kanger.factory.TValueFactory.get") && frames.get(i).getMethod().getDescriptor().equals("(Lorg/kanger/units/TVariable;)Lorg/kanger/units/TValue;")) get=true;
                        if(name.equals("org.kanger.factory.TValueFactory.isEmpty") && frames.get(i).getMethod().getDescriptor().equals("(Lorg/kanger/units/TVariable;)Z")) {
                            isEmpty=true;
                            String caller=i+1<frames.size()?method(frames.get(i+1)):"<truncated>";
                            callers.merge(caller,1L,Long::sum);
                        }
                    }
                    if(get) lookup++; if(isEmpty) empty++; if(get && isEmpty) duplicate++;
                }
            }
            System.out.println(path+" samples="+samples+" get="+lookup+" isEmpty="+empty+" isEmpty_inside_get="+duplicate);
            for(Map.Entry<String,Long> e:callers.entrySet()) System.out.println("CALLER\t"+e.getValue()+"\t"+e.getKey());
        }
    }
}
