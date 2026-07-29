/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.CommentFactory;
import org.kanger.factory.DictionaryFactory;
import org.kanger.factory.DomainFactory;
import org.kanger.factory.FValueFactory;
import org.kanger.factory.FunctionFactory;
import org.kanger.factory.LibraryFactory;
import org.kanger.factory.PredicateFactory;
import org.kanger.factory.RuleFactory;
import org.kanger.factory.TValueFactory;
import org.kanger.factory.TVariableFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IBase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic-only observability for storage and transaction lifecycle.
 *
 * <p>The class does not participate in logical inference and is never
 * serialized. All backend-specific counters are discovered reflectively so
 * other IBase implementations remain source and binary compatible.</p>
 */
public final class Diagnostics {

    private static final String[] SCHEMAS = new String[]{
            DictionaryFactory.SCHEMA,
            PredicateFactory.SCHEMA,
            DomainFactory.SCHEMA,
            RuleFactory.SCHEMA,
            FunctionFactory.SCHEMA,
            LibraryFactory.SCHEMA,
            TVariableFactory.SCHEMA,
            TValueFactory.SCHEMA,
            FValueFactory.SCHEMA,
            CommentFactory.SCHEMA
    };

    private Diagnostics() {
    }

    public static boolean isEnabled(IMind mind) {
        return Boolean.parseBoolean(System.getProperty("kanger.diagnostics", "false"))
                || (mind != null && mind.isStorageUsed());
    }

    public static long timeoutMillis() {
        try {
            return Math.max(1000L, Long.parseLong(
                    System.getProperty("kanger.diagnostics.timeout.ms", "10000")));
        } catch (NumberFormatException ignored) {
            return 10000L;
        }
    }

    public static String snapshot(IMind mind, String label) {
        StringBuilder out = new StringBuilder();
        out.append("\n========== KANGER DIAGNOSTICS: ")
                .append(label == null ? "snapshot" : label)
                .append(" ==========\n");
        out.append("time: ").append(new Date()).append('\n');
        out.append("thread: ").append(Thread.currentThread().getName()).append('\n');

        if (mind == null) {
            out.append("mind: null\n");
            out.append("====================================================\n");
            return out.toString();
        }

        out.append("mind.id: ").append(mind.getId()).append('\n');
        out.append("transaction.level: ").append(mind.getTransactionLevel()).append('\n');
        out.append("transaction.chain: ").append(transactionChain(mind)).append('\n');
        out.append("root.open.transactions: ").append(rootTransactionCounter(mind)).append('\n');
        out.append("storage.used: ").append(mind.isStorageUsed()).append('\n');
        out.append("storage.name: ").append(mind.getStorageName()).append('\n');

        User user = (User) mind.getUser();
        if (!user.isClosed()) {
            try {
                out.append("storage.backend: ").append(user.getData().getDescription()).append('\n');
            } catch (Exception error) {
                out.append("storage.backend: <error: ").append(error).append(">\n");
            }

            long used = 0L;
            long max = 0L;
            long requests = 0L;
            long hits = 0L;
            long misses = 0L;
            long reads = 0L;
            long writes = 0L;
            long deletes = 0L;
            long flushes = 0L;

            out.append("storage.schemas:\n");
            for (String schema : SCHEMAS) {
                IBase base = user.getStorage(schema);
                if (base == null) {
                    continue;
                }
                used += base.getUsedCacheSize();
                max += base.getMaxCacheSize();
                long schemaRequests = metric(base, "getReadRequestCount");
                long schemaHits = metric(base, "getCacheHitCount");
                long schemaMisses = metric(base, "getCacheMissCount");
                long schemaReads = metric(base, "getStorageReadCount");
                long schemaWrites = metric(base, "getWriteCount");
                long schemaDeletes = metric(base, "getDeleteCount");
                long schemaFlushes = metric(base, "getFlushCount");
                requests += positive(schemaRequests);
                hits += positive(schemaHits);
                misses += positive(schemaMisses);
                reads += positive(schemaReads);
                writes += positive(schemaWrites);
                deletes += positive(schemaDeletes);
                flushes += positive(schemaFlushes);

                out.append("  ").append(schema)
                        .append(": cache=").append(base.getUsedCacheSize())
                        .append('/').append(base.getMaxCacheSize());
                if (schemaRequests >= 0L) {
                    out.append(", get=").append(schemaRequests)
                            .append(", hit=").append(schemaHits)
                            .append(", miss=").append(schemaMisses)
                            .append(", physical-read=").append(schemaReads)
                            .append(", write=").append(schemaWrites)
                            .append(", delete=").append(schemaDeletes)
                            .append(", flush=").append(schemaFlushes);
                }
                out.append('\n');
            }
            out.append("storage.total.cache: ").append(used).append('/').append(max).append('\n');
            out.append("storage.total.get: ").append(requests).append('\n');
            out.append("storage.total.cache.hit: ").append(hits).append('\n');
            out.append("storage.total.cache.miss: ").append(misses).append('\n');
            out.append("storage.total.physical.read: ").append(reads).append('\n');
            out.append("storage.total.write: ").append(writes).append('\n');
            out.append("storage.total.delete: ").append(deletes).append('\n');
            out.append("storage.total.flush: ").append(flushes).append('\n');
        }

        out.append("====================================================\n");
        return out.toString();
    }

