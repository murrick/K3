/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage;

/** Test-only process crash injection. Inert unless the property is set. */
final class DumbFaultInjector {

    static final String PROPERTY = "kanger.dumb.fault.haltAt";

    private DumbFaultInjector() {
    }

    static void hit(String point) {
        String selected = System.getProperty(PROPERTY);
        if (point.equals(selected)) {
            System.err.println("DUMB_FAULT_HALT " + point);
            System.err.flush();
            Runtime.getRuntime().halt(86);
        }
    }
}
