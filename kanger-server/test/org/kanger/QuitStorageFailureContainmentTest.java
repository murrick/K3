/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the production Browser failure observed during manual
 * distribution qualification: once a storage-backed Mind can no longer close
 * cleanly, explicit quit must still terminate the server session instead of
 * surfacing a protocol 500 and trapping the user in the poisoned runtime.
 */
class QuitStorageFailureContainmentTest {

    @Test
    void quitDetachesSessionAndReleasesPhysicalStorageWhenLogicalCloseFails()
            throws Exception {
        User user = new User();
        long userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user.setId(userId == 0L ? 1L : userId);

        IMind poisoned = new Mind(user) {
            @Override
            public IMind closeStorage() throws Exception {
                throw new IllegalStateException("fixture storage close failure");
            }
        };
        ProbeData data = new ProbeData();
        data.init(user);
        data.use("poisoned-generation");
        user.setCurrentMind(poisoned);

        String token = UserFactory.addUser(user);
        try {
            IReactor<JSONObject> reactor = new MindLifecycleReactor(
                    new QueryProcessor());
            JSONObject response = (JSONObject) reactor.run(
                    new JSONObject().put("body", new JSONObject()
                            .put("context", "command")
                            .put("parameters", new JSONObject()
                                    .put("token", token)
                                    .put("quit", ""))));

            assertEquals("OK", response.optString("result"), response.toString());
            assertThrows(AuthenticationErrorException.class,
                    () -> UserFactory.getUser(token),
                    "quit returned success but left the poisoned token active");
            assertEquals(1, data.closeCalls,
                    "detached poisoned runtime did not attempt physical storage close");
            assertTrue(data.isClosed(),
                    "detached poisoned runtime retained an open physical generation");
        } finally {
            user.setCurrentMind(null);
            try {
                UserFactory.dropUser(user);
            } catch (Exception ignored) {
                // best-effort fixture cleanup if the regression is still red
            }
        }
    }

    private static final class ProbeData implements IData {
        private boolean open;
        private String name = "";
        private int closeCalls;

        @Override
        public void init(IUser user) {
            ((User) user).setData(this);
        }

        @Override
        public void use(String name) {
            this.name = name;
            this.open = true;
        }

        @Override
        public void close() {
            ++closeCalls;
            open = false;
            name = "";
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
            return name;
        }

        @Override
        public IBase getBase(String context) {
            return null;
        }

        @Override
        public IBase connect(String context) {
            return null;
        }

        @Override
        public String getDescription() {
            return "poisoned logout physical-close fixture";
        }

        @Override
        public Collection<String> list() {
            return Collections.emptyList();
        }
    }
}
