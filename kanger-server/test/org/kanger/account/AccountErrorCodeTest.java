/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountErrorCodeTest {

    @Test
    void publicAccountCodesRemainStable() {
        List<String> codes = Arrays.stream(AccountErrorCode.values())
                .map(AccountErrorCode::code)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList(
                "REGISTRATION_DISABLED",
                "AUTHENTICATION_FAILED",
                "EMAIL_CONFIRMATION_REQUIRED",
                "CONFIRMATION_TOKEN_INVALID",
                "CONFIRMATION_TOKEN_EXPIRED",
                "EMAIL_ALREADY_USED",
                "VERIFIED_EMAIL_IMMUTABLE",
                "ACCOUNT_LOGIN_IMMUTABLE",
                "LOGIN_ALREADY_USED",
                "RESEND_RATE_LIMITED",
                "MAIL_DELIVERY_UNAVAILABLE"), codes);
    }
}
