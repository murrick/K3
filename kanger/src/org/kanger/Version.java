/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public abstract class Version {

    private static final String UNKNOWN_SOURCE_BRANCH = "unknown";
    private static final String UNBOUND_SERVER_VERSION = "server-unbound";
    private static final Properties BUILD_METADATA = loadBuildMetadata();

    /*
     * Legacy binary/serialization compatibility fields. These values are not
     * the public KANGER product release identity.
     */
    public static final int VERSION = 3;
    public static final int RELEASE = 3;
    public static final String REVISION = "7318";
    public static final int VERSION_CODE = ((VERSION & 0xFF) << 8) | (RELEASE & 0xFF);
    public static final String LEGACY_COMPATIBILITY_VERSION_S = VERSION + "." + RELEASE;

    /** Canonical public KANGER product/Core identity. */
    public static final String PRODUCT_VERSION_S = "3.7.0";

    /** Public Core identity reported by all KANGER front ends. */
    public static final String CORE_VERSION_S = PRODUCT_VERSION_S;

    /** Established public display alias retained for compatibility. */
    public static final String VERSION_S = PRODUCT_VERSION_S;

    /**
     * Source provenance only. Server builds prefer source.branch because the
     * legacy branch property is retained there as server packaging metadata.
     */
    public static final String SOURCE_BRANCH = buildProperty(
            "source.branch",
            buildProperty("branch", UNKNOWN_SOURCE_BRANCH));

    /** Legacy public field retained as a provenance alias. */
    public static final String BRANCH = SOURCE_BRANCH;

    /** Public identity of a deployable server component, independent of Core. */
    public static final String SERVER_VERSION_S = buildProperty(
            "server.version", UNBOUND_SERVER_VERSION);

    /** Build timestamp provenance. Empty when build metadata is unavailable. */
    public static final String DATE = buildProperty("date", "");

    public static final String BUILD_CREDIT = "Stabilized and audited in collaboration with ChatGPT.";
    public static final int YEAR = buildYear();
    public static final String DATE_S = buildDateDisplay();

    private static Properties loadBuildMetadata() {
        Properties properties = new Properties();
        try (InputStream input = Version.class.getResourceAsStream("/org/kanger/build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ex) {
            // Public product identity remains available without build metadata.
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

    private static String buildDateDisplay() {
        Date date = parseDate(DATE);
        String display = date == null ? "unavailable" : formatDate(date);
        return display + "\n" + BUILD_CREDIT;
    }

    private static int buildYear() {
        Date date = parseDate(DATE);
        if (date == null) {
            date = new Date();
        }
        return getYear(date);
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(date);
    }

    private static int getYear(Date date) {
        return Integer.parseInt(new SimpleDateFormat("yyyy").format(date));
    }

    private static Date parseDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").parse(date.trim());
        } catch (ParseException ex) {
            return null;
        }
    }
}

//////////