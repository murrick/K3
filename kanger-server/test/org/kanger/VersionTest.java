/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VersionTest {

    @Test
    void serverArtifactVersionIsIndependentFromCoreVersion() {
        assertEquals("3.3", Version.CORE_VERSION_S);
        assertEquals("server-0.13", Version.BRANCH);
        assertEquals("server-0.13", Version.VERSION_S);
        assertEquals("server-0.13", Version.SERVER_VERSION_S);
        assertFalse(Version.SERVER_VERSION_S.contains("deployment"));
        assertFalse(Version.SERVER_VERSION_S.contains("first-vps-deploy"));
    }

    @Test
    void buildMetadataKeepsSourceProvenanceSeparate() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Version.class.getResourceAsStream(
                "/org/kanger/build.properties")) {
            assertNotNull(input);
            properties.load(input);
        }

        assertEquals("server-0.13", properties.getProperty("branch"));
        assertEquals("server-0.13", properties.getProperty("server.version"));
        String sourceBranch = properties.getProperty("source.branch");
        assertNotNull(sourceBranch);
        assertFalse(sourceBranch.trim().isEmpty());
        assertNotEquals("server-0.13", sourceBranch);
    }
}
