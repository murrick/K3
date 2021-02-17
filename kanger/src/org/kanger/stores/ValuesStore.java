package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.units.TValue;
import org.kanger.units.Term;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class ValuesStore implements Iterable<Map<String, Term>> {

    private Set<ComparableArgList> root = new LinkedHashSet<>();

    private final Mind mind;
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

    public Set<ComparableArgList> getRoot() {
        if (order.isEmpty() && root instanceof SortedSet) {
            Set<ComparableArgList> tmp = new LinkedHashSet<>();
            tmp.addAll(root);
            root = tmp;
        } else if (!order.isEmpty() && !(root instanceof SortedSet)) {
            Set<ComparableArgList> tmp = new TreeSet<>();
            tmp.addAll(root);
            root = tmp;
        }
        return root;
    }

    public void add(Collection<TValue> raw) {
        if (!raw.isEmpty()) {
            ComparableArgList row = new ComparableArgList();
            for (IUnit one : raw) {
                row.add(new Argument(one));
            }
            if (!getRoot().contains(row)) {
                getRoot().add(row);
            }
        }
    }

    public List<Term> getValues(String name) throws Exception {
        List<Term> list = new ArrayList<>();
        for (ArgList row : getRoot()) {
            for (Argument t : row) {
                if (name == null || name.equals(t.getV(mind).getTVar().getName().getValue())) {
                    list.add(t.getV(mind).getValue());
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

    public int size() {
        return root.size();
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    public class ComparableArgList extends ArgList implements Comparable<ArgList> {

        @Override
        public int compareTo(ArgList arguments) {
            try {
                Term t1 = null;
                Term t2 = null;
                for (Argument a : this) {
                    if (order.equals(a.getV(mind).getTVar().getName())) {
                        t1 = a.getV(mind).getValue();
                    }
                }
                for (Argument a : arguments) {
                    if (order.equals(a.getV(mind).getTVar().getName())) {
                        t2 = a.getV(mind).getValue();
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
                    return get(0).getV(mind).getValue().compareTo(arguments.get(0).getV(mind).getValue());
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
                return 0;
            }
        }
    }

    @Override
    public Iterator<Map<String, Term>> iterator() {
        return new ValuesIterator();
    }

    public class ValuesIterator implements Iterator<Map<String, Term>> {

        Iterator<ComparableArgList> iterator = getRoot().iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Map<String, Term> next() {
            SortedMap<String, Term> row = new TreeMap<>();
            for (Argument v : iterator.next()) {
                try {
//                    Object val = (v.getV(mind).getValue().getType() == DataType.INTERVAL
//                            || v.getV(mind).getValue().getType() == DataType.SET)
//                            ? v.getV(mind).getValue()
//                            : v.getV(mind).getValue().getValue();
                    row.put(v.getV(mind).getTVar().getName().toString(), v.getV(mind).getValue());
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
