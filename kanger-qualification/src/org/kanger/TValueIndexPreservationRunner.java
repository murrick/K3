/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import org.kanger.storage.Escalera;
import org.kanger.units.*;

/** Guard/fallback qualification using canonical TValue objects. */
public final class TValueIndexPreservationRunner {
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("tvalue-preserve-").toString());
        System.setProperty("kanger.experiment.preserveTValueIndex", "true");
        Mind mind = new Mind(new User());
        Escalera cache = (Escalera) field(mind.getTValues(), "cache");
        cache.containsKey(-1); cache.mark(); cache.release();
        require((Boolean) field(cache, "indexValid"), "empty preservation");
        TVariable variable = mind.getTVars().createTVar(new Rule(mind), mind.getTerms().add("x"));
        TValue first = mind.getTValues().add(variable, mind.getTerms().add(1));
        cache.containsKey(first.getId());
        cache.mark(); cache.mark(); cache.release(); cache.release();
        require((Boolean) field(cache, "indexValid"), "nested no-change preservation");
        cache.mark();
        TValue second = mind.getTValues().add(variable, mind.getTerms().add(2));
        cache.release();
        require(!(Boolean) field(cache, "indexValid"), "changed-root fallback");
        require(cache.get(second.getId()) == null && cache.get(first.getId()) == first, "rollback visibility");
        cache.mark(); cache.setRoot(cache.getRoot()); cache.release();
        require(!(Boolean) field(cache, "indexValid"), "same-root explicit mutation fallback");
        cache.mark(); cache.release();
        require(!(Boolean) field(cache, "indexValid"), "already-invalid fallback");
        cache.containsKey(first.getId());
        cache.mark(); cache.mark(); cache.commit(); cache.release();
        require((Boolean) field(cache, "indexValid"), "nested commit without mutation");
        first.setDeleted(true, mind);
        require(mind.getTValues().find(variable, mind.getTerms().add(1)) == first, "deleted canonical lookup");
        require(mind.getTValues().add(variable, mind.getTerms().add(1)) == first, "resurrection identity");
        require(!first.isDeleted(mind), "resurrection deletion state");
        Escalera other = new Escalera(mind, "terms", null);
        other.containsKey(-1); other.mark(); other.release();
        require(!(Boolean) field(other, "indexValid"), "other-schema fallback");
        if (Boolean.getBoolean("kanger.experiment.verifyTValueIndex")) {
            cache.containsKey(first.getId());
            cache.mark();
            java.util.Map<?, ?> hashes = (java.util.Map<?, ?>) field(cache, "idsByHash");
            hashes.clear(); // Deliberate diagnostic fault: verifier must detect, never repair.
            boolean rejected = false;
            try { cache.release(); } catch (AssertionError expected) { rejected = true; }
            require(rejected && hashes.isEmpty(), "verifier failed to detect corruption without repair");
        }
        System.out.println("TVALUE_INDEX_PRESERVATION_PASS");
    }
}
