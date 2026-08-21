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

package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.TValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Query-local Values membership store.
 *
 * <p>Membership is insertion ordered and independent from presentation
 * ordering. Historical mutable order settings remain temporarily exposed for
 * API compatibility, but they must never change row identity or storage.</p>
 */
public class ValuesStore implements IFactory<Map<String, ITerm>> {

    private final Set<ArgumentsList> root = new LinkedHashSet<>();

    private final Mind mind;
    private String order = "";
    private boolean ascending = true;

    public ValuesStore(Mind mind) {
        this.mind = mind;
    }

    public void commit(ValuesStore base) {
        clear();
        if (!base.isEmpty()) {
            root.addAll(base.root);
        }
    }

    public Set<ArgumentsList> getRoot() {
        return root;
    }

    public void add(Collection<TValue> raw) {
        if (!raw.isEmpty()) {
            ArgumentsList row = new ArgumentsList();
            for (IUnit one : raw) {
                TValue value = (TValue) one;
                try {
                    ITerm term = value.getValue(mind);
                    if (term != null && term.isCVariable()) {
                        continue;
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot resolve query value", e);
                }
                row.add(new Argument(one));
            }
            if (!row.isEmpty() && !root.contains(row)) {
                root.add(row);
            }
        }
    }

    public List<ITerm> getValues(String name) throws Exception {
        List<ITerm> list = new ArrayList<>();
        for (ArgumentsList row : root) {
            for (IArgument t : row) {
                if (name == null || name.equals(((TValue) t.getObject(mind)).getTVar(mind).getName(mind).getValue())) {
                    list.add(((TValue) t.getObject(mind)).getValue(mind));
                }
            }
        }
        return list;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    public void clear() {
        root.clear();
    }

    @Override
    public Map<String, ITerm> get(long id) throws Exception {
        int i = 0;
        for (Map<String, ITerm> row : this) {
            if (id == i++) {
                return row;
            }
        }
        return null;
    }

    public int size() {
        return root.size();
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    @Override
    public Iterator<Map<String, ITerm>> iterator() {
        return new ValuesIterator();
    }

    public class ValuesIterator implements Iterator<Map<String, ITerm>> {

        private final Iterator<ArgumentsList> iterator = root.iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Map<String, ITerm> next() {
            SortedMap<String, ITerm> row = new TreeMap<>();
            for (IArgument v : iterator.next()) {
                try {
                    row.put(((TValue) v.getObject(mind)).getTVar(mind).getName(mind).toString(),
                            ((TValue) v.getObject(mind)).getValue(mind));
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            }
            return row;
        }

        @Override
        public void remove() {
        }
    }
}
