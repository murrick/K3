/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import org.kanger.interfaces.IMind;

import java.util.Date;

/**
 * JVM shutdown adapter for the interactive Console runtime.
 *
 * <p>The hook follows the same ownership contract as ordinary Console
 * lifecycle operations: it retains the actually active {@link IMind}, rolls
 * back unfinished child transaction levels to the root, and delegates physical
 * storage shutdown to {@link IMind#closeStorage()}. It never bypasses the Core
 * lifecycle with a null Mind or a direct low-level storage close.</p>
 */
public class ShutdownHook extends Thread {
    private volatile IMind mind;

    public ShutdownHook(IMind mind) {
        this.mind = mind;
    }

    /**
     * Publishes the Console-owned active Mind to the shutdown thread.
     */
    public void setMind(IMind mind) {
        this.mind = mind;
    }

    /**
     * Returns the latest Console-owned Mind. Package-visible qualification
     * uses this to verify that shutdown finishes at the root context.
     */
    IMind getMind() {
        return mind;
    }

    /**
     * Performs the deterministic cleanup used by {@link #run()}.
     *
     * <p>Unfinished nested transactions are rolled back because JVM shutdown
     * cannot implicitly commit operator work that was never committed by the
     * Console session. Once the root is reached, normal Core close performs the
     * durable root checkpoint and physical storage close.</p>
     */
    void shutdown() throws Exception {
        IMind active = mind;
        if (active == null) {
            return;
        }

        while (active.getNext() != null) {
            IMind parent = active.getNext();
            parent.release(active);
            active = parent;
            mind = active;
        }

        if (active.isStorageUsed()) {
            active = active.closeStorage();
            mind = active;
        }
    }

    @Override
    public void run() {
        try {
            shutdown();
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        }
    }
}
