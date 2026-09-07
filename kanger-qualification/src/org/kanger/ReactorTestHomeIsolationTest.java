/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReactorTestHomeIsolationTest {

    @Test
    void qualificationTestsInheritReactorTestHomePolicy() {
        String configuredTestHome = System.getProperty("kanger.test.home");
        assertNotNull(configuredTestHome,
                "Reactor must provide an explicit test home");

        Path expectedHome = Paths.get(configuredTestHome)
                .toAbsolutePath()
                .normalize();
        Path actualHome = Paths.get(System.getProperty("user.home"))
                .toAbsolutePath()
                .normalize();

        assertEquals(expectedHome, actualHome,
                "Surefire must not expose the host user.home to qualification tests");
        assertEquals("KANGER", System.getenv("KANGER_HOME"),
                "Qualification tests must use the canonical relative KANGER root");

        Path repositoryRoot = Paths.get(UserFactory.getDir(UserFactory.rootDir))
                .toAbsolutePath()
                .normalize();
        Path expectedRepositoryRoot = expectedHome.resolve("KANGER").normalize();

        assertEquals(expectedRepositoryRoot, repositoryRoot,
                "Qualification repository must resolve inside the reactor test home");
        assertTrue(repositoryRoot.startsWith(expectedHome),
                "Qualification repository escaped the reactor test home");
    }
}
