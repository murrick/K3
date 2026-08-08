/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Console-source compatibility alias for the historical utility location.
 *
 * <p>The legacy Console imported {@code org.kanger.enums.*}; the canonical
 * adapter uses the same Core line extraction without broad wildcard imports.
 * Keeping the alias inside the Console source root avoids moving or duplicating
 * Core parsing behavior during the 3.7.0.3 convergence artifact.</p>
 */
abstract class Tools extends org.kanger.enums.Tools {
}