    public static void resetStorageCounters(IMind mind) {
        if (mind == null || !mind.isStorageUsed()) {
            return;
        }
        User user = (User) mind.getUser();
        for (String schema : SCHEMAS) {
            IBase base = user.getStorage(schema);
            if (base != null) {
                invokeNoArg(base, "resetDiagnosticCounters");
            }
        }
    }

    public static Watchdog watch(String operation, IMind mind) {
        return new Watchdog(operation, mind, isEnabled(mind), timeoutMillis());
    }

    public static String threadDump() {
        StringBuilder out = new StringBuilder();
        out.append("\n========== JVM THREAD DUMP ==========\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            out.append('"').append(thread.getName()).append('"')
                    .append(" id=").append(thread.getId())
                    .append(" state=").append(thread.getState())
                    .append('\n');
            for (StackTraceElement frame : entry.getValue()) {
                out.append("    at ").append(frame).append('\n');
            }
            out.append('\n');
        }
        out.append("=====================================\n");
        return out.toString();
    }

    private static String transactionChain(IMind mind) {
        StringBuilder chain = new StringBuilder();
        for (IMind current = mind; current != null; current = current.getNext()) {
            if (chain.length() > 0) {
                chain.append(" -> ");
            }
            chain.append(current.getId());
        }
        return chain.toString();
    }

    private static long rootTransactionCounter(IMind mind) {
        IMind root = mind;
        while (root.getNext() != null) {
            root = root.getNext();
        }
        try {
            Field field = Mind.class.getDeclaredField("transactionCounter");
            field.setAccessible(true);
            return field.getLong(root);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static long metric(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).longValue() : -1L;
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static void invokeNoArg(Object target, String methodName) {
        try {
            target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ignored) {
            // Optional diagnostic contract.
        }
    }

    private static long positive(long value) {
        return value < 0L ? 0L : value;
    }

    public static final class Watchdog implements AutoCloseable {
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final Thread thread;

        private Watchdog(final String operation,
                         final IMind mind,
                         boolean enabled,
                         final long timeoutMillis) {
            if (!enabled) {
                thread = null;
                return;
            }
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(timeoutMillis);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                    if (!completed.get()) {
                        System.err.println(snapshot(mind,
                                "WATCHDOG timeout after " + timeoutMillis + " ms: " + operation));
                        System.err.println(threadDump());
                    }
                }
            }, "kanger-diagnostics-watchdog");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() {
            completed.set(true);
            if (thread != null) {
                thread.interrupt();
            }
        }
    }
}
