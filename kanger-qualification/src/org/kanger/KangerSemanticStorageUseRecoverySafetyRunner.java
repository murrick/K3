/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.IStep;
import org.kanger.primitives.Argument;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Focused regression gate for a storage generation that is acquired
 * physically but fails during semantic qualification.
 *
 * <p>A rejected candidate must be discarded without checkpointing it. The
 * User remains usable and can open another generation in the same session.</p>
 */
public final class KangerSemanticStorageUseRecoverySafetyRunner {

    private KangerSemanticStorageUseRecoverySafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            String name = "semantic-storage-use-recovery-" + System.nanoTime();
            IUser user = UserFactory.createUser(name, name);
            SemanticFailingData data = new SemanticFailingData();
            data.init(user);
            Mind mind = new Mind(user);

            boolean semanticFailureObserved = false;
            try {
                mind.useStorage("semantic-bad-generation");
            } catch (StorageLifecycleException expected) {
                semanticFailureObserved = expected.getErrorCode()
                        == StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION;
            }
            require(semanticFailureObserved,
                    "semantic qualification failure was not classified");
            require(!mind.isStorageUsed(),
                    "rejected semantic generation remained active");
            require(data.closeCalls == 1,
                    "rejected semantic generation was not physically closed");
            require(publishedBaseCount((User) user) == 0,
                    "rejected semantic generation remained published");
            assertFactoryConnectionsDetached(mind);

            data.disableFailure();
            mind = (Mind) mind.useStorage("recovery-generation");
            require(mind.isStorageUsed(),
                    "session could not open a valid generation after semantic failure");
            require("recovery-generation".equals(mind.getStorageName()),
                    "successful retry opened an unexpected generation");
            assertFactoryConnectionsAttached(mind);

            Argument dangling = danglingTVariable(17L);
            boolean danglingReferenceClassified = false;
            try {
                dangling.getObject(mind);
            } catch (StorageLifecycleException expected) {
                danglingReferenceClassified = expected.getErrorCode()
                        == StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION
                        && expected.toString().contains("TVARIABLE id=17");
            }
            require(danglingReferenceClassified,
                    "dangling persistent TVariable was not reported as semantic corruption");

            mind = (Mind) mind.closeStorage();
            require(!mind.isStorageUsed(),
                    "valid retry generation did not close cleanly");

            System.out.println("SEMANTIC_STORAGE_USE_RECOVERY_PASS classification");
            System.out.println("SEMANTIC_STORAGE_USE_RECOVERY_PASS discard");
            System.out.println("SEMANTIC_STORAGE_USE_RECOVERY_PASS retry");
            System.out.println("SEMANTIC_STORAGE_USE_RECOVERY_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static Argument danglingTVariable(long id) throws Exception {
        Argument argument = new Argument();
        Field idField = Argument.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.setLong(argument, id);
        Field typeField = Argument.class.getDeclaredField("type");
        typeField.setAccessible(true);
        typeField.set(argument, ArgumentType.TVARIABLE);
        return argument;
    }

    private static int publishedBaseCount(User user) throws Exception {
        Field field = User.class.getDeclaredField("storage");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(user)).size();
    }

    private static void assertFactoryConnectionsDetached(Mind mind) throws Exception {
        for (Object factory : factories(mind)) {
            require(connection(factory) == null,
                    "rejected semantic generation left factory attached: "
                            + factory.getClass().getSimpleName());
        }
    }

    private static void assertFactoryConnectionsAttached(Mind mind) throws Exception {
        for (Object factory : factories(mind)) {
            require(connection(factory) != null,
                    "valid retry left factory detached: "
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

    private static final class SemanticFailingData implements IData {
        private final Map<String, IBase> bases = new HashMap<>();
        private boolean failSemantic = true;
        private boolean open;
        private String storageName = "";
        private int closeCalls;

        private void disableFailure() {
            failSemantic = false;
        }

        @Override
        public void init(IUser user) {
            ((User) user).setData(this);
        }

        @Override
        public void use(String name) {
            open = true;
            storageName = name;
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
            return "semantic storage use recovery fixture";
        }

        @Override
        public Collection<String> list() {
            return Collections.emptyList();
        }

        private IBase newBase(final String context) {
            final AtomicLong nextId = new AtomicLong();
            final IStep poison = poisonStep();
            return (IBase) Proxy.newProxyInstance(
                    IBase.class.getClassLoader(),
                    new Class<?>[]{IBase.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("getName".equals(methodName)) {
                            return context;
                        }
                        if ("isEmpty".equals(methodName)) {
                            return !(failSemantic && RuleFactory.SCHEMA.equals(context));
                        }
                        if ("containsKey".equals(methodName)) {
                            return false;
                        }
                        if ("getRoot".equals(methodName)) {
                            return failSemantic && RuleFactory.SCHEMA.equals(context)
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
