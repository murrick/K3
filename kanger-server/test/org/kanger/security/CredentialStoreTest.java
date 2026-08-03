package org.kanger.security;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kanger.exception.AuthenticationErrorException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CredentialStoreTest {

    private Path directory;
    private Path file;
    private CredentialStore store;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-credentials-");
        file = directory.resolve("users.conf");
        store = new CredentialStore(file, 1000, new SecureRandom());
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
    public void createsAndAuthenticatesVersionedCredential() throws Exception {
        long userId = store.create("rick", "correct horse battery staple");

        assertEquals(1L, userId);
        assertEquals(userId,
                store.authenticate("rick", "correct horse battery staple"));

        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(content.contains("v2\t"));
        assertFalse(content.contains("correct horse battery staple"));
        assertFalse(content.contains(CredentialStore.legacyToken(
                "rick", "correct horse battery staple") + "="));
    }

    @Test
    public void failedAuthenticationDoesNotRewriteCredentialFile() throws Exception {
        store.create("rick", "correct password");
        byte[] before = Files.readAllBytes(file);

        try {
            store.authenticate("rick", "wrong password");
            fail("authentication must fail");
        } catch (AuthenticationErrorException expected) {
            // expected
        }

        byte[] after = Files.readAllBytes(file);
        assertTrue(java.util.Arrays.equals(before, after));
    }

    @Test
    public void migratesLegacyCredentialAfterSuccessfulAuthentication() throws Exception {
        String login = "legacy-user";
        String password = "legacy-password";
        String legacy = CredentialStore.legacyToken(login, password);
        Files.write(file,
                java.util.Collections.singletonList(legacy + "=7"),
                StandardCharsets.UTF_8);

        assertEquals(7L, store.authenticate(login, password));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String content = join(lines);
        assertTrue(content.contains("v2\t"));
        assertFalse(content.contains(legacy + "=7"));
        assertEquals(7L, store.authenticate(login, password));
    }

    @Test
    public void updateReplacesCredentialForSameUser() throws Exception {
        long userId = store.create("old-login", "old-password");
        store.update(userId, "new-login", "new-password");

        assertEquals(userId, store.authenticate("new-login", "new-password"));
        try {
            store.authenticate("old-login", "old-password");
            fail("old credential must no longer authenticate");
        } catch (AuthenticationErrorException expected) {
            // expected
        }
    }

    private static String join(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            result.append(line).append('\n');
        }
        return result.toString();
    }
}
