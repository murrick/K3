/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.account.AccountErrorCode;
import org.kanger.account.RegistrationPolicy;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountIdentityPolicyTest {

    @Test
    void accountLoginCannotDivergeFromCredentialIdentity() {
        JSONObject violation = AccountPolicyReactor.accountLoginChangeViolation(
                "rick", "another-login");

        assertEquals(AccountErrorCode.ACCOUNT_LOGIN_IMMUTABLE.code(),
                violation.getString("code"));
        assertNull(AccountPolicyReactor.accountLoginChangeViolation(
                " rick ", "rick"));
    }

    @Test
    void loginIdentityViolationStopsBeforeLegacyProfileMutation()
            throws Exception {
        AtomicBoolean delegated = new AtomicBoolean();
        AccountPolicyReactor reactor = new AccountPolicyReactor(
                RegistrationPolicy.EMAIL_VERIFIED,
                packet -> {
                    delegated.set(true);
                    return new JSONObject().put("result", "OK");
                },
                parameters -> { },
                parameters -> AccountPolicyReactor.accountLoginImmutable());
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "login")
                .put("parameters", new JSONObject()
                        .put("register", "another-login")
                        .put("password", "")
                        .put("token", "active-session-token")));

        JSONObject response = (JSONObject) reactor.run(packet);

        assertFalse(delegated.get());
        assertEquals(AccountErrorCode.ACCOUNT_LOGIN_IMMUTABLE.code(),
                response.getString("code"));
    }
}
