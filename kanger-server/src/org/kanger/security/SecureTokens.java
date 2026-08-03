/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates independent opaque tokens for server-side security domains.
 */
public final class SecureTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /**
     * Returns a 256-bit random value encoded as unpadded URL-safe Base64.
     */
    public static String random256() {
        byte[] value = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
