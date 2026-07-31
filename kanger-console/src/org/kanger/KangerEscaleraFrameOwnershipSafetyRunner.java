/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.DB;
import org.kanger.storage.Escalera;
import org.kanger.udf.UDF;

/**
 * Regression gate for strict LIFO completion and underflow rejection of nested
 * Escalera checkpoint frames.
 *
 * <p>The current parameterless completion API cannot identify the lexical
 * owner of a frame. It can, however, guarantee that every completion consumes
 * exactly one open frame in LIFO order and that completion at depth zero is
 * rejected.</p>
 */
public final class KangerEscaleraFrameOwnershipSafetyRunner {

    private KangerEscaleraFrameOwnershipSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            IUser user = UserFactory.createUser("escalera-frame-ownership",
                    "escalera-frame-ownership");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);
            Escalera cache = new Escalera(mind, "frame-ownership", null);

            ITerm baseline = mind.getTerms().add("frame_baseline");
            ITerm outer = mind.getTerms().add("frame_outer");
            ITerm inner = mind.getTerms().add("frame_inner");
            cache.add((IUnit) baseline);

            cache.mark();
            cache.add((IUnit) outer);

            cache.mark();
            cache.add((IUnit) inner);

            cache.release();
            require(cache.containsKey(baseline.getId()),
                    "inner release lost baseline");
            require(cache.containsKey(outer.getId()),
                    "inner release lost outer mutation");
            require(!cache.containsKey(inner.getId()),
                    "inner release retained inner mutation");

            cache.commit();
            require(cache.containsKey(baseline.getId()),
                    "outer commit lost baseline");
            require(cache.containsKey(outer.getId()),
                    "outer commit lost committed mutation");
            require(!cache.containsKey(inner.getId()),
                    "outer commit restored inner mutation");

            boolean commitUnderflowRejected = false;
            try {
                cache.commit();
            } catch (IllegalStateException expected) {
                commitUnderflowRejected = true;
            }
            require(commitUnderflowRejected,
                    "commit without an open mark was silently accepted");

            boolean releaseUnderflowRejected = false;
            try {
                cache.release();
            } catch (IllegalStateException expected) {
                releaseUnderflowRejected = true;
            }
            require(releaseUnderflowRejected,
                    "release without an open mark was silently accepted");

            System.out.println("ESCALERA_FRAME_OWNERSHIP_PASS nested-lifo");
            System.out.println("ESCALERA_FRAME_OWNERSHIP_PASS underflow");
            System.out.println("ESCALERA_FRAME_OWNERSHIP_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
