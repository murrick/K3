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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger.interfaces.internal;

import org.kanger.interfaces.IMind;

import java.util.Collection;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public interface IBase {

    void add(IStep one) throws Exception;

    void update(IStep one) throws Exception;

    IStep get(long id) throws Exception;

    void clearCache();

    boolean isEmpty();

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

    void clear() throws Exception;

    void reindex(IBase to, IMind mind) throws Exception;

    boolean containsKey(long id) throws Exception;

    IStep getRoot();

    IStep getTop();

    String getName();

    long getUsedCacheSize();

    long getMaxCacheSize();

    /**
     * Optional cache telemetry. Default methods keep historical/pluggable
     * storage engines source-compatible until they opt in.
     */
    default long getCacheHits() {
        return -1L;
    }

    default long getCacheMisses() {
        return -1L;
    }

    default long getCacheEvictions() {
        return -1L;
    }

    default long getCachedEntryCount() {
        return -1L;
    }

    default boolean isCacheEnabled() {
        return getMaxCacheSize() > 0L;
    }

    long lastId();

    long nextId();

    void flush() throws Exception;

    void close() throws Exception;

    Class getUdf();

}
