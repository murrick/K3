package org.kanger;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import org.kanger.enums.UnitType;
import org.kanger.units.Rule;

/** Read authority, ownership, callback order and subclass getter fallback. */
public final class InternalUnitStateReadsRunner {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("check " + checks);
        checks++;
    }
    private static final class SpyMind extends Mind {
        final Map<UnitType, Set<Long>> deletes = new LinkedHashMap<>();
        final Map<UnitType, Set<Long>> restores = new LinkedHashMap<>();
        int deleteCalls, restoreCalls;
        Rule watched;
        RuntimeException failure;
        SpyMind() throws Exception { super(new User()); }
        @Override public Map<UnitType, Set<Long>> getDeleted() {
            deleteCalls++;
            return deletes;
        }
        @Override public Map<UnitType, Set<Long>> getRestored() {
            restoreCalls++;
            if (watched != null) watched.setId(restoreCalls == 1 ? 10 : 20);
            if (failure != null && restoreCalls == 2) throw failure;
            return restores;
        }
    }
    private static final class TracedIds extends LinkedHashSet<Long> {
        final List<String> events;
        RuntimeException failure;
        TracedIds(List<String> events) { this.events = events; add(9L); add(3L); }
        @Override public int size() { events.add("size"); return super.size(); }
        @Override public Iterator<Long> iterator() {
            events.add("iterator");
            if (failure != null) throw failure;
            return super.iterator();
        }
        @Override public boolean contains(Object value) {
            events.add("contains:" + value);
            if (failure != null) throw failure;
            return super.contains(value);
        }
    }
    private static final class RetainingTarget extends ArrayList<Long> {
        Collection<? extends Long> retained;
        @Override public boolean addAll(Collection<? extends Long> values) {
            retained = values;
            return super.addAll(values);
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("unit-state-reads-").toString());
        Mind mind = new Mind(new User());
        check(mind.usesInternalUnitStateReads() == Boolean.getBoolean("kanger.experiment.internalUnitStateReads"));
        Map<UnitType, Set<Long>> deleted = mind.getDeleted(), restored = mind.getRestored();
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9L, 3L));
        deleted.put(UnitType.TERM, ids);
        LinkedHashSet<Long> target = new LinkedHashSet<>(Arrays.asList(1L));
        mind.appendLocalDeletedIds(UnitType.TERM, target);
        check(new ArrayList<>(target).equals(Arrays.asList(1L,9L,3L)));
        Set<Long> copy = mind.copyLocalUnitIds(UnitType.TERM, true);
        check(new ArrayList<>(copy).equals(Arrays.asList(9L,3L)));
        copy.clear(); check(ids.size()==2);
        ids.add(7L);
        check(mind.copyLocalUnitIds(UnitType.TERM,true).contains(7L));
        deleted.clear(); target.clear(); mind.appendLocalDeletedIds(UnitType.TERM,target);
        check(target.isEmpty()); check(mind.copyLocalUnitIds(UnitType.TERM,true).isEmpty());
        deleted.put(UnitType.TERM,null); mind.appendLocalDeletedIds(UnitType.TERM,target);
        check(target.isEmpty()); check(mind.copyLocalUnitIds(UnitType.TERM,true).isEmpty());
        check(!mind.isLocalUnitRestored(UnitType.RULE,9));
        restored.put(UnitType.RULE,new LinkedHashSet<>(Arrays.asList(9L)));
        check(mind.isLocalUnitRestored(UnitType.RULE,9));
        restored.get(UnitType.RULE).clear(); check(!mind.isLocalUnitRestored(UnitType.RULE,9));
        restored.put(UnitType.RULE,null);
        try { mind.isLocalUnitRestored(UnitType.RULE,9); throw new AssertionError("null set accepted"); }
        catch (NullPointerException expected) { check(true); }
        List<String> events = new ArrayList<>();
        TracedIds traced = new TracedIds(events);
        deleted.put(UnitType.TERM,traced); restored.put(UnitType.RULE,traced);
        events.clear(); Set<Long> expectedCopy = new LinkedHashSet<>(traced);
        List<String> expectedEvents = new ArrayList<>(events);
        events.clear(); Set<Long> actualCopy = mind.copyLocalUnitIds(UnitType.TERM,true);
        check(events.equals(expectedEvents)); check(new ArrayList<>(actualCopy).equals(new ArrayList<>(expectedCopy)));
        events.clear(); LinkedHashSet<Long> expectedTarget = new LinkedHashSet<>(); expectedTarget.addAll(traced);
        expectedEvents = new ArrayList<>(events);
        events.clear(); target.clear(); mind.appendLocalDeletedIds(UnitType.TERM,target);
        check(events.equals(expectedEvents)); check(target.equals(expectedTarget));
        events.clear(); check(mind.isLocalUnitRestored(UnitType.RULE,9));
        check(events.equals(Arrays.asList("contains:9")));
        RuntimeException failure = new IllegalStateException("source callback failure"); traced.failure=failure;
        try { mind.copyLocalUnitIds(UnitType.TERM,true); throw new AssertionError("missing copy failure"); }
        catch (RuntimeException actual) { check(actual==failure); }
        try { mind.appendLocalDeletedIds(UnitType.TERM,target); throw new AssertionError("missing append failure"); }
        catch (RuntimeException actual) { check(actual==failure); }
        try { mind.isLocalUnitRestored(UnitType.RULE,9); throw new AssertionError("missing contains failure"); }
        catch (RuntimeException actual) { check(actual==failure); }
        traced.failure=null;
        RetainingTarget customTarget = new RetainingTarget(); mind.appendLocalDeletedIds(UnitType.TERM,customTarget);
        check(customTarget.retained==traced); // This fallback deliberately accounts for exposure.
        check(mind.getDeleted()==deleted); check(mind.getRestored()==restored);
        SpyMind spy = new SpyMind(); check(!spy.usesInternalUnitStateReads());
        spy.deletes.put(UnitType.TERM,new LinkedHashSet<>(Arrays.asList(2L)));
        spy.appendLocalDeletedIds(UnitType.TERM,new LinkedHashSet<Long>()); check(spy.deleteCalls==1);
        spy.copyLocalUnitIds(UnitType.TERM,true); check(spy.deleteCalls==2);
        Rule rule = new Rule(); rule.setId(9);
        check(!rule.isRestored(spy)); check(spy.restoreCalls==1);
        spy.restoreCalls=0; spy.restores.put(UnitType.RULE,new LinkedHashSet<>(Arrays.asList(20L))); spy.watched=rule;
        check(rule.isRestored(spy)); check(spy.restoreCalls==2); check(rule.getId()==20);
        spy.restoreCalls=0; spy.failure=failure;
        try { rule.isRestored(spy); throw new AssertionError("missing getter failure"); }
        catch (RuntimeException actual) { check(actual==failure); }
        check(spy.restoreCalls==2);
        SpyMind pack = new SpyMind(); pack.getTerms().pack(); pack.deleteCalls=0; pack.getTerms().pack();
        check(pack.deleteCalls==1);
        SpyMind child = new SpyMind(); child.deletes.put(UnitType.RULE,new LinkedHashSet<>(Arrays.asList(7L)));
        child.restores.put(UnitType.RULE,new LinkedHashSet<>(Arrays.asList(9L)));
        Mind parent = new Mind(new User());
        Method merge=Mind.class.getDeclaredMethod("mergeUnitState",Mind.class); merge.setAccessible(true); merge.invoke(parent,child);
        check(child.deleteCalls==2); check(child.restoreCalls==2);
        check(parent.getDeleted().get(UnitType.RULE).contains(7L)); check(parent.getRestored().get(UnitType.RULE).contains(9L));
        System.out.println("INTERNAL_UNIT_STATE_READS_OK checks="+checks);
    }
}
