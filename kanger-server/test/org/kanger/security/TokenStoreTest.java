package org.kanger.security;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kanger.exception.AuthenticationErrorException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TokenStoreTest {

    private Path directory;
    private ConfirmationTokenStore confirmations;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-confirmations-");
        confirmations = new ConfirmationTokenStore(directory.resolve("confirmations.conf"));
    }

    @After
    public void tearDown() throws Exception {
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
    public void generatesIndependentOpaqueTokens() {
        Set<String> tokens = new HashSet<String>();
        for (int index = 0; index < 100; index++) {
            String token = SecureTokens.random256();
            assertEquals(43, token.length());
            assertTrue(token.matches("[A-Za-z0-9_-]+"));
            assertTrue(tokens.add(token));
        }
    }

    @Test
    public void confirmationTokenIsSingleUse() throws Exception {
        String token = confirmations.issue(42L, 60000L);
        assertEquals(42L, confirmations.consume(token));

        try {
            confirmations.consume(token);
            fail("confirmation token must be single use");
        } catch (AuthenticationErrorException expected) {
            // expected
        }
    }

    @Test
    public void issuingNewTokenInvalidatesPreviousTokenForUser() throws Exception {
        String first = confirmations.issue(42L, 60000L);
        String second = confirmations.issue(42L, 60000L);

        try {
            confirmations.consume(first);
            fail("previous token must be invalidated");
        } catch (AuthenticationErrorException expected) {
            // expected
        }
        assertEquals(42L, confirmations.consume(second));
    }
}
