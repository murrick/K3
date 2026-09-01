/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.IStep;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Focused regression gate for exception atomicity while User acquires and
 * qualifies a new storage generation.
 */
public final class KangerStorageAcquisitionAtomicitySafetyRunner {

    private KangerStorageAcquisitionAtomicitySafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            String name = "storage-acquisition-atomicity-" + System.nanoTime();
            IUser user = UserFactory.createUser(name, name);
            FailingData data = new FailingData(4);
            data.init(user);
            Mind mind = new Mind(user);

            long sentinelId = mind.getTerms().add("storage_acquisition_sentinel").getId();

            boolean failureObserved = false;
            try {
                mind.useStorage("partial-storage-generation");
            } catch (InjectedStorageFailure expected) {
                failureObserved = true;
            }
            require(failureObserved,
                    "fault injection did not reach partial IBase acquisition");

            boolean storageUsed = mind.isStorageUsed();
            int publishedBases = publishedBaseCount((User) user);
            require(!storageUsed && data.closeCalls == 1 && publishedBases == 0,
                    "failed storage acquisition was not rolled back: storageUsed="
                            + storageUsed + " closeCalls=" + data.closeCalls
                            + " publishedBases=" + publishedBases);

            require(mind.getTerms().get(sentinelId) != null,
                    "failed pre-publication acquisition destroyed the existing in-memory Mind");
            assertFactoryConnectionsDetached(mind);

            data.disableFailure();
            mind = (Mind) mind.useStorage("complete-storage-generation");
            require(mind.isStorageUsed(),
                    "storage could not be opened after a rolled-back acquisition failure");
            require(publishedBaseCount((User) user) == 10,
                    "successful retry did not publish the complete schema set");
            assertFactoryConnectionsAttached(mind);

            mind = (Mind) mind.closeStorage();
            require(!mind.isStorageUsed(), "successful retry did not close cleanly");

            data.enableSemanticFailure();
            boolean semanticFailureObserved = false;
            try {
                mind.useStorage("semantic-bad-generation");
            } catch (StorageLifecycleException expected) {
                semanticFailureObserved = expected.getErrorCode()
                        == StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION
                        && expected.toString().contains("semantically inconsistent");
            }
            require(semanticFailureObserved,
                    "post-publication semantic failure was not classified");
            require(!mind.isStorageUsed(),
                    "rejected semantic generation remained active");
            require(data.closeCalls == 3,
                    "rejected semantic generation was not closed without checkpoint");
            require(publishedBaseCount((User) user) == 0,
                    "rejected semantic generation remained published");
            assertFactoryConnectionsDetached(mind);

            data.disableSemanticFailure();
            mind = (Mind) mind.useStorage("semantic-recovery-generation");
            require(mind.isStorageUsed(),
                    "session could not open a valid generation after semantic failure");
            require("semantic-recovery-generation".equals(mind.getStorageName()),
                    "semantic recovery opened an unexpected generation");
            require(publishedBaseCount((User) user) == 10,
                    "semantic recovery did not publish the complete schema set");
            assertFactoryConnectionsAttached(mind);

            mind = (Mind) mind.closeStorage();
            require(!mind.isStorageUsed(),
                    "semantic recovery generation did not close cleanly");

