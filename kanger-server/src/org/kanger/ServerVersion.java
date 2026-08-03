/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Public version identity of the deployable KANGER Server artifact.
 *
 * <p>This identity is deliberately separate from {@link Version}, which keeps
 * the historical KANGER core version and source-build provenance. Deployment
 * scenario labels and source branch names must never become the public server
 * version.</p>
 */
final class ServerVersion {

    static final String DEFAULT_ARTIFACT = "server-0.12";
    private static final String UNKNOWN = "unknown";
    private static final Properties BUILD_METADATA = loadBuildMetadata();

    static final String ARTIFACT = buildProperty("artifact", DEFAULT_ARTIFACT);
    static final String SOURCE_BRANCH = buildProperty("source.branch", UNKNOWN);
    static final String BUILD_DATE = buildProperty("date", UNKNOWN);

    private ServerVersion() {
    }

    private static Properties loadBuildMetadata() {
        Properties properties = new Properties();
        try (InputStream input = ServerVersion.class.getResourceAsStream(
                "/org/kanger/server-build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ex) {
            // Defaults preserve a stable public identity for source-only runs.
        }
        return properties;
    }

    private static String buildProperty(String name, String fallback) {
        String value = BUILD_METADATA.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
