/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.lang.instrument.Instrumentation;

/** Offline measurement only; never participates in candidate selection. */
public final class LatentSubstitutionTopologyRunner {
    private static Instrumentation instrumentation;
    public static void premain(String args, Instrumentation value) { instrumentation = value; }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-topology-").toString());
        Locale.setDefault(Locale.ROOT);
        System.out.println("case,compiled,R,S,A,St,At,C,P,density,avg,p50,p95,p99,max,P_singleton,P_with_t,index_ns,csr_payload_bytes,adjacency_retained_bytes");
        measure("empty", "");
        measure("one", "!p(1); !@x p(x) -> q(x);");
        StringBuilder dense = new StringBuilder();
        StringBuilder sparse = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            dense.append("!p(").append(i).append("); !@x p(x) -> q").append(i).append("(x);\n");
            sparse.append("!p").append(i).append("(1); !@x p").append(i).append("(x) -> q").append(i).append("(x);\n");
        }
        measure("dense-100", dense.toString());
        measure("sparse-100", sparse.toString());
        measure("symmetric-chain", "!p(A,B); !@x @y p(x,y) -> p(y,x); !@x @y p(x,y) -> q(x,y); !@x @y q(x,y) -> r(x,y);");
        for (String file : args) {
            measure(file, new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8));
        }
    }

    private static void measure(String label, String source) throws Exception {
        User user = new User();
        new UDF().init(user);
        Mind mind = new Mind(user);
        boolean compiled = source.isEmpty() || mind.compile(source);
        if (!compiled) {
            System.err.println("REJECTED_SOURCE " + label + " (excluded from topology measurements)");
            return;
        }
        Map<Long, Domain> unique = new LinkedHashMap<>();
        Set<Long> singleton = new HashSet<>();
        int rules = 0;
        for (IRule r : mind.getRules()) {
            if (r.isDeleted(mind)) continue;
            rules++;
            for (List<Domain> branch : ((Rule) r).getTree()) {
                for (Domain d : branch) unique.put(d.getId(), d);
                if (branch.size() == 1) singleton.add(branch.get(0).getId());
            }
        }
        List<Domain> succ = new ArrayList<>(), ant = new ArrayList<>();
        int st = 0, at = 0;
        for (Domain d : unique.values()) {
            if (d.isAntc()) { ant.add(d); if (d.isSubstitutable()) at++; }
            else { succ.add(d); if (d.isSubstitutable()) st++; }
        }
        // Match the exact first gate in Linker.linkDomains. No values are read.
        // CSR payload estimate excludes headers, domain dictionary and reverse index.
        long started = System.nanoTime();
        Map<Long, List<Integer>> buckets = new HashMap<>();
        for (int j = 0; j < ant.size(); j++) {
            long key = ant.get(j).getPredicateId();
            List<Integer> bucket = buckets.get(key);
            if (bucket == null) { bucket = new ArrayList<>(); buckets.put(key, bucket); }
            bucket.add(j);
        }
        int[][] adjacency = new int[succ.size()][];
        long pairs = 0, reachable = 0, withT = 0;
        for (int i = 0; i < succ.size(); i++) {
            Domain s = succ.get(i);
            List<Integer> bucket = buckets.get(s.getPredicateId());
            adjacency[i] = new int[bucket == null ? 0 : bucket.size()];
            for (int k = 0; k < adjacency[i].length; k++) adjacency[i][k] = bucket.get(k);
            pairs += adjacency[i].length;
        }
        long elapsed = System.nanoTime() - started;
        long retained = -1;
        if (instrumentation != null) {
            retained = instrumentation.getObjectSize(adjacency);
            for (int[] row : adjacency) retained += instrumentation.getObjectSize(row);
        }
        // Independent exhaustive check proves this snapshot index loses no gate pair.
        for (int i = 0; i < succ.size(); i++) {
            int found = 0;
            Domain s = succ.get(i);
            for (int j = 0; j < ant.size(); j++) {
                Domain a = ant.get(j);
                if (s.getPredicateId() != a.getPredicateId()) continue;
                if (found >= adjacency[i].length || adjacency[i][found++] != j)
                    throw new AssertionError("Lost/reordered topology edge");
                if (singleton.contains(s.getId()) || singleton.contains(a.getId())) reachable++;
                if (s.isSubstitutable() || a.isSubstitutable()) withT++;
            }
            if (found != adjacency[i].length) throw new AssertionError("Extra edge");
        }
        int[] degrees = new int[succ.size()];
        for (int i = 0; i < degrees.length; i++) degrees[i] = adjacency[i].length;
        Arrays.sort(degrees);
        long c = (long) succ.size() * ant.size();
        System.out.printf("%s,%s,%d,%d,%d,%d,%d,%d,%d,%.8f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                label, compiled, rules, succ.size(), ant.size(), st, at, c, pairs,
                c == 0 ? 0 : (double) pairs / c,
                succ.isEmpty() ? 0 : (double) pairs / succ.size(),
                percentile(degrees, .5), percentile(degrees, .95), percentile(degrees, .99),
                percentile(degrees, 1), reachable, withT, elapsed, 4L * (succ.size() + 1 + pairs), retained);
    }

    private static int percentile(int[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(p * sorted.length) - 1];
    }
}
