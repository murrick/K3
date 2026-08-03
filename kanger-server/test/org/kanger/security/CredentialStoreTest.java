package org.kanger.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialStoreTest {

    private Path directory;
    private Path file;
    private CredentialStore store;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("kanger-credentials-");
        file = directory.resolve("users.conf");
        store = new CredentialStore(file, 1000, new SecureRandom());
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
    void createsAndAuthenticatesVersionedCredential() throws Exception {
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
    void failedAuthenticationDoesNotRewriteCredentialFile() throws Exception {
        store.create("rick", "correct password");
        byte[] before = Files.readAllBytes(file);

        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("rick", "wrong password"));

        byte[] after = Files.readAllBytes(file);
        assertTrue(java.util.Arrays.equals(before, after));
    }

    @Test
    void migratesLegacyCredentialAfterSuccessfulAuthentication() throws Exception {
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
    void updateReplacesCredentialForSameUser() throws Exception {
        long userId = store.create("old-login", "old-password");
        store.update(userId, "new-login", "new-password");

        assertEquals(userId, store.authenticate("new-login", "new-password"));
        assertThrows(AuthenticationErrorException.class,
                () -> store.authenticate("old-login", "old-password"));
    }

    private static String join(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            result.append(line).append('\n');
        }
        return result.toString();
    }
}
