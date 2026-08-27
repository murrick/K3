/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Classified failure of an explicit source import before its semantic delta
 * has been applied.
 *
 * <p>The exact source text is recovery material for Browser repair and is
 * intentionally carried separately from the diagnostic message. The original
 * cause remains available to server diagnostics but must not be rendered into
 * the public response by parsing {@link Throwable#toString()}.</p>
 */
final class SourceImportException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String logicalName;
    private final String recoverySource;

    SourceImportException(String logicalName,
                          String recoverySource,
                          Throwable cause) {
        super(message(logicalName), cause);
        this.logicalName = logicalName == null ? "" : logicalName;
        this.recoverySource = recoverySource == null ? "" : recoverySource;
    }

    String getLogicalName() {
        return logicalName;
    }

    String getRecoverySource() {
        return recoverySource;
    }

    private static String message(String logicalName) {
        if (logicalName == null || logicalName.isEmpty()) {
            return "Source import failed";
        }
        return "Source import failed for " + logicalName;
    }
}
