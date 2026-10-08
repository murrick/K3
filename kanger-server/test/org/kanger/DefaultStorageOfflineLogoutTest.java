package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.bootstrap.RuntimeBootstrap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DefaultStorageOfflineLogoutTest {
    @Test void logoutReleasesNestedOfflineLayersWithDefaultDumb2() throws Exception {
        String identity = "offline-default-" + UUID.randomUUID();
        User user = (User) UserFactory.createUser(identity, identity);
        int before = UserFactory.activeSessionCount();
        try {
            RuntimeBootstrap.ensure(user);
            assertEquals("org.kanger.storage.dumb2.DB", user.getData().getClass().getName());
            Mind root = new Mind(user);
            Mind child = new Mind(root);
            assertTrue(Boolean.TRUE.equals(child.query("!scratch;")));
            Mind nested = new Mind(child);
            user.setCurrentMind(nested);
            String token = UserFactory.addUser(user);
            UserFactory.logout(token);
            assertEquals(before, UserFactory.activeSessionCount());
            assertTrue(user.getData().isClosed());
            assertNull(user.getCurrentMind());
            assertThrows(org.kanger.exception.AuthenticationErrorException.class,
                    () -> UserFactory.getUser(token));
        } finally {
            UserFactory.dropUser(user);
        }
    }
}
