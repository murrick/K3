/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification for User-owned runtime time-zone context. */
public class UserTimeZoneTest {

    @Test
    void userSnapshotsDefaultTimeZoneAtConstruction() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Brussels"));
            User brussels = new User();

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Fakaofo"));
            User fakaofo = new User();

            assertEquals("Europe/Brussels", brussels.getTimeZone());
            assertEquals("Pacific/Fakaofo", fakaofo.getTimeZone());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void explicitSessionTimeZoneOverridesConstructionSnapshot() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Fakaofo"));
            User user = new User();
            assertEquals("Pacific/Fakaofo", user.getTimeZone());

            user.setTimeZone("Europe/Brussels");
            assertEquals("Europe/Brussels", user.getTimeZone());

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Vladivostok"));
            assertEquals("Europe/Brussels", user.getTimeZone());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void invalidTimeZoneIsRejected() {
        final User user = new User();
        assertThrows(IllegalArgumentException.class,
                () -> user.setTimeZone("Not/A_Time_Zone"));
        assertThrows(IllegalArgumentException.class,
                () -> user.setTimeZone("  "));
    }

    @Test
    void timeZoneIsNotPersistedInKangerConf() throws Exception {
        Path dir = Files.createTempDirectory("kanger-user-timezone-");
        Path config = dir.resolve("kanger.conf");
        try {
            User user = new User();
            user.setUserDir(dir.toString() + File.separator);
            user.setTimeZone("Europe/Brussels");
            user.setProperty("timezone.persistence.probe", "ok");

            Properties persisted = new Properties();
            try (BufferedReader reader = Files.newBufferedReader(config)) {
                persisted.load(reader);
            }

            assertEquals("ok", persisted.getProperty("timezone.persistence.probe"));
            assertFalse(persisted.containsValue("Europe/Brussels"));
        } finally {
            Files.deleteIfExists(config);
            Files.deleteIfExists(dir);
        }
    }
}
