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
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.Escalera;
import org.kanger.units.Function;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class FunctionFactory implements IFactory<Function> {

    public static final String SCHEMA = "functions";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;

    public FunctionFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(FunctionFactory base) throws Exception {
        top = null;
        connection = null;
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(FunctionFactory base) throws Exception {
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
        cache.update();
    }

    public synchronized Function add(ITerm name, ArgumentsList arguments) throws Exception {
        return add(name, arguments, FunctionBinding.LEGACY_AUTO);
    }

    public synchronized Function add(ITerm name, ArgumentsList arguments, FunctionBinding binding) throws Exception {
        Function f = new Function(mind);
        f.setName(name);
        f.setRange(arguments.size());
        f.setBinding(binding);
        f.getArguments().clear();
        f.getArguments().addAll(arguments);
        f.getArguments().add(new Argument());
        f.setId(((User) mind.getUser()).nextId(SCHEMA));
        f.setMindId(mind.getId());
        cache.add(f);
        if (top == null) {
            top = cache.getRoot();
        }
        if (!f.isCalculable()) {
            mind.getCalculator().calculate(f, false);
        }
        return f;
    }

    public Function get(long id) throws Exception {
        Function t = (Function) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Function) s.getData(mind);
            }
        }
        return t;
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getFunctions());
        } else {
            cache.clear();
            transaction(null);
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

    public int size() throws Exception {
        return cache.size();
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
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

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }
}
