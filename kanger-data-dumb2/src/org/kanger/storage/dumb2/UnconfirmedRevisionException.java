/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.IOException;

/** The move failed and its visible outcome could not be established. Never proof of rollback. */
final class UnconfirmedRevisionException extends IOException {
    private static final long serialVersionUID=1L;
    final long previous,next;
    UnconfirmedRevisionException(long previous,long next,IOException cause){super("Revision publication outcome is unconfirmed; checkpoint storage before using CURRENT",cause);this.previous=previous;this.next=next;}
}
