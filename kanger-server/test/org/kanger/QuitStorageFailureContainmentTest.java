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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression for the production Browser failure observed during manual
 * distribution qualification: once a storage-backed Mind can no longer close
 * cleanly, explicit quit must still terminate the server session instead of
 * surfacing a protocol 500 and trapping the user in the poisoned runtime.
 */
class QuitStorageFailureContainmentTest {

    @Test
    void quitDetachesSessionWhenStorageCloseFails() throws Exception {
        User user = new User();
        long userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user.setId(userId == 0L ? 1L : userId);
        user.setCurrentMind(new Mind(user) {
            @Override
            public IMind closeStorage() throws Exception {
                throw new IllegalStateException("fixture storage close failure");
            }
        });

        String token = UserFactory.addUser(user);
        try {
            JSONObject response = (JSONObject) new QueryProcessor().run(
                    new JSONObject().put("body", new JSONObject()
                            .put("context", "command")
                            .put("parameters", new JSONObject()
                                    .put("token", token)
                                    .put("quit", ""))));

            assertEquals("OK", response.optString("result"), response.toString());
            assertThrows(AuthenticationErrorException.class,
                    () -> UserFactory.getUser(token),
                    "quit returned success but left the poisoned token active");
        } finally {
            user.setCurrentMind(null);
            try {
                UserFactory.dropUser(user);
            } catch (Exception ignored) {
                // best-effort fixture cleanup if the regression is still red
            }
        }
    }
}
