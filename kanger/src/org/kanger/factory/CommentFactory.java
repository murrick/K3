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

package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Comment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 20.12.2020.
 */
public class CommentFactory {

    public static final String SCHEMA = "comments";

    public static final long HEADER_ID = -2L;
    public static final long FOOTER_ID = -3L;

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;

    public CommentFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(CommentFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(CommentFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
        }
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized Comment add(long ruleId, String comment) throws Exception {
        Comment p = get(ruleId);
        if (p != null) {
            p.setDeleted(false, mind);
            if (!p.getComment().equals(comment)) {
                p.setComment(comment);
                if (connection != null) {
                    IStep s = connection.get(p.getId());
                    if (s != null) {
                        s.setData(p);
                        s.update();
                    } else {
                        System.err.println("!");
                    }
                }

            }
            return p;
        } else {
            p = new Comment(ruleId, comment, mind);
            p.setId(ruleId);
            p.setMindId(mind.getId());
            cache.add(p);
            if (top == null) {
                top = cache.getRoot();
            }
            return p;
        }
    }

    public Comment get(long id) throws Exception {
        Comment t = (Comment) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Comment) s.getData(mind);
            }
        }
        return t;
    }
    public int size() throws Exception {
        return cache.size();
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction((CommentFactory) ((Mind) mind.getNext()).getComments());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public Iterator iterator() {
        return cache.iterator(-1);
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public void mark() throws Exception {
        cache.mark();
    }

    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

}
