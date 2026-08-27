/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Classified failure of an explicit storage switch below the Server boundary.
 *
 * <p>The original cause remains available to server diagnostics, but its Java
 * exception text and physical storage details must not become part of the
 * public protocol response. Core owns storage-switch compensation; this
 * carrier deliberately does not claim whether the external operation was
 * applied and requires callers to verify the reported workspace state.</p>
 */
final class StorageSwitchException extends Exception {

    private static final long serialVersionUID = 1L;

    StorageSwitchException(String logicalName, Throwable cause) {
        super(message(logicalName), cause);
    }

    private static String message(String logicalName) {
        if (logicalName == null || logicalName.isEmpty()) {
            return "Storage switch failed";
        }
        return "Storage switch failed for " + logicalName;
    }
}
