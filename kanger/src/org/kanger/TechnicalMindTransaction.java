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
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
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

/**
 * Owns one operation-local child {@link Mind} and guarantees single settlement.
 *
 * <p>This scope is deliberately distinct from a user-visible transaction level.
 * It exists only while one Core operation is executing and must be settled
 * before that operation returns. Successful callers explicitly {@link #commit()}
 * or {@link #rollback()}; an exception before settlement is automatically
 * rolled back by {@link #close()}.</p>
 *
 * <p>The {@code settlementStarted} flag is set <em>before</em> entering
 * {@link Mind#commit(org.kanger.interfaces.IMind)} or
 * {@link Mind#release(org.kanger.interfaces.IMind)}. A settlement operation can
 * itself fail after consuming the parent reservation, so retrying it from
 * {@code close()} would risk a double-settle/transaction-counter underflow.</p>
 */
final class TechnicalMindTransaction implements AutoCloseable {

    private final Mind parent;
    private final Mind child;
    private boolean settlementStarted;

    private TechnicalMindTransaction(Mind parent) throws Exception {
        if (parent == null) {
            throw new IllegalArgumentException("Technical Mind transaction requires a parent");
        }
        this.parent = parent;
        this.child = new Mind(parent);
    }

    static TechnicalMindTransaction begin(Mind parent) throws Exception {
        return new TechnicalMindTransaction(parent);
    }

    Mind mind() {
        return child;
    }

    boolean commit() throws Exception {
        beginSettlement();
        return parent.commit(child);
    }

    void rollback() throws Exception {
        beginSettlement();
        parent.release(child);
    }

    private void beginSettlement() {
        if (settlementStarted) {
            throw new IllegalStateException("Technical Mind transaction already settled");
        }
        settlementStarted = true;
    }

    @Override
    public void close() throws Exception {
        if (!settlementStarted) {
            settlementStarted = true;
            parent.release(child);
        }
    }
}
