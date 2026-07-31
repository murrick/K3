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

    private static final String SOURCE_BRANCH = "arch/3.5.0-core-consolidation";
    private static final String SOURCE_DATE = "2021-12-28_13:28:12";
    private static final Properties BUILD_METADATA = loadBuildMetadata();

    public static final int VERSION = 3;
    public static final int RELEASE = 3;
    public static final String REVISION = "7318";
    public static final String BRANCH = buildProperty("branch", SOURCE_BRANCH);
    public static final String DATE = buildProperty("date", SOURCE_DATE);
    public static final String BUILD_CREDIT = "Stabilized and audited in collaboration with ChatGPT.";
    public static final int YEAR = getYear(parseDate(DATE));
    public static final int VERSION_CODE = ((VERSION & 0xFF) << 8) | (RELEASE & 0xFF);
    public static final String VERSION_S = BRANCH;
    public static final String DATE_S = formatDate(parseDate(DATE)) + "\n" + BUILD_CREDIT;

    private static Properties loadBuildMetadata() {
        Properties properties = new Properties();
        try (InputStream input = Version.class.getResourceAsStream("/org/kanger/build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ex) {
            // Source constants remain the compatibility fallback.
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

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(date);
    }

    private static int getYear(Date date) {
        return Integer.parseInt(new SimpleDateFormat("yyyy").format(date));
    }

    private static Date parseDate(String date) {
        Date d = null;
        try {
            d = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").parse(date);
        } catch (ParseException ex) {
            //
        }

        return d;
    }
}

//////////