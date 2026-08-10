/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side owner of an exact source-document snapshot for one live user
 * context.
 *
 * <p>{@link IMind#getSourceCode()} is a semantic reconstruction from compiled
 * rules. That representation is intentionally not byte-preserving. The Browser
 * editor, source repository and compiler boundary additionally need the exact
 * operator document, including the presence or absence of a final EOL. This
 * class keeps that document separate from semantic state.</p>
 *
 * <p>The snapshot is published only after a source document has compiled
 * successfully. Any subsequent Core mutation that changes semantic workspace
 * state invalidates it, after which callers fall back to semantic
 * reconstruction until another exact document is compiled or loaded.</p>
 */
final class SourceDocumentState {

    private static final Map<IUser, String> DOCUMENTS =
            Collections.synchronizedMap(new WeakHashMap<IUser, String>());

    private SourceDocumentState() {
    }

    static void publish(IUser user, String source) {
        if (user == null) {
            return;
        }
        DOCUMENTS.put(user, source == null ? "" : source);
    }

    static void invalidate(IUser user) {
        if (user != null) {
            DOCUMENTS.remove(user);
        }
    }

    static String current(IUser user, IMind mind) throws Exception {
        if (user != null) {
            String exact = DOCUMENTS.get(user);
            if (exact != null) {
                return exact;
            }
        }
        return mind == null ? "" : mind.getSourceCode();
    }

    /**
     * Produce a compiler-only copy with a terminal line boundary when needed.
     * The returned copy must never be republished as the source document.
     */
    static String compilerInput(String source) {
        String exact = source == null ? "" : source;
        if (exact.isEmpty()) {
            return exact;
        }
        char last = exact.charAt(exact.length() - 1);
        return last == '\r' || last == '\n' ? exact : exact + '\n';
    }
}
