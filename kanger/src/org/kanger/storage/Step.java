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

package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Step implements IStep {

    private long id = -1;           // id записи
    private int hash = 0;           // хэш записи
    private Object data = null;     // данные записи
    private IStep next = null;      // следующая запись

    private long size = 0;

    @Override
    public ByteBuffer pack() {
        return null;
    }

    @Override
    public IStep apply(ByteBuffer packet) {
        return null;
    }

    @Override
    public Object getData(Mind mind) throws Exception {
        if (data != null && data instanceof IUnit) {
            ((IUnit) data).setMind(mind);
        }
        return data;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public IStep getNext() {
        return next;
    }

    @Override
    public void setNext(IStep next) {
        this.next = next;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public int getHash() {
        return hash;
    }

    @Override
    public void setHash(int hash) {
        this.hash = hash;
    }

    @Override
    public void update() {
    }

    @Override
    public void append() {
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long size) {
        this.size = size;
    }
}
