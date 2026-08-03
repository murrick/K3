/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.json.JSONObject;

/**
 * Validates legacy API identifiers before QueryProcessor can concatenate them
 * with user-owned filesystem roots. Values are not normalized or rewritten:
 * an unsafe identifier is rejected as a whole.
 */
final class ApiInputPolicy {

    private static final String[] SOURCE_FILE_PARAMETERS =
            new String[]{"get", "put", "delete"};
    private static final String[] STORAGE_PARAMETERS =
            new String[]{"use", "drop", "reindex"};

    private ApiInputPolicy() {
    }

    static JSONObject violation(JSONObject parameters) {
        if (parameters == null) {
            return null;
        }

        for (String name : SOURCE_FILE_PARAMETERS) {
            String value = value(parameters, name);
            if (value != null && !value.isEmpty() && !isSafeLeafName(value)) {
                return error(name);
            }
        }

        for (String name : STORAGE_PARAMETERS) {
            String value = value(parameters, name);
            if (value != null && !value.isEmpty() && !isSafeStorageName(value)) {
                return error(name);
            }
        }
        return null;
    }

    static boolean isSafeLeafName(String value) {
        if (!isBasicIdentifier(value) || value.length() > 255) {
            return false;
        }
        return !".".equals(value)
                && !"..".equals(value)
                && value.indexOf("..") < 0
                && value.indexOf('/') < 0
                && value.indexOf('\\') < 0
                && value.indexOf(':') < 0;
    }

    static boolean isSafeStorageName(String value) {
        if (!isBasicIdentifier(value)
                || value.length() > 255
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0
                || value.startsWith(".")
                || value.endsWith(".")
                || value.indexOf("..") >= 0) {
            return false;
        }

        String[] segments = value.split("\\.", -1);
        for (String segment : segments) {
            if (segment.isEmpty()
                    || ".".equals(segment)
                    || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBasicIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == 0 || Character.isISOControl(current)) {
                return false;
            }
        }
        return true;
    }

    private static String value(JSONObject parameters, String name) {
        if (!parameters.has(name) || parameters.isNull(name)) {
            return null;
        }
        Object raw = parameters.opt(name);
        return raw instanceof String ? (String) raw : null;
    }

    private static JSONObject error(String parameter) {
        return new JSONObject()
                .put("result", "error")
                .put("description",
                        "Invalid filesystem identifier in parameter " + parameter);
    }
}
