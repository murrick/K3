import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import jdk.jfr.consumer.*;

/** Java 17 diagnostic only; never included in the Java 8 production reactor. */
public final class ReadWarmProfile {
    static String method(RecordedFrame frame) {
        return frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
    }
    static boolean contains(List<RecordedEvent> phases, Instant time) {
        for (RecordedEvent phase : phases)
            if (!time.isBefore(phase.getStartTime()) && time.isBefore(phase.getEndTime())) return true;
        return false;
    }
    static void print(String label, Map<String, Long> counts) {
        counts.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .forEach(e -> System.out.println(label + "\t" + e.getValue() + "\t" + e.getKey()));
    }
    public static void main(String[] args) throws Exception {
        List<RecordedEvent> events = RecordingFile.readAllEvents(Paths.get(args[0]));
        List<RecordedEvent> phases = new ArrayList<>();
        for (RecordedEvent event : events)
            if (event.getEventType().getName().equals("kanger.OptimizationPhase") && event.getInt("sample") >= 2)
                phases.add(event);
        if (phases.size() != 4) throw new AssertionError("Expected four warm phases, got " + phases.size());
        Map<String, Long> leaf = new HashMap<>(), inclusive = new HashMap<>(), site = new HashMap<>();
        Map<String, Long> routes = new HashMap<>(), allocClass = new HashMap<>(), allocSite = new HashMap<>();
        long execution = 0, truncated = 0, allocationEvents = 0, allocationWeight = 0;
        for (RecordedEvent event : events) {
            String type = event.getEventType().getName();
            boolean cpu = type.equals("jdk.ExecutionSample");
            if (!cpu && !type.equals("jdk.ObjectAllocationSample")) continue;
            RecordedThread thread = cpu ? event.getThread("sampledThread") : event.getThread();
            if (thread == null || !thread.getJavaName().equals("main") || !contains(phases, event.getStartTime())) continue;
            RecordedStackTrace trace = event.getStackTrace();
            if (trace == null || trace.getFrames().isEmpty()) continue;
            List<RecordedFrame> frames = trace.getFrames();
            String first = null;
            for (RecordedFrame frame : frames)
                if (method(frame).startsWith("org.kanger.")) { first = method(frame) + ":" + frame.getLineNumber(); break; }
            if (!cpu) {
                allocationEvents++;
                long weight = event.getLong("weight");
                allocationWeight += weight;
                allocClass.merge(event.getClass("objectClass").getName(), weight, Long::sum);
                allocSite.merge(first == null ? "NO_KANGER_FRAME" : first, weight, Long::sum);
                continue;
            }
            execution++;
            if (trace.isTruncated()) truncated++;
            leaf.merge(method(frames.get(0)), 1L, Long::sum);
            if (first != null) site.merge(first, 1L, Long::sum);
            Set<String> seen = new HashSet<>(), seenRoutes = new HashSet<>();
            for (int i = 0; i < frames.size(); i++) {
                RecordedFrame frame = frames.get(i);
                String m = method(frame);
                if (m.startsWith("org.kanger.") && seen.add(m)) inclusive.merge(m, 1L, Long::sum);
                String target = null;
                if (m.equals("org.kanger.factory.TValueFactory.get") && frame.getMethod().getDescriptor().equals("(Lorg/kanger/units/TVariable;)Lorg/kanger/units/TValue;")) target = "CURRENT_LOOKUP";
                else if (m.equals("org.kanger.units.TVariable.activeMind")) target = "ACTIVE_MIND";
                else if (m.equals("org.kanger.units.TVariable.setMind")) target = "SET_MIND";
                if (target == null || !seenRoutes.add(target)) continue;
                List<String> callers = new ArrayList<>();
                for (int j = i + 1; j < frames.size() && callers.size() < 5; j++)
                    if (method(frames.get(j)).startsWith("org.kanger."))
                        callers.add(method(frames.get(j)) + ":" + frames.get(j).getLineNumber());
                routes.merge(target + "\t" + String.join(" <- ", callers), 1L, Long::sum);
            }
        }
        System.out.println("WARM_PROFILE phases=4 execution_samples=" + execution + " truncated=" + truncated
            + " allocation_samples=" + allocationEvents + " allocation_weight_bytes=" + allocationWeight);
        print("LEAF", leaf); print("FIRST_KANGER", site); print("INCLUSIVE", inclusive);
        print("ROUTE", routes); print("ALLOCATION_CLASS_WEIGHT", allocClass); print("ALLOCATION_SITE_WEIGHT", allocSite);
    }
}
