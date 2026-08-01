/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Stack;

/**
 * Focused red regression gate for Linker's local TValue/FValue checkpoint
 * balance after a successful ground-domain match that creates no substitution.
 */
public final class KangerLinkerCheckpointBalanceSafetyRunner {

    private KangerLinkerCheckpointBalanceSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-linker-checkpoint-balance-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser(
                    "linker-checkpoint-balance", "linker-checkpoint-balance");
            new UDF().init(user);
            new DB().init(user);
            Mind parent = new Mind(user);

            require(Boolean.TRUE.equals(parent.query("!linker_checkpoint_fact(value);")),
                    "Unable to install the ground fact used by the Linker gate");

            Mind child = new Mind(parent);
            child.setQueryPass(QueryPass.CHECKTRUE);
            Rule query = (Rule) child.compileLine(
                    "?linker_checkpoint_fact(value);",
                    true,
                    new LinkedList<ITerm>());
            require(query != null && !query.isSecond(),
                    "Unable to compile a distinct ground query rule");

            FactoryDepth before = snapshot(child);
            child.link(query, false);
            FactoryDepth after = snapshot(child);

            require(after.equals(before),
                    "Linker retained local checkpoint frames: before="
                            + before + ", after=" + after);

            parent.release(child);
            System.out.println("LINKER_CHECKPOINT_BALANCE_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static FactoryDepth snapshot(Mind mind) throws Exception {
        Object tValues = mind.getTValues();
        Object fValues = mind.getFValues();
        return new FactoryDepth(
                cacheStackDepth(tValues),
                stackDepth(tValues, "additionsStack"),
                stackDepth(tValues, "actionStack"),
                cacheStackDepth(fValues),
                stackDepth(fValues, "invalidatedStack"),
                stackDepth(fValues, "actionStack"));
    }

    private static int cacheStackDepth(Object factory) throws Exception {
        Object cache = field(factory.getClass(), "cache").get(factory);
        return stackDepth(cache, "stack");
    }

    private static int stackDepth(Object owner, String name) throws Exception {
        return ((Stack<?>) field(owner.getClass(), name).get(owner)).size();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + '.' + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FactoryDepth {
        private final int tCache;
        private final int tAdditions;
        private final int tAction;
        private final int fCache;
        private final int fInvalidated;
        private final int fAction;

        private FactoryDepth(int tCache,
                             int tAdditions,
                             int tAction,
                             int fCache,
                             int fInvalidated,
                             int fAction) {
            this.tCache = tCache;
            this.tAdditions = tAdditions;
            this.tAction = tAction;
            this.fCache = fCache;
            this.fInvalidated = fInvalidated;
            this.fAction = fAction;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof FactoryDepth)) {
                return false;
            }
            FactoryDepth other = (FactoryDepth) value;
            return tCache == other.tCache
                    && tAdditions == other.tAdditions
                    && tAction == other.tAction
                    && fCache == other.fCache
                    && fInvalidated == other.fInvalidated
                    && fAction == other.fAction;
        }

        @Override
        public String toString() {
            return "T(cache=" + tCache
                    + ", additions=" + tAdditions
                    + ", action=" + tAction
                    + "), F(cache=" + fCache
                    + ", invalidated=" + fInvalidated
                    + ", action=" + fAction + ')';
        }
    }
}
