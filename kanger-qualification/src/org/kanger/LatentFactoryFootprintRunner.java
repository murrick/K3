/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;
import org.kanger.udf.UDF;

/** Offline occurrence graph accounting; never traverses the owning factory. */
public final class LatentFactoryFootprintRunner {
    private static Instrumentation instrumentation;
    public static void premain(String args, Instrumentation value) { instrumentation = value; }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }
    private static long[] graph(Object... roots) throws Exception {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        ArrayDeque<Object> queue = new ArrayDeque<>();
        Collections.addAll(queue, roots);
        long bytes = 0, boxed = 0, objects = 0;
        while (!queue.isEmpty()) {
            Object value = queue.remove();
            if (!seen.add(value)) continue;
            long size = instrumentation.getObjectSize(value);
            bytes += size; objects++;
            if (value instanceof Long) { boxed += size; continue; }
            Class<?> type = value.getClass();
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive())
                    for (Object item : (Object[]) value) if (item != null) queue.add(item);
                continue;
            }
            String name = type.getName();
            if (!(name.startsWith("java.util.") || name.startsWith("org.kanger.factory.RuleCandidateIndex$")))
                throw new AssertionError("Unexpected retained object: " + name);
            for (Class<?> c = type; c != null; c = c.getSuperclass())
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object item = f.get(value);
                    if (item != null) queue.add(item);
                }
        }
        return new long[]{bytes, bytes - boxed, objects};
    }
    public static void main(String[] args) throws Exception {
        if (instrumentation == null) throw new IllegalStateException("Use -javaagent");
        System.setProperty("user.home", Files.createTempDirectory("factory-footprint-").toString());
        System.setProperty("kanger.experiment.latent", "factory");
        System.setProperty("kanger.experiment.argumentPlan", "false");
        List<String> output = new ArrayList<>();
        output.add("fixture,rules,buckets,domain_slots,reachable_bytes,nonboxed_bytes,objects,rebuild_median_ns");
        measure("empty", "", output);
        measure("natives", new String(Files.readAllBytes(Paths.get("natives.k")), StandardCharsets.UTF_8), output);
        for (int size : new int[]{10, 30}) {
            StringBuilder dense = new StringBuilder(), sparse = new StringBuilder();
            for (int i = 0; i < size; i++) {
                dense.append("!p(").append(i).append("); !@x p(x) -> q").append(i).append("(x);\n");
                sparse.append("!p").append(i).append("(1); !@x p").append(i).append("(x) -> q").append(i).append("(x);\n");
            }
            measure("dense-" + size, dense.toString(), output);
            measure("sparse-" + size, sparse.toString(), output);
        }
        Files.write(Paths.get(args[0]), output, StandardCharsets.UTF_8);
        System.out.println("FACTORY_FOOTPRINT_PASS");
    }
    private static void measure(String label, String source, List<String> output) throws Exception {
        User user = new User(); new UDF().init(user); Mind mind = new Mind(user);
        if (!source.isEmpty() && !mind.compile(source)) throw new AssertionError(label);
        mind.getRules().prepareLatentIndex();
        Object index = field(mind.getRules(), "candidateIndex");
        Map<?, ?> occurrences = (Map<?, ?>) field(index, "occurrences");
        Object journals = field(index, "occurrenceJournals");
        // Account before collection views are created by this diagnostic.
        long[] footprint = graph(occurrences, journals);
        long buckets = 0, slots = 0;
        for (Object rows : occurrences.values())
            for (Object ids : ((Map<?, ?>) field(rows, "rows")).values()) {
                buckets++; slots += ((long[]) ids).length;
            }
        List<Rule> rules = new ArrayList<>();
        for (IRule rule : mind.getRules()) if (occurrences.containsKey(rule.getId())) rules.add((Rule) rule);
        if (rules.size() != occurrences.size()) throw new AssertionError("Rule coverage");
        Constructor<?> constructor = Class.forName("org.kanger.factory.RuleCandidateIndex$OccurrenceRows")
                .getDeclaredConstructor(Rule.class);
        constructor.setAccessible(true);
        long[] samples = new long[21];
        for (int sample = -5; sample < samples.length; sample++) {
            long start = System.nanoTime();
            Map<Long, Object> rebuilt = new HashMap<>();
            for (Rule rule : rules) rebuilt.put(rule.getId(), constructor.newInstance(rule));
            long elapsed = System.nanoTime() - start;
            if (sample >= 0) samples[sample] = elapsed;
            for (Rule rule : rules) {
                Map<?, ?> oldRows = (Map<?, ?>) field(occurrences.get(rule.getId()), "rows");
                Map<?, ?> newRows = (Map<?, ?>) field(rebuilt.get(rule.getId()), "rows");
                if (!oldRows.keySet().equals(newRows.keySet())) throw new AssertionError("Keys");
                for (Object key : oldRows.keySet())
                    if (!Arrays.equals((long[]) oldRows.get(key), (long[]) newRows.get(key)))
                        throw new AssertionError("Order or IDs");
            }
        }
        Arrays.sort(samples);
        output.add(label + "," + rules.size() + "," + buckets + "," + slots + ","
                + footprint[0] + "," + footprint[1] + "," + footprint[2] + "," + samples[10]);
        System.err.println("FOOTPRINT_COMPLETE " + label);
    }
}
