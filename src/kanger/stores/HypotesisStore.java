package kanger.stores;

import kanger.User;
import kanger.primitives.*;
import kanger.units.Predicate;

import java.util.*;

/**
 * Created by murray on 28.05.15.
 */
public class HypotesisStore implements Comparable<HypotesisStore> {

    private List<Hypotese> root = null;
    private boolean enableStore = true;
    private User user = null;

    public HypotesisStore(User user) {
        this.user = user;
    }

    public void commit(HypotesisStore base) {
        if (!enableStore) {
            return;
        }
        if (!base.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
            }
            for (Hypotese h : base.getRoot()) {
                add(h);
            }
        }
    }

    public Hypotese add(boolean antc, boolean isQuery, long predicateId, ArgList arg) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypotese h = find(antc, predicateId, arg);
        if (h != null) {
            if (isQuery) {
                h.setQuery(isQuery);
            }
            return h;
        } else {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? antc : !antc;
            h = new Hypotese(user);
            h.setAntc(antc);
            h.setPredicateId(predicateId);
            h.addParams(arg);
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
        if (h == null /*|| h.isAntc() != hypotese.isAntc()*/) {
            h = hypotese;
            root.add(h);
            return h;
        }
        return h;

    }

//    public void addAll(Collection<Hypotese> list) {
//        if (list != null && !list.isEmpty()) {
//            if (root == null) {
//                root = new ArrayList<>();
//            }
//            root.addAll(list);
//        }
//    }

    public void enable(boolean enable) {
        enableStore = enable;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public Hypotese get(int index) {
        return root.toArray(new Hypotese[]{})[index];
    }


    public Hypotese find(Boolean antc, long predicateId, ArgList arg) {
        if (root == null) {
            return null;
        }
        for (Hypotese h : root) {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? h.isAntc() : !h.isAntc();
            if (h.getPredicateId() == predicateId && (antc == null || h.isAntc() == antc)) {

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
        if (root == null) {
            return null;
        }
        for (Hypotese h : root) {
            if (h.getPredicateId() == hy.getPredicateId() && h.isAntc() == hy.isAntc()) {

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


    public boolean contains(Hypotese h) {
        return find(h) != null;
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

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }


    @Override
    public int compareTo(HypotesisStore o) {
        return Integer.valueOf(size()).compareTo(Integer.valueOf(o.size()));
    }

    public void exclude(HypotesisStore exclude) {
        if (!isEmpty() && !exclude.isEmpty()) {
            Set<Hypotese> toDelete = new HashSet<>();
            for (Hypotese h : root) {
                if (exclude.find(h) != null) {
                    toDelete.add(h);
                }
            }
            //Если не остается гипотез кроме базовых - оставляем базовые
            if (toDelete.size() < root.size()) {
                for (Hypotese h : toDelete) {
                    if (!h.isQuery()) {
                        root.remove(h);
                    }
                }
            }
//        } else if (isEmpty() && !exclude.isEmpty()) {
//            for (Hypotese h : exclude.getRoot()) {
//                add(h);
//            }
        }
    }
}
