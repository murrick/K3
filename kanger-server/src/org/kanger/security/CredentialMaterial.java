/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Immutable password-verification material that can be persisted before an
 * account id exists and later published as a credential without recovering the
 * original password.
 *
 * <p>The encoded form contains a salted PBKDF2 verifier, not plaintext. It is
 * still security-sensitive and must receive the same filesystem protection as
 * the credential store or a pending-registration store.</p>
 */
public final class CredentialMaterial {

    private static final String FORMAT = "pbkdf2-sha256-v1";
    private static final int MINIMUM_SALT_BYTES = 16;
    private static final int MINIMUM_HASH_BYTES = 32;

    private final int iterations;
    private final byte[] salt;
    private final byte[] hash;

    CredentialMaterial(int iterations, byte[] salt, byte[] hash) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be greater than zero");
        }
        if (salt == null || salt.length < MINIMUM_SALT_BYTES) {
            throw new IllegalArgumentException("credential salt is too short");
        }
        if (hash == null || hash.length < MINIMUM_HASH_BYTES) {
            throw new IllegalArgumentException("credential hash is too short");
        }
        this.iterations = iterations;
        this.salt = salt.clone();
        this.hash = hash.clone();
    }

    /**
     * Encodes the verifier for protected transient persistence.
     */
    public String encode() {
        return FORMAT + "\t"
                + iterations + "\t"
                + encodeBytes(salt) + "\t"
                + encodeBytes(hash);
    }

    /**
     * Restores an encoded verifier produced by {@link #encode()}.
     */
    public static CredentialMaterial decode(String encoded) throws IOException {
        if (encoded == null || encoded.isEmpty()) {
            throw new IOException("credential material must not be empty");
        }
        String[] values = encoded.split("\\t", -1);
        if (values.length != 4 || !FORMAT.equals(values[0])) {
            throw new IOException("unsupported credential material format");
        }
        try {
            return new CredentialMaterial(
                    Integer.parseInt(values[1]),
                    decodeBytes(values[2]),
                    decodeBytes(values[3]));
        } catch (IllegalArgumentException error) {
            throw new IOException("invalid credential material", error);
        }
    }

    /**
     * Verifies a candidate password without exposing the stored verifier.
     */
    public boolean matches(String password) throws Exception {
        if (password == null || password.isEmpty()) {
            return false;
        }
        char[] chars = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(
                chars, salt, iterations, hash.length * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(
                    "PBKDF2WithHmacSHA256");
            byte[] candidate = factory.generateSecret(specification).getEncoded();
            return MessageDigest.isEqual(hash, candidate);
        } finally {
            specification.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    int iterations() {
        return iterations;
    }

    byte[] salt() {
        return salt.clone();
    }

    byte[] hash() {
        return hash.clone();
    }

    private static String encodeBytes(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decodeBytes(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
