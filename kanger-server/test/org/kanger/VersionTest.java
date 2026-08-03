/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VersionTest {

    @Test
    void serverArtifactVersionIsIndependentFromCoreAndSourceBranch() {
        assertEquals("3.3", Version.CORE_VERSION_S);
        assertEquals("server-0.12", Version.BRANCH);
        assertEquals("server-0.12", Version.VERSION_S);
        assertEquals("server-0.12", Version.SERVER_VERSION_S);
        assertNotEquals(Version.SOURCE_BRANCH, Version.SERVER_VERSION_S);
        assertFalse(Version.SERVER_VERSION_S.contains("deployment"));
        assertFalse(Version.SERVER_VERSION_S.contains("first-vps-deploy"));
    }
}
