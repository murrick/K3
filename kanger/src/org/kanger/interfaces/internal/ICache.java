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
 */

package org.kanger.interfaces.internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public interface ICache extends Iterable {

    void add(IUnit one) throws Exception;

//    void update(IUnit one) throws Exception;

    Object get(long id) throws Exception;

    void delete(long id) throws Exception;

    default void deleteAll(Collection<Long> ids) throws Exception {
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    delete(id);
                }
            }
        }
    }

    int size();

    boolean isEmpty();

    Set<Long> find(int h) throws Exception;

    void clear() throws Exception;

    long mark();

    long commit();

    long release() throws Exception;

    boolean containsKey(long id) throws Exception;

    Iterator<Object> iterator(long fromId);

    IStep getRoot();

    void setRoot(IStep root);

    @Override
    Iterator<Object> iterator();

    boolean update() throws Exception;

}
