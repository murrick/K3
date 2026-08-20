/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.Locale;

/** Canonical logical-name policy for KANGER source transport objects. */
public final class SourceNamePolicy {

    public static final String EXTENSION = ".k";

    private SourceNamePolicy() {
    }

    /**
     * Returns one canonical logical source name.
     *
     * <p>An absent extension is appended. An existing {@code .k} suffix is
     * recognized case-insensitively and normalized to lowercase, while the
     * stem is preserved byte-for-byte apart from surrounding whitespace.</p>
     */
    public static String canonicalize(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            return value;
        }
        if (value.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return value.substring(0, value.length() - EXTENSION.length())
                    + EXTENSION;
        }
        return value + EXTENSION;
    }

    /** True only for physical names already in canonical {@code *.k} form. */
    public static boolean isCanonicalSourceFileName(String name) {
        return name != null
                && name.length() > EXTENSION.length()
                && name.endsWith(EXTENSION);
    }
}
