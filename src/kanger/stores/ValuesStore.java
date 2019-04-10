package kanger.stores;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.units.TValue;
import kanger.units.Term;

import java.util.*;

/**
 * Created by murray on 28.05.15.
 */
public class ValuesStore implements Iterable<Map<String, Object>> {

    private List<ArgList> rootSystem = new ArrayList<>();
    private List<ArgList> rootData = new ArrayList<>();

    private User user = null;

    public ValuesStore(User user) {
        this.user = user;
    }

    public void commit(ValuesStore base) {
        clear();
        if (!base.isEmpty()) {
            rootSystem.addAll(base.rootSystem);
            rootData.addAll(base.rootData);
        }
    }

//    public TValue add(TVariable t) {
//        if(!enableStore) {
//            return null;
//        }
//        if (root == null) {
//            root = new ArrayList<>();
//        }
//        TValue m = t.getCurrent();
////        m.setSolution(user.getMind().getSolutions().add(d));
//        if (!root.contains(m)) {
//            root.add(m);
//        } else {
//            m = root.get(root.indexOf(m));
//        }
//        return m;
//    }

//    private boolean contains(TValue v) {
//        if (root != null) {
//            for (ArgList row : root) {
//                if (row.contains(v)) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

    public void addSystem(Collection<TValue> raw) {

        ArgList row = new ArgList();
        for (Identifiable one : raw) {
            row.add(new Argument(one));
        }
        if (!rootSystem.contains(row)) {
            rootSystem.add(row);
        }
    }

    public void addData(Collection<TValue> raw) {

        ArgList row = new ArgList();
        for (Identifiable one : raw) {
            row.add(new Argument(one));
        }
        if (!rootData.contains(row)) {
            rootData.add(row);
        }
    }

//    private boolean containsTVar(List<TValue> row, TValue t) {
//        for (TValue v : row) {
//            if (v.getTVar().getId() == t.getTVar().getId()) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    public void add(TValue t) {
//        if (enableStore && !contains(t)) {
//            if (root == null) {
//                root = new ArrayList<>();
//            }
//
//            boolean found = false;
//            for (List<TValue> row : root) {
//                if (!containsTVar(row, t)) {
//                    row.add(t);
//                    found = true;
//                    break;
//                }
//            }
//            if (!found) {
//                List<TValue> row = new ArrayList<>();
//                row.add(t);
//                root.add(row);
//            }
//        }
//    }

    /*
     *
     * Результат работы функции:
     *
     * Query: ?$x $y $z f(x,y) && a(x, z) && z >= 30;
     * Result: true
     * Solves (3):
     * 	Solution 001: 0:!f(J,S); 4 B
     * 	Solution 002: 1:!f(J,T); 3 B
     * 	Solution 003: 2:!a(J,37.0); 2 B
     * Values(2):
     * 	Value 001: [x]#1=J, [y]#2=S, [z]#3=37.0
     * 	Value 002: [x]#1=J, [y]#2=T, [z]#3=37.0
     * ----------------------------------------------------
     * OK
     * ====================================================
     *
     * Плюс сортировка
     */

//    public void normalize() {

//        List<Map<TVariable, TValue>> cnt = new ArrayList<>();
//        for (List<TValue> s : root) {
//            for(TValue v : s) {
//                boolean done = false;
//                for (Map<TVariable, TValue> map : cnt) {
//                    if (!map.containsKey(v.getTVar())) {
//                        map.put(v.getTVar(), v);
//                        done = true;
//                        break;
//                    }
//                }
//                if(!done) {
//                    Map<TVariable, TValue> map = new HashMap<>();
//                    map.put(v.getTVar(), v);
//                    cnt.add(map);
//                }
//            }
//        }
//
//        root.reset();
//        int i = 0;
//        for (Map<TVariable, TValue> s : cnt) {
//            List<TValue> set = new ArrayList<>();
//            set.addAll(s.values());
//            root.add(set);
//        }

//        Set<TVariable> retain = new HashSet<>();
//        for (List<TValue> s : root.values()) {
//            for (TValue v : s) {
//                retain.add(v.getTVar());
//            }
//        }
//        for (List<TValue> s : root.values()) {
//            Set<TVariable> set = new HashSet<>();
//            for (TValue v : s) {
//                set.add(v.getTVar());
//            }
//            retain.retainAll(set);
//        }
//        if (!retain.isEmpty()) {
//            Set<TValue> collect = new HashSet<>();
//            for (List<TValue> s : root.values()) {
//                for (TValue v : s) {
//                    if (!retain.contains(v.getTVar())) {
//                        collect.add(v);
//                    }
//                }
//            }
//            if (!collect.isEmpty()) {
//                for (List<TValue> s : root.values()) {
//                    s.addAll(collect);
//                }
//            }
//        }
//
//        List<List<TValue>> list = new ArrayList<>();
//        list.addAll(root.values());
//        Collections.sort(list, new Comparator<List<TValue>>() {
//            @Override
//            public int compare(List<TValue> o1, List<TValue> o2) {
//                return o1.toArray(new TValue[]{})[0].getValue().compareTo(o2.toArray(new TValue[]{})[0].getValue());
//            }
//        });
//        root.reset();
//        i = 0;
//        for (List<TValue> s : list) {
//            root.put(++i, s);
//        }
//    }

    public void enable(boolean e) {
//        enableStore = e;
    }

    public boolean isEnabled() {
        return true;
    }

//    public TValue get(int index) {
//        return root.toArray(new TValue[]{})[index];
//    }

    public List<Term> getValues(String name) {
        List<Term> list = new ArrayList<>();
        List<ArgList> root = rootData.isEmpty() ? rootSystem : rootData;
        for (ArgList row : root) {
            for (Argument t : row) {
                if (name == null || name.equals(t.getV().getTVar().getName().getValue())) {
                    list.add(t.getV().getValue());
                }
            }
        }
        return list;
    }

//    public int find(TValue s) {
//        return root.indexOf(s);
//    }

//    public List<ArgList> getRoot() {
//        return root;
//    }

    public void clear() {
//        if (enableStore) {
        rootSystem.clear();
        rootData.clear();
//        }
    }

    public int size() {
        return rootData.isEmpty() ? rootSystem.size() : rootData.size();
    }

    public boolean isEmpty() {
        return rootData.isEmpty() && rootSystem.isEmpty();
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        return new ValuesIterator();
    }

    public class ValuesIterator implements Iterator<Map<String, Object>> {

        Iterator<ArgList> iterator = rootData.isEmpty() ? rootSystem.iterator() : rootData.iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Map<String, Object> next() {
            SortedMap<String, Object> row = new TreeMap<>();
            for (Argument v : iterator.next()) {
                row.put(v.getV().getTVar().getName().toString(), v.getV().getValue().getValue());
            }
            return row;
        }

        @Override
        public void remove() {

        }
    }
}
