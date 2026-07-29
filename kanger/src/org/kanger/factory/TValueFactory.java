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
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class TValueFactory implements IFactory<TValue> {

    public static final String SCHEMA = "tvalues";

    private ICache cache;
    private final Mind mind;
    private IStep top = null;
    private IBase connection = null;
    private Map<TVariable, TValue> current = new HashMap<>();
    private boolean action = false;

    public TValueFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TValueFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        current.clear();
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(TValueFactory base) throws Exception {
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
        action = base.isAction();
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized TValue add(TVariable tv, ITerm o) throws Exception {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setId(((User) mind.getUser()).nextId(SCHEMA));
            t.setMindId(mind.getId());
            cache.add(t);
            if (top == null) {
                top = cache.getRoot();
            }
            action = true;
            tv.incFloodControl(o);
        } else {
            t.setDeleted(false, mind);
        }
        return t;
    }

    public TValue get(TVariable tv) {
        if (isEmpty(tv)) {
            return null;
        }
        TValue v = current.get(tv);
        return v;
    }

    public boolean isEmpty(TVariable tv) {
        return !current.containsKey(tv);
    }

    public TValue find(TVariable tv, ITerm v) throws Exception {
        TValue temp = new TValue(tv, v);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = get(id);
            //TODO: Осознанно нет проверки на Deleted. Вообще надо понять нужен ли этот стек
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        return null;
    }

    public TValue get(long id) throws Exception {
        TValue t = (TValue) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (TValue) s.getData(mind);
            }
        }
        return t;
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            } else {
                boolean found = mind.getRules().hasActiveRuleWithTerm(
                        ((TValue) o).getValue(mind).getId());
                if (!found) {
                    toDelete.add(o);
                }
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
        Iterator<Map.Entry<TVariable, TValue>> iterator = current.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!cache.containsKey(iterator.next().getValue().getId())) {
                iterator.remove();
            }
        }
        top = cache.getRoot();
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getTValues());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public long mark() throws Exception {
        return cache.mark();
    }


    public long commit() throws Exception {
        return cache.commit();
    }

    public long release() throws Exception {
        return cache.release();
    }


    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v);
        }
        return v;
    }

    public int size() throws Exception {
        return cache.size();
    }

    public boolean isAction() {
        return action;
    }

    public void dropAction() {
        action = false;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public TValue getRoot(TVariable t) throws Exception {
        for (TValue v : this) {
            if (!v.isDeleted(mind) && v.getTVar(mind).getId() == t.getId()) {
                return v;
            }
        }
        return null;
    }

    public Map<TVariable, TValue> getCurrent() {
        return current;
    }

    private void forward(IStep root, TVariable t, IReactor reactor) throws Exception {
        if (root.getNext() != null) {
            forward(root.getNext(), t, reactor);
            if (((TValue) root.getData()).getTVarId() == t.getId()) {
                reactor.run(root.getData(mind));
            }
        } else {
            if (((TValue) root.getData()).getTVarId() == t.getId()) {
                reactor.run(root.getData(mind));
            }
        }
    }

    public void forEach(TVariable t, IReactor reactor) throws Exception {
        if (cache.size() > 0) {
            forward(cache.getRoot(), t, reactor);
        }
    }

    public void scan(TVariable t, IReactor reactor) throws Exception {
        if (!cache.isEmpty()) {
            IStep root = null;
            IStep bottom = null;
            do {
                root = cache.getRoot();
                IStep saveRoot = root;
                for (; root != bottom; root = root.getNext()) {
                    if (((TValue) root.getData(mind)).getTVarId() == t.getId()) {
                        reactor.run(root.getData(mind));
                    }
                }
                bottom = saveRoot;
            } while (root != cache.getRoot());
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
