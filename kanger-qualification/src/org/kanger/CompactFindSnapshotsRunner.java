package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.storage.Escalera;
import org.kanger.units.Term;

/** Snapshot ownership, reentrant index mutation, ordering and custom cache fallback. */
public final class CompactFindSnapshotsRunner {
    static int checks;
    static void check(boolean b) { if (!b) throw new AssertionError("check " + checks); checks++; }
    static List<Long> list(Iterable<Long> values) {
        List<Long> result = new ArrayList<>();
        for (Long id : values) result.add(id);
        return result;
    }
    static final class CollisionTerm extends Term {
        CollisionTerm(long id) { setId(id); }
        @Override public int getHash() { return 42; }
    }
    static final class CustomCache extends Escalera {
        int calls; RuntimeException failure;
        final Set<Long> result = new LinkedHashSet<>(Arrays.asList(9L, 3L));
        CustomCache(Mind mind) { super(mind,"dictionary",null); }
        @Override public Set<Long> find(int hash) {
            calls++;
            if (failure != null) throw failure;
            return result;
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("compact-find-").toString());
        Mind mind = new Mind(new User());
        Escalera cache = new Escalera(mind,"dictionary",null);
        Iterable<Long> empty = Escalera.findCandidates(cache,42);
        check(list(empty).isEmpty());
        Set<Long> publicEmpty = cache.find(42); publicEmpty.add(999L);
        check(cache.find(42).isEmpty());
        cache.add(new CollisionTerm(1));
        check(list(empty).isEmpty());
        Iterable<Long> singleton = Escalera.findCandidates(cache,42);
        check(list(singleton).equals(Arrays.asList(1L)));
        Set<Long> publicOne = cache.find(42); publicOne.clear();
        check(cache.find(42).equals(new HashSet<>(Arrays.asList(1L))));
        cache.add(new CollisionTerm(17));
        check(list(singleton).equals(Arrays.asList(1L)));
        Iterable<Long> pair = Escalera.findCandidates(cache,42);
        check(list(pair).equals(list(cache.find(42))));
        cache.add(new CollisionTerm(33));
        check(list(pair).size()==2);
        cache.mark();
        for (long id=49; id<1000; id+=16) cache.add(new CollisionTerm(id));
        List<Long> expected = list(cache.find(42));
        Iterable<Long> many = Escalera.findCandidates(cache,42);
        check(list(many).equals(expected));
        cache.release();
        check(list(many).equals(expected));
        check(list(Escalera.findCandidates(cache,42)).equals(list(cache.find(42))));
        check(cache.find(42).size()==3);
        // Simulate callbacks that add colliding candidates while consuming a snapshot.
        Iterable<Long> beforeCallback = Escalera.findCandidates(cache,42);
        List<Long> visited = new ArrayList<>();
        for (Long id : beforeCallback) {
            visited.add(id);
            cache.add(new CollisionTerm(id+2000));
        }
        check(visited.equals(list(beforeCallback)));
        check(visited.size()==3); check(cache.find(42).size()==6);
        cache.setRoot(null);
        check(list(beforeCallback).equals(visited));
        check(list(Escalera.findCandidates(cache,42)).isEmpty());
        check(list(Escalera.findCandidates(cache,Integer.MIN_VALUE)).isEmpty());
        CustomCache custom = new CustomCache(mind);
        Iterable<Long> returned = Escalera.findCandidates(custom,42);
        check(returned==custom.result); check(custom.calls==1);
        check(list(returned).equals(Arrays.asList(9L,3L)));
        RuntimeException failure = new IllegalStateException("custom find failure");
        custom.failure=failure;
        try { Escalera.findCandidates(custom,42); throw new AssertionError("missing failure"); }
        catch (RuntimeException actual) { check(actual==failure); }
        check(custom.calls==2);
        System.out.println("COMPACT_FIND_SNAPSHOTS_OK checks="+checks);
    }
}
