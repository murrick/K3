/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenTestHomeIsolationTest {

    @Test
    void serverTestsUseDedicatedKangerHome() {
        String configuredTestHome = System.getProperty("kanger.test.home");
        assertNotNull(configuredTestHome,
                "Maven must provide an explicit server test home");

        Path expectedHome = Paths.get(configuredTestHome)
                .toAbsolutePath()
                .normalize();
        Path actualHome = Paths.get(System.getProperty("user.home"))
                .toAbsolutePath()
                .normalize();

        assertEquals(expectedHome, actualHome,
                "Surefire must not expose the host user.home to server tests");
        assertEquals("KANGER", System.getenv("KANGER_HOME"),
                "Server tests must use the canonical relative KANGER root");

        Path repositoryRoot = Paths.get(UserFactory.getDir(UserFactory.rootDir))
                .toAbsolutePath()
                .normalize();
        Path expectedRepositoryRoot = expectedHome.resolve("KANGER").normalize();

        assertEquals(expectedRepositoryRoot, repositoryRoot,
                "Server test repository must resolve inside the dedicated test home");
        assertTrue(repositoryRoot.startsWith(expectedHome),
                "Server test repository escaped the dedicated test home");
    }
}
