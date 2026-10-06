package org.kanger;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Temporary optimization-thread census. Results never control production paths. */
public final class StoredLookupProfile {
    public static final String[] COLUMNS = {"calls", "true", "false", "exceptions", "exact_domain",
        "exact_mind", "primary_find", "nested_find", "candidate_visits", "equals_true", "equals_false",
        "find_hit", "find_miss", "find_exception", "empty_lookup", "hit_but_not_stored",
        "repeat_same_domain_context", "repeat_same_result", "repeat_changed_result", "true_to_false",
        "false_to_true", "exact_factory_find", "exact_rule_candidates", "null_candidates",
        "changed_context", "arguments_overload", "within_link_scope"};
    private static Thread owner;
    private static int site;
    private static LinkFrame link;
    private static StoredFrame stored;
    private static FindFrame find;
    private static final Map<Integer, Stats> sites = new TreeMap<>();
    private static final Map<Integer, long[]> transitions = new TreeMap<>();
    private static long links, nonemptyLinks, firstObservations;
    private static final class Stats {
        final long[] c = new long[COLUMNS.length];
        final Map<Long, Long> lengths = new TreeMap<>();
    }
    private static final class Seen {
        Mind context; boolean result; int site;
        Seen(Mind context, boolean result, int site) { this.context = context; this.result = result; this.site = site; }
    }
    public static final class LinkFrame {
        final LinkFrame previous;
        IdentityHashMap<Domain, Seen> seen;
        LinkFrame(LinkFrame previous) { this.previous = previous; }
    }
    public static final class StoredFrame {
        final StoredFrame previous;
        final Stats stats;
        final int tag;
        final Domain domain;
        final Mind context;
        final LinkFrame linkScope;
        boolean complete, result, primaryHit;
        StoredFrame(StoredFrame previous, Stats stats, int tag, Domain domain, Mind context, LinkFrame linkScope) {
            this.previous = previous; this.stats = stats; this.tag = tag; this.domain = domain;
            this.context = context; this.linkScope = linkScope;
        }
    }
    public static final class FindFrame {
        final FindFrame previous;
        final StoredFrame scope;
        final boolean primary;
        long visits;
        boolean complete, hit;
        FindFrame(FindFrame previous, StoredFrame scope, boolean primary) {
            this.previous = previous; this.scope = scope; this.primary = primary;
        }
    }
    private static boolean enabled() { return owner == Thread.currentThread(); }
    public static void begin() {
        sites.clear(); transitions.clear(); links = nonemptyLinks = firstObservations = 0;
        site = 0; link = null; stored = null; find = null; owner = Thread.currentThread();
    }
    public static boolean atSite(Domain domain, Mind context, int tag) throws Exception {
        if (!enabled()) return domain.isStored(context);
        int previous = site; site = tag;
        try { return domain.isStored(context); } finally { site = previous; }
    }
    public static LinkFrame beginLink() {
        if (!enabled()) return null;
        ++links; link = new LinkFrame(link); return link;
    }
    public static void endLink(LinkFrame frame) {
        if (frame == null) return;
        if (link != frame) throw new AssertionError("Link scope mismatch");
        if (frame.seen != null) ++nonemptyLinks;
        link = frame.previous;
    }
    public static StoredFrame beginStored(Domain domain, Mind context, boolean argumentsOverload) {
        if (!enabled()) return null;
        Stats stats = sites.get(site);
        if (stats == null) { stats = new Stats(); sites.put(site, stats); }
        ++stats.c[0];
        if (domain.getClass() == Domain.class) ++stats.c[4];
        if (context != null && context.getClass() == Mind.class) ++stats.c[5];
        if (argumentsOverload) ++stats.c[25];
        if (link != null) ++stats.c[26];
        stored = new StoredFrame(stored, stats, site, domain, context, link);
        return stored;
    }
    public static boolean storedResult(boolean result) {
        if (enabled() && stored != null) { stored.complete = true; stored.result = result; }
        return result;
    }
    public static void endStored(StoredFrame frame) {
        if (frame == null) return;
        if (stored != frame) throw new AssertionError("Stored scope mismatch");
        long[] c = frame.stats.c;
        if (!frame.complete) ++c[3];
        else {
            ++c[frame.result ? 1 : 2];
            if (frame.primaryHit && !frame.result) ++c[15];
            LinkFrame current = frame.linkScope;
            if (current != null) {
                if (current.seen == null) current.seen = new IdentityHashMap<>();
                Seen previous = current.seen.get(frame.domain);
                if (previous == null) {
                    ++firstObservations;
                    current.seen.put(frame.domain, new Seen(frame.context, frame.result, frame.tag));
                } else {
                    if (previous.context == frame.context) {
                        ++c[16]; ++c[previous.result == frame.result ? 17 : 18];
                        if (previous.result != frame.result) ++c[previous.result ? 19 : 20];
                        int key = previous.site * 100 + frame.tag;
                        long[] pair = transitions.get(key);
                        if (pair == null) { pair = new long[2]; transitions.put(key, pair); }
                        ++pair[previous.result == frame.result ? 0 : 1];
                    } else ++c[24];
                    previous.context = frame.context; previous.result = frame.result; previous.site = frame.tag;
                }
            }
        }
        stored = frame.previous;
    }
    public static FindFrame beginFind(RuleFactory factory) {
        if (!enabled() || stored == null) return null;
        boolean primary = find == null || find.scope != stored;
        find = new FindFrame(find, stored, primary);
        ++stored.stats.c[primary ? 6 : 7];
        if (primary && factory.getClass() == RuleFactory.class) ++stored.stats.c[21];
        return find;
    }
    public static void visit() {
        if (enabled() && find != null) { ++find.visits; if (find.primary) ++find.scope.stats.c[8]; }
    }
    public static void candidate(IRule rule) {
        if (!enabled() || find == null || !find.primary) return;
        if (rule == null) ++find.scope.stats.c[23];
        else if (rule.getClass() == Rule.class) ++find.scope.stats.c[22];
    }
    public static boolean comparison(boolean value) {
        if (enabled() && find != null && find.primary) ++find.scope.stats.c[value ? 9 : 10];
        return value;
    }
    public static void findResult(boolean hit) {
        if (enabled() && find != null) { find.complete = true; find.hit = hit; }
    }
    public static void endFind(FindFrame frame) {
        if (frame == null) return;
        if (find != frame) throw new AssertionError("Find scope mismatch");
        if (frame.primary) {
            Stats stats = frame.scope.stats;
            if (!frame.complete) ++stats.c[13];
            else {
                ++stats.c[frame.hit ? 11 : 12];
                if (!frame.hit && frame.visits == 0) ++stats.c[14];
                if (frame.hit) frame.scope.primaryHit = true;
            }
            Long count = stats.lengths.get(frame.visits);
            stats.lengths.put(frame.visits, count == null ? 1 : count + 1);
        }
        find = frame.previous;
    }
    public static void finish(int sample) {
        if (!enabled() || link != null || stored != null || find != null || site != 0)
            throw new AssertionError("Unbalanced census");
        owner = null;
        System.out.println("STORED_GLOBAL " + sample + " " + Arrays.toString(new long[]{links, nonemptyLinks, firstObservations}));
        for (Map.Entry<Integer,Stats> entry : sites.entrySet()) {
            System.out.println("STORED_SITE " + sample + " " + entry.getKey() + " " + Arrays.toString(entry.getValue().c));
            for (Map.Entry<Long,Long> length : entry.getValue().lengths.entrySet())
                System.out.println("STORED_HIST " + sample + " " + entry.getKey() + " " + length.getKey() + " " + length.getValue());
        }
        for (Map.Entry<Integer,long[]> entry : transitions.entrySet())
            System.out.println("STORED_PAIR " + sample + " " + (entry.getKey()/100) + " " + (entry.getKey()%100) + " " + Arrays.toString(entry.getValue()));
    }
}
