package kanger.stores;

import kanger.User;
import kanger.primitives.TValue;
import kanger.primitives.Term;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by murray on 28.05.15.
 */
public class ValuesStore {

    private SortedSet<TValue> root = null;
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
                root = new TreeSet<>();
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

    public TValue add(TValue t) {
        if(!enableStore) {
            return null;
        }
        if (root == null) {
            root = new TreeSet<>();
        }
        if (!root.contains(t)) {
            root.add(t);
        }
        return t;
    }

    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public TValue get(int index) {
        return root.toArray(new TValue[]{})[index];
    }

    public List<Term> getValues(String name) {
        List<Term> list = new ArrayList<>();
        for (TValue t : root) {
            if (name == null || name.equals(t.getTVar().getName())) {
                list.add(t.getValue());
            }
        }
        return list;
    }

//    public int find(TValue s) {
//        return root.indexOf(s);
//    }

    public SortedSet<TValue> getRoot() {
        return root;
    }

    public void clear() {
        if(enableStore) {
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
