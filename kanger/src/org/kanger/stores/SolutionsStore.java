package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class SolutionsStore {

    private List<IRule> root = null;
    private boolean enableStore = true;

    private final Mind mind;

    public SolutionsStore(Mind mind) {
        this.mind = mind;
    }

    public void commit(SolutionsStore base) {
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

    public IRule add(IRule d) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
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

    public boolean contains(Domain d) throws Exception {
        if (!isEmpty()) {
            for (IRule r : root) {
                if (!r.isDeleted(mind) && ((Rule) r).getDomain().equalsBase(d) && r.isAntc() == d.isAntc()) {
                    return true;
                }
            }
        }
        return false;
    }

//    public boolean contains(Right rt) throws Exception {
//        if (!isEmpty()) {
//            for (Right r : root) {
//                if (r.getDomain().equalsBase(rt.getDomain())) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public Rule get(int index) {
        return root.toArray(new Rule[]{})[index];
    }

//    public int find(Solution o) {
//        return root.indexOf(o);
//    }

    public List<IRule> getRoot() {
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
