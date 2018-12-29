package kanger.stores;

import kanger.User;
import kanger.units.Record;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by murray on 28.05.15.
 */
public class SolutionsStore {

    private SortedSet<Record> root = null;
    private boolean enableStore = true;

    private User user = null;

    public SolutionsStore(User user) {
        this.user = user;
    }

    public void commit(SolutionsStore base) {
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

    public Record add(Record d) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new TreeSet<>();
        }
//        Solution s = new Solution(d);
        if (!root.contains(d)) {
            root.add(d);
        }
//        else {
//            d = root.get(root.indexOf(d));
//        }
        return d;
    }

    public boolean contains(Record rec) {
        for (Record r : root) {
            if (r.getDomain().equalsBase(rec.getDomain()) && r.getDomain().isAntc() == rec.getDomain().isAntc()) {
                return true;
            }
        }
        return false;
    }


    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public Record get(int index) {
        return root.toArray(new Record[]{})[index];
    }

//    public int find(Solution o) {
//        return root.indexOf(o);
//    }

    public SortedSet<Record> getRoot() {
        return root;
    }

//    public void remove(Solution s) {
//        root.remove(s);
//    }

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
