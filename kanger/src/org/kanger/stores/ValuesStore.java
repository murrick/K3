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

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class ValuesStore implements IFactory<Map<String, ITerm>> {

    private Set<ComparableArgumentsList> root = new LinkedHashSet<>();

    private final transient Mind mind;
    private String order = "";
    private boolean ascending = true;

    public ValuesStore(Mind mind) {
        this.mind = mind;
    }

    public void commit(ValuesStore base) {
        clear();
        if (!base.isEmpty()) {
            getRoot().addAll(base.root);
        }
    }

    public Set<ComparableArgumentsList> getRoot() {
        if (order.isEmpty() && root instanceof SortedSet) {
            Set<ComparableArgumentsList> tmp = new LinkedHashSet<>();
            tmp.addAll(root);
            root = tmp;
        } else if (!order.isEmpty() && !(root instanceof SortedSet)) {
            Set<ComparableArgumentsList> tmp = new TreeSet<>();
            tmp.addAll(root);
            root = tmp;
        }
        return root;
    }

    public void add(Collection<TValue> raw) {
        if (!raw.isEmpty()) {
            ComparableArgumentsList row = new ComparableArgumentsList();
            for (IUnit one : raw) {
                row.add(new Argument(one));
            }
            if (!getRoot().contains(row)) {
                getRoot().add(row);
            }
        }
    }

    public List<ITerm> getValues(String name) throws Exception {
        List<ITerm> list = new ArrayList<>();
        for (ArgumentsList row : getRoot()) {
            for (IArgument t : row) {
                if (name == null || name.equals(((TValue) t.getObject(mind)).getTVar().getName().getValue())) {
                    list.add(((TValue) t.getObject(mind)).getValue());
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

    public class ComparableArgumentsList extends ArgumentsList implements Comparable<ArgumentsList> {

        @Override
        public int compareTo(ArgumentsList arguments) {
            try {
                ITerm t1 = null;
                ITerm t2 = null;
                for (IArgument a : this) {
                    if (order.equals(((TValue) a.getObject(mind)).getTVar().getName())) {
                        t1 = ((TValue) a.getObject(mind)).getValue();
                    }
                }
                for (IArgument a : arguments) {
                    if (order.equals(((TValue) a.getObject(mind)).getTVar().getName())) {
                        t2 = ((TValue) a.getObject(mind)).getValue();
                    }
                }
                if (t1 != null && t2 != null) {
                    if (ascending) {
                        return t1.compareTo(t2);
                    } else {
                        return t2.compareTo(t1);
                    }
                } else if (t1 != null) {
                    return ascending ? 1 : -1;
                } else if (t2 != null) {
                    return ascending ? -1 : 1;
                } else {
                    return ((TValue) get(0).getObject(mind)).getValue().compareTo(((TValue) arguments.get(0).getObject(mind)).getValue());
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
                return 0;
            }
        }
    }

    public class ValuesIterator implements Iterator<Map<String, ITerm>> {

        Iterator<ComparableArgumentsList> iterator = getRoot().iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Map<String, ITerm> next() {
            SortedMap<String, ITerm> row = new TreeMap<>();
            for (IArgument v : iterator.next()) {
                try {
//                    Object val = (v.getV(mind).getValue().getType() == DataType.INTERVAL
//                            || v.getV(mind).getValue().getType() == DataType.SET)
//                            ? v.getV(mind).getValue()
//                            : v.getV(mind).getValue().getValue();
                    row.put(((TValue) v.getObject(mind)).getTVar().getName().toString(), ((TValue) v.getObject(mind)).getValue());
                } catch (Exception e) {
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
