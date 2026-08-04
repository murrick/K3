/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationPolicyTest {

    @Test
    void disabledModeSelectsTrustedProvisioning() {
        RegistrationPolicy policy = RegistrationPolicy.fromEmailMode("disabled");

        assertEquals(RegistrationPolicy.TRUSTED, policy);
        assertFalse(policy.allowsPublicSelfRegistration());
        assertFalse(policy.requiresEmailConfirmation());
    }

    @Test
    void mailTransportModesSelectEmailVerifiedRegistration() {
        for (String mode : new String[]{"starttls", "smtps", " STARTTLS "}) {
            RegistrationPolicy policy = RegistrationPolicy.fromEmailMode(mode);

            assertEquals(RegistrationPolicy.EMAIL_VERIFIED, policy);
            assertTrue(policy.allowsPublicSelfRegistration());
            assertTrue(policy.requiresEmailConfirmation());
        }
    }

    @Test
    void unsupportedOrMissingModeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationPolicy.fromEmailMode(null));
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationPolicy.fromEmailMode(""));
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationPolicy.fromEmailMode("smtp"));
    }
}
