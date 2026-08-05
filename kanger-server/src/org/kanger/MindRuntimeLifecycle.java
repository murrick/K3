/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

/**
 * Owns the server-side closure of the active {@link IMind} chain.
 *
 * <p>The browser protocol publishes the deepest user transaction through
 * {@link IUser#getCurrentMind()}. Session logout, expiry and process shutdown
 * must therefore roll every unpublished child back to the root before closing
 * storage. Merely clearing the objects through {@code User.close(...)} leaves
 * parent transaction reservations unfinished.</p>
 */
final class MindRuntimeLifecycle {

    private MindRuntimeLifecycle() {
    }

    /**
     * Rolls the published active chain back from the deepest child to its root.
     * The compatibility slot is advanced after every successful release so a
     * later failure never leaves it pointing at an already-finished child.
     */
    static IMind rollbackToRoot(IUser user) throws Exception {
        if (user == null) {
            return null;
        }

        IMind current = user.getCurrentMind();
        while (current != null && current.getNext() != null) {
            IMind parent = current.getNext();
            parent.release(current);
            current = parent;
            user.setCurrentMind(current);
        }
        return current;
    }

    /**
     * Closes one server runtime best-effort while preserving the first failure.
     * Storage closure is attempted even when transaction rollback failed; the
     * session must not retain an open physical generation after detachment.
     */
    static void close(IUser user) throws Exception {
        if (user == null) {
            return;
        }

        Exception failure = null;
        IMind current = user.getCurrentMind();
        try {
            current = rollbackToRoot(user);
        } catch (Exception rollbackFailure) {
            failure = rollbackFailure;
            current = user.getCurrentMind();
        }

        try {
            if (current != null) {
                current = current.closeStorage();
                user.setCurrentMind(current);
            }
        } catch (Exception closeFailure) {
            if (failure == null) {
                failure = closeFailure;
            } else if (failure != closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        } finally {
            user.setCurrentMind(null);
        }

        if (failure != null) {
            throw failure;
        }
    }
}
