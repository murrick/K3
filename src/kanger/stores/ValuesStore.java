package kanger.stores;

import kanger.Mind;
import kanger.primitives.Domain;
import kanger.primitives.TMeaning;
import kanger.primitives.TVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by murray on 28.05.15.
 */
public class ValuesStore {

    private List<TMeaning> root = null;
    private boolean enableStore = true;

    private Mind mind = null;

    public ValuesStore(Mind mind) {
        this.mind = mind;
    }

    public TMeaning add(TVariable t, Domain d) {
        if(!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        TMeaning m = new TMeaning(t);
        m.setSolution(mind.getSolutions().add(d));
        if (!root.contains(m)) {
            root.add(m);
        } else {
            m = root.get(root.indexOf(m));
        }
        return m;
    }

    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public TMeaning get(int index) {
        return root.get(index);
    }

    public int find(TMeaning s) {
        return root.indexOf(s);
    }

    public List<TMeaning> getRoot() {
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
