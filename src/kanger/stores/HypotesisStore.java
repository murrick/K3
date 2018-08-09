package kanger.stores;

import kanger.primitives.Argument;
import kanger.primitives.Hypotese;
import kanger.primitives.Predicate;

import java.util.*;

/**
 * Created by murray on 28.05.15.
 */
public class HypotesisStore implements Comparable<HypotesisStore> {

    private List<Hypotese> root = null;
    private boolean enableStore = true;

    public Hypotese add(boolean antc, boolean isQuery, Predicate pred, List<Argument> arg) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypotese h = find(antc, pred, arg);
        if (h != null) {
            if (isQuery) {
                h.setQuery(isQuery);
            }
            return h;
        } else {
            h = new Hypotese(antc, pred, arg);
            h.setQuery(isQuery);
            root.add(h);
            return h;
        }

    }

    public Hypotese add(Hypotese hypotese) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypotese h = find(hypotese);
        if (h == null) {
            h = hypotese;
            root.add(h);
            return h;
        }
        return h;

    }

    public void addAll(Collection<Hypotese> list) {
        if (list != null && !list.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
            }
            root.addAll(list);
        }
    }

    public void enable(boolean enable) {
        enableStore = enable;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public Hypotese get(int index) {
        return root.get(index);
    }


    public Hypotese find(boolean antc, Predicate pred, List<Argument> arg) {
        for (Hypotese h : root) {
            if (h.getPredicate().equals(pred) && h.isAntc() == antc) {

                int i = 0;
                if (arg.size() == h.getSolve().size()) {
                    for (; i < h.getSolve().size(); ++i) {
                        if (h.getSolve().get(i) != null && arg.get(i) != null && !h.getSolve().get(i).equals(arg.get(i).getValue())) {
                            break;
                        }
                    }
                }
                if (i == h.getSolve().size()) {
                    return h;
                }
            }
        }
        return null;
    }

    public Hypotese find(Hypotese hy) {
        for (Hypotese h : root) {
            if (h.getPredicate().equals(hy.getPredicate())) {

                int i = 0;
                if (hy.getSolve().size() == h.getSolve().size()) {
                    for (; i < h.getSolve().size(); ++i) {
                        if (h.getSolve().get(i) != null && hy.getSolve().get(i) != null && !h.getSolve().get(i).equals(hy.getSolve().get(i))) {
                            break;
                        }
                    }
                }
                if (i == h.getSolve().size()) {
                    return h;
                }
            }
        }
        return null;
    }


    public List<Hypotese> getRoot() {
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

    public void pack() {
        if (root != null) {
            List<Hypotese> temp = new ArrayList<>();
            for (Hypotese h : root) {
                if (!h.isDeleted()) {
                    temp.add(h);
                }
            }
            root = temp;
        }
    }

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }

    public void setAntc(boolean antc) {
        if (root != null) {
            for (Hypotese h : root) {
                h.setAntc(antc);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof HypotesisStore) || size() != ((HypotesisStore) o).size()) {
            return false;
        }
        for (Hypotese h : ((HypotesisStore) o).getRoot()) {
            if (find(h) == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(HypotesisStore o) {
        return Integer.valueOf(size()).compareTo(Integer.valueOf(o.size()));
    }

    public void exclude(HypotesisStore exclude) {
        if (!isEmpty() && !exclude.isEmpty()) {
            Set<Hypotese> toDelete = new HashSet<>();
            for (Hypotese h : exclude.getRoot()) {
                Hypotese x = find(h);
                if (x != null) {
                    toDelete.add(x);
                }
            }
            for (Hypotese h : toDelete) {
                if (!h.isQuery()) {
                    root.remove(h);
                }
            }
        }
    }
}
