package kanger.stores;

import kanger.User;
import kanger.units.TValue;
import kanger.units.Term;

import java.util.*;

/**
 * Created by murray on 28.05.15.
 */
public class ValuesStore {

    private List<TValue> root = null;
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
                root = new ArrayList<>();
            }
            root.addAll(base.getRoot());
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
            root = new ArrayList<>();
        }
        if (!root.contains(t)) {
            root.add(t);
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

//    public void normalize() {
//        List<Map<TVariable, TValue>> cnt = new ArrayList<>();
//        for (List<TValue> s : root.values()) {
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
//        root.clear();
//        int i = 0;
//        for (Map<TVariable, TValue> s : cnt) {
//            List<TValue> set = new ArrayList<>();
//            set.addAll(s.values());
//            root.put(++i, set);
//        }
//
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
//        root.clear();
//        i = 0;
//        for (List<TValue> s : list) {
//            root.put(++i, s);
//        }
//    }

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
                for (TValue t : root) {
                    if (name == null || name.equals(t.getTVar().getName().getValue())) {
                        list.add(t.getValue());
                    }
                }
        }
        return list;
    }

//    public int find(TValue s) {
//        return root.indexOf(s);
//    }

    public List<TValue> getRoot() {
        return root;
    }

    public void clear() {
        if (enableStore) {
            root = null;
        }
    }

    public int size() {
        if(root == null) {
            return 0;
        } else {
            return root.size();
        }
    }

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }
}
