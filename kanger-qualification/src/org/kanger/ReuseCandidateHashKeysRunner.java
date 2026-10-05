package org.kanger;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.kanger.storage.Escalera;
import org.kanger.units.Term;

/** Boxed-key boundaries, slot replacement, live lookup ownership and key races. */
public final class ReuseCandidateHashKeysRunner {
    private static int checks;
    private static final int[] HASHES = {Integer.MIN_VALUE, Integer.MAX_VALUE, -129, -128, -1, 0, 127, 128, 256, 512, 1024, 65536};
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("check " + checks);
        checks++;
    }
    private static final class HashedTerm extends Term {
        private final int hash;
        HashedTerm(long id, int hash) { setId(id); this.hash = hash; }
        @Override public int getHash() { return hash; }
    }
    private static List<Long> values(Iterable<Long> ids) {
        List<Long> result = new ArrayList<>();
        for (Long id : ids) result.add(id);
        return result;
    }
    private static void compare(Escalera cache, int hash) throws Exception {
        check(values(Escalera.findCandidates(cache, hash)).equals(values(cache.find(hash))));
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("reuse-keys-").toString());
        Escalera cache = new Escalera(new Mind(new User()), "dictionary", null);
        Method key = Escalera.class.getDeclaredMethod("candidateHashKey", int.class);
        key.setAccessible(true);
        boolean enabled = Boolean.getBoolean("kanger.experiment.reuseCandidateHashKeys");
        Field slots = Escalera.class.getDeclaredField("candidateHashKeys");
        slots.setAccessible(true);
        check(enabled ? ((Integer[]) slots.get(cache)).length == 256 : slots.get(cache) == null);
        for (int hash : HASHES) {
            Integer first = (Integer) key.invoke(cache, hash);
            check(first.intValue() == hash);
            Integer second = (Integer) key.invoke(cache, hash);
            check(second.intValue() == hash);
            if (enabled) check(first == second);
        }
        // All these keys occupy slot zero; replacement must not return a stale key.
        for (int pass = 0; pass < 50; pass++) {
            for (int hash : new int[]{256, 512, 1024, 0, Integer.MIN_VALUE})
                check(((Integer) key.invoke(cache, hash)).intValue() == hash);
        }
        Random random = new Random(18380);
        for (int i = 0; i < 1000; i++) {
            int hash = random.nextInt();
            check(((Integer) key.invoke(cache, hash)).intValue() == hash);
        }
        long id = 1;
        for (int hash : HASHES) {
            compare(cache, hash); // Miss does not cache the absent result.
            cache.add(new HashedTerm(id++, hash));
            compare(cache, hash);
            Iterable<Long> singleton = Escalera.findCandidates(cache, hash);
            List<Long> original = values(singleton);
            Set<Long> owned = cache.find(hash);
            owned.clear();
            compare(cache, hash);
            check(values(singleton).equals(original));
            cache.mark();
            cache.add(new HashedTerm(id++, hash));
            compare(cache, hash);
            check(values(singleton).equals(original));
            cache.release();
            compare(cache, hash);
            check(cache.find(hash).size() == 1);
        }
        // Read-only map published before thread start. This is not a claim that
        // concurrent structural mutation of Escalera is supported.
        for (int hash : HASHES) compare(cache, hash);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                final int seed = worker;
                tasks.add(pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 5000; i++) {
                            int hash = HASHES[(i + seed) % HASHES.length];
                            Integer boxed = (Integer) key.invoke(cache, hash);
                            if (boxed.intValue() != hash) throw new AssertionError("wrong concurrent key");
                            if (!values(Escalera.findCandidates(cache, hash)).equals(values(cache.find(hash))))
                                throw new AssertionError("wrong concurrent candidates");
                        }
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) task.get(60, TimeUnit.SECONDS);
            check(true);
        } finally { pool.shutdownNow(); }
        cache.clear();
        for (int hash : HASHES) { compare(cache, hash); check(cache.find(hash).isEmpty()); }
        cache.add(new HashedTerm(id++, 256));
        compare(cache, 256);
        cache.setRoot(null);
        compare(cache, 256);
        check(cache.find(256).isEmpty());
        System.out.println("REUSE_CANDIDATE_HASH_KEYS_OK checks=" + checks + " concurrent_probes=20000");
    }
}
