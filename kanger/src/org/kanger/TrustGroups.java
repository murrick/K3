/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov */
package org.kanger;

/** Local symbolic commune names shared by operator and source metadata. */
public final class TrustGroups {
    private TrustGroups() { }
    public static String validate(String group) {
        if (group == null) return null;
        if (group.isEmpty() || group.length() > 256
                || group.chars().anyMatch(c -> Character.isWhitespace(c)
                        || Character.isSpaceChar(c) || Character.isISOControl(c)))
            throw new IllegalArgumentException("Trust commune name must be one non-empty symbol (up to 256 characters)");
        return group;
    }
}
