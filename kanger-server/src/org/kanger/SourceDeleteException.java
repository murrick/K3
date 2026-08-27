/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Classified failure of an explicit source deletion at the physical
 * filesystem boundary.
 *
 * <p>The original cause remains available to server diagnostics, but its Java
 * exception text and physical path must not become part of the public protocol
 * response. Because a filesystem syscall failure does not prove the external
 * side-effect outcome, callers must verify current state before retrying.</p>
 */
final class SourceDeleteException extends Exception {

    private static final long serialVersionUID = 1L;

    SourceDeleteException(String logicalName, Throwable cause) {
        super(message(logicalName), cause);
    }

    private static String message(String logicalName) {
        if (logicalName == null || logicalName.isEmpty()) {
            return "Source delete failed";
        }
        return "Source delete failed for " + logicalName;
    }
}