            System.out.println("STORAGE_ACQUISITION_ATOMICITY_PASS rollback");
            System.out.println("STORAGE_ACQUISITION_ATOMICITY_PASS retry");
            System.out.println("STORAGE_ACQUISITION_ATOMICITY_PASS semantic_classification");
            System.out.println("STORAGE_ACQUISITION_ATOMICITY_PASS semantic_discard");
            System.out.println("STORAGE_ACQUISITION_ATOMICITY_PASS semantic_retry");
            System.out.println("STORAGE_ACQUISITION_ATOMICITY_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static int publishedBaseCount(User user) throws Exception {
        Field field = User.class.getDeclaredField("storage");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(user)).size();
    }

    private static void assertFactoryConnectionsDetached(Mind mind) throws Exception {
        for (Object factory : factories(mind)) {
            require(connection(factory) == null,
                    "failed acquisition attached factory connection: "
                            + factory.getClass().getSimpleName());
        }
    }

    private static void assertFactoryConnectionsAttached(Mind mind) throws Exception {
        for (Object factory : factories(mind)) {
            require(connection(factory) != null,
                    "successful retry left factory detached: "
                            + factory.getClass().getSimpleName());
        }
    }

    private static Object connection(Object factory) throws Exception {
        Field field = factory.getClass().getDeclaredField("connection");
        field.setAccessible(true);
        return field.get(factory);
    }

    private static Object[] factories(Mind mind) {
        return new Object[]{
                mind.getTerms(), mind.getPredicates(), mind.getDomains(), mind.getRules(),
                mind.getTVars(), mind.getTValues(), mind.getFunctions(), mind.getFValues(),
                mind.getComments(), mind.getLibrary()
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class InjectedStorageFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class FailingData implements IData {
        private final Map<String, IBase> bases = new HashMap<>();
        private int failAt;
        private int acquisitionCount;
        private boolean semanticFailure;
        private boolean open;
        private String storageName = "";
        private int closeCalls;

        private FailingData(int failAt) {
            this.failAt = failAt;
        }

        private void disableFailure() {
            failAt = Integer.MAX_VALUE;
            acquisitionCount = 0;
        }

        private void enableSemanticFailure() {
            semanticFailure = true;
        }

        private void disableSemanticFailure() {
            semanticFailure = false;
        }

        @Override
        public void init(IUser user) {
            ((User) user).setData(this);
        }

        @Override
        public void use(String name) {
            open = true;
            storageName = name;
            acquisitionCount = 0;
            bases.clear();
        }

        @Override
        public void close() {
            ++closeCalls;
            open = false;
            storageName = "";
            bases.clear();
        }

        @Override
        public void flush() {
        }

        @Override
        public void remove(String name) {
        }

        @Override
        public void reindex(IReactor<String> reactor, IMind mind) {
        }

        @Override
        public boolean isClosed() {
            return !open;
        }

        @Override
        public String getStorageName() {
            return storageName;
        }

        @Override
        public IBase getBase(String context) {
            ++acquisitionCount;
            if (acquisitionCount == failAt) {
                throw new InjectedStorageFailure();
            }
            IBase base = bases.get(context);
            if (base == null) {
                base = newBase(context);
                bases.put(context, base);
            }
            return base;
        }

        @Override
        public IBase connect(String context) {
            return open ? bases.get(context) : null;
        }

        @Override
        public String getDescription() {
            return "injected storage acquisition fixture";
        }

        @Override
        public Collection<String> list() {
            return Collections.emptyList();
        }

        private IBase newBase(final String name) {
            final AtomicLong nextId = new AtomicLong();
            final IStep poison = poisonStep();
            return (IBase) Proxy.newProxyInstance(
                    IBase.class.getClassLoader(),
                    new Class<?>[]{IBase.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("getName".equals(methodName)) {
                            return name;
                        }
                        if ("isEmpty".equals(methodName)) {
                            return !(semanticFailure && RuleFactory.SCHEMA.equals(name));
                        }
                        if ("containsKey".equals(methodName)) {
                            return false;
                        }
                        if ("getRoot".equals(methodName)) {
                            return semanticFailure && RuleFactory.SCHEMA.equals(name)
                                    ? poison : null;
                        }
                        if ("getTop".equals(methodName)
                                || "get".equals(methodName)
                                || "getUdf".equals(methodName)) {
                            return null;
                        }
                        if ("lastId".equals(methodName)) {
                            return nextId.get();
                        }
                        if ("nextId".equals(methodName)) {
                            return nextId.getAndIncrement();
                        }
                        if (method.getReturnType() == long.class) {
                            return 0L;
                        }
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        return null;
                    });
        }

        private IStep poisonStep() {
            return (IStep) Proxy.newProxyInstance(
                    IStep.class.getClassLoader(),
                    new Class<?>[]{IStep.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("getId".equals(methodName)) {
                            return 0L;
                        }
                        if ("getHash".equals(methodName)) {
                            return 0;
                        }
                        if ("getNext".equals(methodName)) {
                            return null;
                        }
                        if ("getData".equals(methodName)) {
                            throw new NullPointerException(
                                    "injected semantic hydration failure");
                        }
                        if (method.getReturnType() == long.class) {
                            return 0L;
                        }
                        if (method.getReturnType() == int.class) {
                            return 0;
                        }
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        return null;
                    });
        }
    }
}
