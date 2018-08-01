package kanger.stores;

import kanger.Mind;
import kanger.primitives.Domain;
import kanger.primitives.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by murray on 28.05.15.
 */
public class SolutionsStore {

    private List<Solution> root = null;
    private boolean enableStore = true;

    private Mind mind = null;

    public SolutionsStore(Mind mind) {
        this.mind = mind;
    }

    public Solution add(Domain d) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Solution s = new Solution(d, mind);
        if (!root.contains(s)) {
            root.add(s);
        } else {
            s = root.get(root.indexOf(s));
        }
        return s;
    }

    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public Solution get(int index) {
        return root.get(index);
    }

    public int find(Solution o) {
        return root.indexOf(o);
    }

    public List<Solution> getRoot() {
        return root;
    }

    public void remove(Solution s) {
        root.remove(s);
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
