package kanger.stores;

import kanger.User;
import kanger.primitives.TValue;
import kanger.primitives.TVariable;
import kanger.primitives.Term;

import java.util.*;

/**
 * Created by murray on 28.05.15.
 */
public class ValuesStore {

    private SortedMap<Integer, SortedSet<TValue>> root = null;
    private boolean enableStore = true;

    private User user = null;

    public ValuesStore(User user) {
        this.user = user;
    }

    public void commit(ValuesStore base) {
        if (!enableStore) {
            return;
        }
        clear();
        if (!base.isEmpty()) {
            if (root == null) {
                root = new TreeMap<>();
            }
            root.putAll(base.getRoot());
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

    public TValue add(int tag, TValue t) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new TreeMap<>();
        }
        boolean found = false;
        for (SortedSet<TValue> s : root.values()) {
            if (s.contains(t)) {
                found = true;
                break;
            }
        }
        if (!found) {
            if (!root.containsKey(tag)) {
                root.put(tag, new TreeSet<>());
            }
            root.get(tag).add(t);
        }
        return t;
    }

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

    public void normalize() {
        Set<TVariable> retain = new HashSet<>();
        for (SortedSet<TValue> s : root.values()) {
            for (TValue v : s) {
                retain.add(v.getTVar());
            }
        }
        for (SortedSet<TValue> s : root.values()) {
            Set<TVariable> set = new HashSet<>();
            for (TValue v : s) {
                set.add(v.getTVar());
            }
            retain.retainAll(set);
        }
        if (!retain.isEmpty()) {
            Set<TValue> collect = new HashSet<>();
            for (SortedSet<TValue> s : root.values()) {
                for (TValue v : s) {
                    if (!retain.contains(v.getTVar())) {
                        collect.add(v);
                    }
                }
            }
            if (!collect.isEmpty()) {
                for (SortedSet<TValue> s : root.values()) {
                    s.addAll(collect);
                }
            }
        }

        List<SortedSet<TValue>> list = new ArrayList<>();
        list.addAll(root.values());
        Collections.sort(list, new Comparator<SortedSet<TValue>>() {
            @Override
            public int compare(SortedSet<TValue> o1, SortedSet<TValue> o2) {
                return o1.toArray(new TValue[]{})[0].getValue().compareTo(o2.toArray(new TValue[]{})[0].getValue());
            }
        });
        root.clear();
        int i = 0;
        for (SortedSet<TValue> s : list) {
            root.put(++i, s);
        }

    }

    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

//    public TValue get(int index) {
//        return root.toArray(new TValue[]{})[index];
//    }

    public List<Term> getValues(String name) {
        List<Term> list = new ArrayList<>();
        if(root != null) {
            for (SortedSet<TValue> s : root.values()) {
                for (TValue t : s) {
                    if (name == null || name.equals(t.getTVar().getName().getValue())) {
                        list.add(t.getValue());
                    }
                }
            }
        }
        return list;
    }

//    public int find(TValue s) {
//        return root.indexOf(s);
//    }

    public Map<Integer, SortedSet<TValue>> getRoot() {
        return root;
    }

    public void clear() {
        if (enableStore) {
            root = null;
        }
    }

    public int size() {
        return root == null ? 0 : root.size();
    }

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }
}
