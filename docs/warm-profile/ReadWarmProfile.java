import java.nio.file.*;
import java.time.*;
import java.util.*;
import jdk.jfr.consumer.*;
/** Diagnostic Java17 tool; no dependency added to the reactor. */
public class ReadWarmProfile {
    static class Window { Instant start,end; int sample; }
    static String method(RecordedFrame f) {return f.getMethod().getType().getName()+"."+f.getMethod().getName();}
    static void add(Map<String,Long> map,String key,long weight) {map.merge(key,weight,Long::sum);}
    static boolean inside(Instant t,List<Window> windows) {for(Window w:windows) if(!t.isBefore(w.start)&&t.isBefore(w.end))return true;return false;}
    static void print(String kind,Map<String,Long> map) {map.entrySet().stream().sorted((a,b)->Long.compare(b.getValue(),a.getValue())).limit(kind.equals("CPU_PATH") ? 50 : Long.MAX_VALUE).forEach(e->System.out.println(kind+"\t"+e.getValue()+"\t"+e.getKey()));}
    public static void main(String[] args)throws Exception {
        List<Window> windows=new ArrayList<>();
        try(RecordingFile f=new RecordingFile(Paths.get(args[0]))) {while(f.hasMoreEvents()) {RecordedEvent e=f.readEvent(); if(e.getEventType().getName().equals("kanger.OptimizeWindow")){Window w=new Window();w.start=e.getStartTime();w.end=e.getEndTime();w.sample=e.getInt("sample");System.out.println("WINDOW\t"+w.sample+"\t"+e.getDuration().toNanos());if(w.sample>=2)windows.add(w);}}}
        if(windows.size()!=4)throw new AssertionError("Expected four warm windows: "+windows.size());
        Map<String,Long> leaf=new HashMap<>(),first=new HashMap<>(),inclusive=new HashMap<>(),paths=new HashMap<>(),classes=new HashMap<>(),allocSites=new HashMap<>();
        long cpu=0,alloc=0,weight=0,truncatedCpu=0,truncatedAlloc=0;
        try(RecordingFile f=new RecordingFile(Paths.get(args[0]))) {while(f.hasMoreEvents()) {
            RecordedEvent e=f.readEvent();String type=e.getEventType().getName();
            boolean execution=type.equals("jdk.ExecutionSample"), allocation=type.equals("jdk.ObjectAllocationSample");
            if(!execution&&!allocation)continue;
            RecordedThread t=execution?e.getThread("sampledThread"):e.getThread();
            if(t==null||!t.getJavaName().equals("main")||!inside(e.getStartTime(),windows))continue;
            RecordedStackTrace trace=e.getStackTrace();
            if(execution) {cpu++;if(trace!=null&&trace.isTruncated())truncatedCpu++;} else {alloc++;weight+=e.getLong("weight");if(trace!=null&&trace.isTruncated())truncatedAlloc++;}
            List<RecordedFrame> frames=trace==null?Collections.emptyList():trace.getFrames();
            String firstK="<no-kanger-stack>";Set<String> seen=new HashSet<>();StringBuilder path=new StringBuilder();
            for(RecordedFrame frame:frames) {String m=method(frame); if(m.startsWith("org.kanger.")) {if(firstK.equals("<no-kanger-stack>")) firstK=m+":"+frame.getLineNumber();if(execution&&seen.add(m))add(inclusive,m,1);if(path.length()<2500)path.append(m).append(":").append(frame.getLineNumber()).append(" <- ");}}
            if(execution) {add(leaf,frames.isEmpty()?"<no-stack>":method(frames.get(0)),1);add(first,firstK,1);add(paths,path.toString(),1);} else {String c=e.getClass("objectClass").getName();long w=e.getLong("weight");add(classes,c,w);add(allocSites,c+" @ "+firstK,w);}
        }}
        System.out.println("TOTAL\tcpu_samples="+cpu+" allocation_samples="+alloc+" sampled_allocation_weight="+weight+" truncated_cpu="+truncatedCpu+" truncated_alloc="+truncatedAlloc);
        print("CPU_LEAF",leaf);print("CPU_FIRST_KANGER",first);print("CPU_INCLUSIVE",inclusive);print("CPU_PATH",paths);print("ALLOC_CLASS_WEIGHT",classes);print("ALLOC_SITE_WEIGHT",allocSites);
    }
}
