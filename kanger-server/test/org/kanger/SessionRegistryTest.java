package org.kanger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IUser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTest {

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void serializesCompleteWorkflowForOneUser() throws Exception {
        MutableTime time = new MutableTime();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        SessionRegistry registry = registry(time, new AtomicInteger());
        String token = registry.open(user(7L));

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Future<Void> first = executor.submit(() -> registry.execute(
                token,
                new SessionRegistry.UserAction<Void>() {
                    @Override
                    public Void run(IUser ignored) throws Exception {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        firstEntered.countDown();
                        assertTrue(releaseFirst.await(5L, TimeUnit.SECONDS));
                        active.decrementAndGet();
                        return null;
                    }
                }));

        assertTrue(firstEntered.await(5L, TimeUnit.SECONDS));
        Future<Void> second = executor.submit(() -> registry.execute(
                token,
                new SessionRegistry.UserAction<Void>() {
                    @Override
                    public Void run(IUser ignored) {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        active.decrementAndGet();
                        return null;
                    }
                }));

        Thread.sleep(100L);
        assertFalse(second.isDone());
        releaseFirst.countDown();

        first.get(5L, TimeUnit.SECONDS);
        second.get(5L, TimeUnit.SECONDS);
        assertEquals(1, maximum.get());
    }

    @Test
    void tokenRotationInvalidatesPreviousToken() throws Exception {
        MutableTime time = new MutableTime();
        SessionRegistry registry = registry(time, new AtomicInteger());
        IUser user = user(8L);

        String first = registry.open(user);
        String second = registry.open(user);

        assertThrows(AuthenticationErrorException.class,
                () -> registry.execute(first, ignored -> null));
        assertEquals(user,
                registry.execute(second, activeUser -> activeUser));
        assertEquals(1, registry.size());
    }

    @Test
    void logoutInvalidatesTokenAndClosesRuntimeOnce() throws Exception {
        MutableTime time = new MutableTime();
        AtomicInteger closed = new AtomicInteger();
        SessionRegistry registry = registry(time, closed);
        String token = registry.open(user(9L));

        registry.closeToken(token);

        assertEquals(1, closed.get());
        assertEquals(0, registry.size());
        assertThrows(AuthenticationErrorException.class,
                () -> registry.execute(token, ignored -> null));
        assertThrows(AuthenticationErrorException.class,
                () -> registry.closeToken(token));
        assertEquals(1, closed.get());
    }

    @Test
    void inactivityIsMeasuredFromRequestCompletion() throws Exception {
        MutableTime time = new MutableTime();
        AtomicInteger closed = new AtomicInteger();
        SessionRegistry registry = new SessionRegistry(
                50L,
                time,
                user -> closed.incrementAndGet());
        String token = registry.open(user(10L));

        time.now = 40L;
        registry.execute(token, ignored -> {
            time.now = 100L;
            return null;
        });

        time.now = 149L;
        registry.expireInactive();
        assertEquals(0, closed.get());
        assertEquals(1, registry.size());

        time.now = 151L;
        registry.expireInactive();
        assertEquals(1, closed.get());
        assertEquals(0, registry.size());
    }

    @Test
    void shutdownClosesEveryRegisteredRuntime() throws Exception {
        MutableTime time = new MutableTime();
        AtomicInteger closed = new AtomicInteger();
        SessionRegistry registry = registry(time, closed);
        registry.open(user(11L));
        registry.open(user(12L));

        registry.shutdown();

        assertEquals(2, closed.get());
        assertEquals(0, registry.size());
    }

    private static SessionRegistry registry(MutableTime time,
                                            AtomicInteger closed) {
        return new SessionRegistry(
                1000L,
                time,
                user -> closed.incrementAndGet());
    }

    private static IUser user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final class MutableTime implements SessionRegistry.TimeSource {
        private volatile long now;

        @Override
        public long now() {
            return now;
        }
    }
}
