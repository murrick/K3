/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Parallel local-only fan-out of one frontier over direct connections. */
final class FrontierFanOut {

    private FrontierFanOut() {
    }

    static List<FrontierAnswer> execute(
            OperationSnapshot operation,
            final FrontierDomain frontier) throws Exception {
        return execute(operation, FrontierInvocation.create(frontier));
    }

    static List<FrontierAnswer> execute(
            OperationSnapshot operation,
            final FrontierInvocation invocation) throws Exception {
        if (invocation == null) {
            throw new NullPointerException("invocation");
        }
        List<ContextConnection> connections =
                operation.getExecutionConnections();
        if (connections.isEmpty()) {
            return Collections.emptyList();
        }

        int workers = Math.max(
                1,
                Math.min(
                        connections.size(),
                        Runtime.getRuntime().availableProcessors()));
        ExecutorService executor =
                Executors.newFixedThreadPool(workers);
        List<Future<FrontierAnswer>> futures =
                new ArrayList<Future<FrontierAnswer>>();

        try {
            for (final ContextConnection connection : connections) {
                futures.add(executor.submit(
                        new Callable<FrontierAnswer>() {
                            @Override
                            public FrontierAnswer call()
                                    throws Exception {
                                return LocalFrontierExecutor.execute(
                                        connection,
                                        invocation);
                            }
                        }));
            }

            List<FrontierAnswer> answers =
                    new ArrayList<FrontierAnswer>();
            for (Future<FrontierAnswer> future : futures) {
                try {
                    answers.add(future.get());
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    for (Future<FrontierAnswer> one : futures) {
                        one.cancel(true);
                    }
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new RuntimeException(cause);
                }
            }
            return answers;
        } finally {
            executor.shutdownNow();
        }
    }
}
