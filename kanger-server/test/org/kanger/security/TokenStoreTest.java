package org.kanger.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStoreTest {

    private Path directory;
    private ConfirmationTokenStore confirmations;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-confirmations-");
        confirmations = new ConfirmationTokenStore(directory.resolve("confirmations.conf"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (directory != null) {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    void generatesIndependentOpaqueTokens() {
        Set<String> tokens = new HashSet<String>();
        for (int index = 0; index < 100; index++) {
            String token = SecureTokens.random256();
            assertEquals(43, token.length());
            assertTrue(token.matches("[A-Za-z0-9_-]+"));
            assertTrue(tokens.add(token));
        }
    }

    @Test
    void confirmationTokenIsSingleUse() throws Exception {
        String token = confirmations.issue(42L, 60000L);
        assertEquals(42L, confirmations.consume(token));

        assertThrows(AuthenticationErrorException.class,
                () -> confirmations.consume(token));
    }

    @Test
    void issuingNewTokenInvalidatesPreviousTokenForUser() throws Exception {
        String first = confirmations.issue(42L, 60000L);
        String second = confirmations.issue(42L, 60000L);

        assertThrows(AuthenticationErrorException.class,
                () -> confirmations.consume(first));
        assertEquals(42L, confirmations.consume(second));
    }
}
