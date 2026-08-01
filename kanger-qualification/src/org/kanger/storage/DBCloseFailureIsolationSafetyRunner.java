/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Focused invariant gate for exception-isolated DUMB generation close. */
public final class DBCloseFailureIsolationSafetyRunner {

    private DBCloseFailureIsolationSafetyRunner() {
    }

    public static void main(String[] args) throws Exception {
        DB db = new DB();
        db.use("close-failure-generation");

        ProbeBase first = new ProbeBase("first", false);
        ProbeBase failing = new ProbeBase("failing", true);
        ProbeBase last = new ProbeBase("last", false);

        Map<String, IBase> registry = new LinkedHashMap<String, IBase>();
        registry.put(first.getName(), first);
        registry.put(failing.getName(), failing);
        registry.put(last.getName(), last);
        setRegistry(db, registry);

        Exception observed = null;
        try {
            db.close();
        } catch (Exception expected) {
            observed = expected;
        }

        require(observed != null,
                "The injected IBase.close failure must be propagated");
        require(first.closeCalls == 1,
                "The base before the failure must be closed exactly once");
        require(failing.closeCalls == 1,
                "The failing base must be attempted exactly once");
        require(last.closeCalls == 1,
                "A base after the failure must still be closed");

        Map<String, IBase> afterFailure = getRegistry(db);
        require(afterFailure.size() == 1
                        && afterFailure.get(failing.getName()) == failing,
                "Only the base that failed to close may remain registered");
        require(!db.isClosed(),
                "A generation with one failed base must remain retryable");
        require("close-failure-generation".equals(db.getStorageName()),
                "The generation name must remain published until retry succeeds");

        db.close();

        require(first.closeCalls == 1,
                "A successfully closed base must not be revisited on retry");
        require(failing.closeCalls == 2,
                "The failed base must be retried exactly once");
        require(last.closeCalls == 1,
                "A successfully closed trailing base must not be revisited");
        require(db.isClosed(),
                "A successful retry must empty the generation registry");
        require(db.getStorageName().isEmpty(),
                "A fully closed generation must clear its published name");

        System.out.println("DUMB close failure isolation invariant: success");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, IBase> getRegistry(DB db) throws Exception {
        Field field = DB.class.getDeclaredField("bases");
        field.setAccessible(true);
        return (Map<String, IBase>) field.get(db);
    }

    private static void setRegistry(DB db, Map<String, IBase> registry)
            throws Exception {
        Field field = DB.class.getDeclaredField("bases");
        field.setAccessible(true);
        field.set(db, registry);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ProbeBase implements IBase {
        private final String name;
        private boolean failNextClose;
        private int closeCalls;

        private ProbeBase(String name, boolean failNextClose) {
            this.name = name;
            this.failNextClose = failNextClose;
        }

        @Override
        public void add(IStep one) {
        }

        @Override
        public void update(IStep one) {
        }

        @Override
        public IStep get(long id) {
            return null;
        }

        @Override
        public void clearCache() {
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public void deleteAll(Collection<Long> ids) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void reindex(IBase to, IMind mind) {
        }

        @Override
        public boolean containsKey(long id) {
            return false;
        }

        @Override
        public IStep getRoot() {
            return null;
        }

        @Override
        public IStep getTop() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getUsedCacheSize() {
            return 0L;
        }

        @Override
        public long getMaxCacheSize() {
            return 0L;
        }

        @Override
        public long lastId() {
            return 0L;
        }

        @Override
        public long nextId() {
            return 0L;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws Exception {
            ++closeCalls;
            if (failNextClose) {
                failNextClose = false;
                throw new Exception("injected close failure: " + name);
            }
        }

        @Override
        public Class getUdf() {
            return null;
        }
    }
}
