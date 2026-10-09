/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.CommandErrorException;

/** Shared one-line description rules for Console, API and revision providers. */
public final class RevisionDescriptions {
    private RevisionDescriptions() { }
    public static String validate(String value) throws CommandErrorException {
        if (value == null) return null;
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.codePointCount(0, value.length()) > 512) {
            throw new CommandErrorException("Revision description must be one line of at most 512 characters");
        }
        return value.trim();
    }
    public static String automatic(String value) {
        String text = value.replaceAll("[\\r\\n]+", " ").trim();
        return text.codePointCount(0, text.length()) <= 512 ? text
                : text.substring(0, text.offsetByCodePoints(0, 509)) + "...";
    }
}
