/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

/** Full publication/open operations, including inference; not isolated index build cost. */
public final class LatentPublicationBenchmarkRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-publication-").toString());
        List<String> output = new ArrayList<>();
        output.add("facts,sample,publish_ns,reopen_ns,rules,semantic_sha256");
        for (int size : new int[]{10, 30}) {
            for (int sample = -Integer.getInteger("benchWarmups", 2);
                    sample < Integer.getInteger("benchSamples", 5); sample++) {
                User user = (User) UserFactory.createUser("publication-" + size + "-" + (sample + 10), "bench");
                new UDF().init(user); new DB().init(user);
                Mind mind = new Mind(user); user.setCurrentMind(mind);
                try {
                    mind = (Mind) mind.useStorage("publication"); user.setCurrentMind(mind);
                    StringBuilder source = new StringBuilder("!@x p(x) -> q(x); !@x q(x) -> r(x);");
                    for (int i = 0; i < size; i++) source.append("!p(").append(i).append(");");
                    require(mind.compile(source.toString()), "seed");
                    long start = System.nanoTime();
                    boolean published = mind.compile("!p(" + size + ");");
                    long publish = System.nanoTime() - start;
                    require(published, "publish");
                    String before = fingerprint(mind);
                    mind = (Mind) mind.closeStorage(); user.setCurrentMind(mind);
                    start = System.nanoTime();
                    mind = (Mind) mind.useStorage("publication");
                    long reopen = System.nanoTime() - start;
                    user.setCurrentMind(mind);
                    String after = fingerprint(mind);
                    require(before.equals(after), "reopen changed rules");
                    require(Boolean.TRUE.equals(mind.query("?r(" + size + ");")), "missing derived production");
                    require(Boolean.TRUE.equals(mind.query("?r(0);")), "missing seed production");
                    require(!Boolean.TRUE.equals(mind.query("?p(" + (size + 1) + ");")), "unexpected fact");
                    int count = 0;
                    for (IRule rule : mind.getRules()) if (!rule.isDeleted(mind)) count++;
                    if (sample >= 0) output.add(size + "," + sample + "," + publish + "," + reopen + "," + count + "," + after);
                } finally {
                    if (user.getCurrentMind() != null && user.getCurrentMind().isStorageUsed())
                        user.setCurrentMind(user.getCurrentMind().closeStorage());
                }
            }
        }
        Files.write(Paths.get(args[0]), output, StandardCharsets.UTF_8);
        System.out.println("PUBLICATION_BENCH_PASS");
    }
    private static String fingerprint(Mind mind) throws Exception {
        List<String> rows = new ArrayList<>();
        for (IRule rule : mind.getRules()) if (!rule.isDeleted(mind))
            rows.add(rule.isGenerated() + ":" + ((Rule) rule).toString(mind));
        Collections.sort(rows);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String row : rows) digest.update((row + "\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
