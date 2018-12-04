package kanger.stores;

import kanger.User;
import kanger.factory.DatabaseFactory;
import kanger.primitives.Argument;
import kanger.primitives.Predicate;
import kanger.primitives.Solution;
import kanger.primitives.Term;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by murray on 28.05.15.
 */
public class SolutionsStore {

    private List<DatabaseFactory.Record> root = null;
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
                root = new ArrayList<>();
            }
            root.addAll(base.getRoot());
        }
    }

    public DatabaseFactory.Record add(DatabaseFactory.Record d) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
//        Solution s = new Solution(d);
        if (!root.contains(d)) {
            root.add(d);
        } else {
            d = root.get(root.indexOf(d));
        }
        return d;
    }

    public boolean contains(Object pred, boolean antc, Object... params) {
        Predicate predicate;
        if (pred instanceof Predicate) {
            predicate = (Predicate) pred;
        } else {
            predicate = user.getMind().getPredicates().add(pred.toString(), params.length);
        }
        List<Argument> parameters = new ArrayList<>();
        for (Object p : params) {
            if (p instanceof Argument) {
                parameters.add((Argument) p);
            } else if (p instanceof Term) {
                parameters.add((new Argument(p)));
            } else {
                parameters.add(new Argument(user.getMind().getTerms().add(p)));
            }
        }
        for (DatabaseFactory.Record r : root) {
            if (r.getDomain().getPredicate().getId() == predicate.getId() && r.getDomain().isAntc() == antc) {
                boolean ok = true;
                for (int i = 0; i < predicate.getRange(); ++i) {
                    if (r.getDomain().get(i).getValue().getId() != parameters.get(i).getValue().getId()) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return true;
                }
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

    public DatabaseFactory.Record get(int index) {
        return root.get(index);
    }

    public int find(Solution o) {
        return root.indexOf(o);
    }

    public List<DatabaseFactory.Record> getRoot() {
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
