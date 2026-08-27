/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.PendingRegistrationException;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountIdentityPolicyTest {

    @Test
    void accountLoginCannotDivergeFromCredentialIdentity() {
        PendingRegistrationException violation =
                AccountPolicyReactor.accountLoginChangeViolation(
                        "rick", "another-login");

        assertEquals(AccountErrorCode.ACCOUNT_LOGIN_IMMUTABLE,
                violation.getCode());
        assertNull(AccountPolicyReactor.accountLoginChangeViolation(
                " rick ", "rick"));
    }

    @Test
    void loginIdentityViolationStopsBeforeLegacyProfileMutation()
            throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(
                new AccountPolicyReactor(
                        RegistrationPolicy.EMAIL_VERIFIED,
                        packet -> {
                            delegated.set(true);
                            return new JSONObject().put("result", "OK");
                        },
                        parameters -> { },
                        parameters -> AccountPolicyReactor.accountLoginImmutable()));
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", new JSONObject()
                        .put("register", "another-login")
                        .put("password", "")
                        .put("token", "active-session-token")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertFalse(delegated.get());
        assertEquals("error", response.getString("result"), response.toString());
        assertEquals(AccountErrorCode.ACCOUNT_LOGIN_IMMUTABLE.code(),
                response.getString("code"), response.toString());
        assertEquals("The account login cannot be changed",
                response.getString("description"), response.toString());

        JSONObject diagnostic = response.getJSONObject("error");
        assertEquals(1, diagnostic.getInt("schema"));
        assertEquals("account", diagnostic.getString("domain"));
        assertEquals(AccountErrorCode.ACCOUNT_LOGIN_IMMUTABLE.code(),
                diagnostic.getString("code"));
        assertFalse(diagnostic.getBoolean("retryable"));
        assertEquals("retain", diagnostic.getString("session_action"));
        assertEquals("not_applied", diagnostic.getString("operation_outcome"));
    }
}
