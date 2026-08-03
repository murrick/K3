/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.security.SecureTokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns application sessions and serializes the complete mutable workflow of
 * one user. A user runtime has one fair reentrant lock even when its token is
 * rotated, so an old in-flight request and a new session can never execute on
 * the same Mind concurrently.
 */
final class SessionRegistry {

    interface UserAction<T> {
        T run(IUser user) throws Exception;
    }

    interface Work<T> {
        T run() throws Exception;
    }

    interface RuntimeCloser {
        void close(IUser user) throws Exception;
    }

    interface TimeSource {
        long now();
    }

    private final Object monitor = new Object();
    private final Map<String, UserRuntime> byToken =
            new HashMap<String, UserRuntime>();
    private final Map<Long, UserRuntime> byUser =
            new HashMap<Long, UserRuntime>();

    private final long inactivityMillis;
    private final TimeSource timeSource;
    private final RuntimeCloser runtimeCloser;

    SessionRegistry(long inactivityMillis,
                    TimeSource timeSource,
                    RuntimeCloser runtimeCloser) {
        if (inactivityMillis <= 0L) {
            throw new IllegalArgumentException("inactivityMillis must be positive");
        }
        if (timeSource == null) {
            throw new IllegalArgumentException("timeSource must not be null");
        }
        if (runtimeCloser == null) {
            throw new IllegalArgumentException("runtimeCloser must not be null");
        }
        this.inactivityMillis = inactivityMillis;
        this.timeSource = timeSource;
        this.runtimeCloser = runtimeCloser;
    }

    String open(IUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }

        String token = SecureTokens.random256();
        synchronized (monitor) {
            UserRuntime runtime = byUser.get(user.getId());
            if (runtime == null) {
                runtime = new UserRuntime(user);
                byUser.put(user.getId(), runtime);
            } else if (runtime.user != user) {
                throw new IllegalStateException(
                        "A different user instance is already active for id "
                                + user.getId());
            }

            if (runtime.activeToken != null) {
                byToken.remove(runtime.activeToken);
            }
            runtime.activeToken = token;
            runtime.registered = true;
            runtime.lastActivity = timeSource.now();
            byToken.put(token, runtime);
        }
        return token;
    }

    IUser findActiveUser(long userId) {
        synchronized (monitor) {
            UserRuntime runtime = byUser.get(userId);
            return runtime != null && runtime.registered ? runtime.user : null;
        }
    }

    <T> T execute(String token, UserAction<T> action) throws Exception {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        UserRuntime runtime = requireRuntime(token);
        return executeLocked(runtime, token, action);
    }

    <T> T executeIfPresent(String token, final Work<T> work) throws Exception {
        if (work == null) {
            throw new IllegalArgumentException("work must not be null");
        }

        UserRuntime runtime;
        synchronized (monitor) {
            runtime = byToken.get(token);
        }
        if (runtime == null) {
            return work.run();
        }
        return executeLocked(runtime, token, new UserAction<T>() {
            @Override
            public T run(IUser user) throws Exception {
                return work.run();
            }
        });
    }

    <T> T executeUserIfPresent(long userId, final Work<T> work) throws Exception {
        if (work == null) {
            throw new IllegalArgumentException("work must not be null");
        }

        UserRuntime runtime;
        String token;
        synchronized (monitor) {
            runtime = byUser.get(userId);
            token = runtime == null ? null : runtime.activeToken;
        }
        if (runtime == null || token == null) {
            return work.run();
        }
        return executeLocked(runtime, token, new UserAction<T>() {
            @Override
            public T run(IUser user) throws Exception {
                return work.run();
            }
        });
    }

    void closeToken(String token) throws Exception {
        UserRuntime runtime;
        synchronized (monitor) {
            runtime = byToken.get(token);
            if (runtime == null || !token.equals(runtime.activeToken)) {
                throw new AuthenticationErrorException("token " + token);
            }
            detach(runtime);
        }
        closeDetached(runtime);
    }

    void closeUser(long userId) throws Exception {
        UserRuntime runtime;
        synchronized (monitor) {
            runtime = byUser.get(userId);
            if (runtime == null) {
                return;
            }
            detach(runtime);
        }
        closeDetached(runtime);
    }

    void expireInactive() throws Exception {
        long now = timeSource.now();
        List<UserRuntime> expired = new ArrayList<UserRuntime>();
        synchronized (monitor) {
            for (UserRuntime runtime : new ArrayList<UserRuntime>(byUser.values())) {
                if (runtime.registered
                        && now - runtime.lastActivity > inactivityMillis) {
                    detach(runtime);
                    expired.add(runtime);
                }
            }
        }

        Exception failure = null;
        for (UserRuntime runtime : expired) {
            try {
                closeDetached(runtime);
            } catch (Exception ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    void shutdown() throws Exception {
        List<UserRuntime> detached;
        synchronized (monitor) {
            detached = new ArrayList<UserRuntime>(byUser.values());
            for (UserRuntime runtime : detached) {
                detach(runtime);
            }
        }

        Exception failure = null;
        for (UserRuntime runtime : detached) {
            try {
                closeDetached(runtime);
            } catch (Exception ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    int size() {
        synchronized (monitor) {
            return byUser.size();
        }
    }

    private UserRuntime requireRuntime(String token)
            throws AuthenticationErrorException {
        synchronized (monitor) {
            UserRuntime runtime = byToken.get(token);
            if (runtime == null
                    || !runtime.registered
                    || !token.equals(runtime.activeToken)) {
                throw new AuthenticationErrorException("token " + token);
            }
            return runtime;
        }
    }

    private <T> T executeLocked(UserRuntime runtime,
                                String token,
                                UserAction<T> action) throws Exception {
        runtime.lock.lock();
        try {
            synchronized (monitor) {
                if (!runtime.registered
                        || byToken.get(token) != runtime
                        || !token.equals(runtime.activeToken)) {
                    throw new AuthenticationErrorException("token " + token);
                }
                runtime.lastActivity = timeSource.now();
            }
            return action.run(runtime.user);
        } finally {
            synchronized (monitor) {
                if (runtime.registered
                        && byToken.get(token) == runtime
                        && token.equals(runtime.activeToken)) {
                    runtime.lastActivity = timeSource.now();
                }
            }
            runtime.lock.unlock();
        }
    }

    private void detach(UserRuntime runtime) {
        if (runtime.activeToken != null) {
            byToken.remove(runtime.activeToken);
        }
        byUser.remove(runtime.user.getId());
        runtime.activeToken = null;
        runtime.registered = false;
    }

    private void closeDetached(UserRuntime runtime) throws Exception {
        runtime.lock.lock();
        try {
            if (!runtime.closed) {
                runtime.closed = true;
                runtimeCloser.close(runtime.user);
            }
        } finally {
            runtime.lock.unlock();
        }
    }

    private static final class UserRuntime {
        private final IUser user;
        private final ReentrantLock lock = new ReentrantLock(true);
        private String activeToken;
        private long lastActivity;
        private boolean registered;
        private boolean closed;

        private UserRuntime(IUser user) {
            this.user = user;
        }
    }
}
