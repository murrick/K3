/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.IOException;

/** Marker visibility or persistence could not be confirmed. Never proof of rollback. */
final class UnconfirmedRevisionException extends IOException {
    private static final long serialVersionUID=1L;
    /** previous=-1 denotes initial installation with no predecessor. */
    final long previous,next;
    UnconfirmedRevisionException(long previous,long next,IOException cause){super("Revision publication outcome is unconfirmed; checkpoint storage before using CURRENT",cause);this.previous=previous;this.next=next;}
}
